package io.github.ciprianlocalpulse.kitfiscal;

import io.github.ciprianlocalpulse.kitfiscal.exception.KitFiscalApiException;
import io.github.ciprianlocalpulse.kitfiscal.internal.Json;
import io.github.ciprianlocalpulse.kitfiscal.model.PragRezultat;
import io.github.ciprianlocalpulse.kitfiscal.model.RezultatComparativ;
import io.github.ciprianlocalpulse.kitfiscal.model.RezultatFiscal;
import io.github.ciprianlocalpulse.kitfiscal.model.VenitInput;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Client Java pentru serviciul {@code kitfiscal API} (estimator fiscal
 * PFA / SRL Micro / SRL Profit, România 2026).
 *
 * <p>Strat subțire peste {@link java.net.http.HttpClient} (standard din
 * JDK 11+, nicio dependență de Jackson/Gson/OkHttp) — potrivit pentru
 * integrare directă în Spring Boot, Quarkus sau orice serviciu Java/Kotlin
 * enterprise fără riscul unui conflict de dependințe transitive.</p>
 *
 * <p>Sursa unică de adevăr a calculului rămâne serverul Python; acest
 * client nu conține nicio logică fiscală proprie.</p>
 *
 * <pre>{@code
 * KitFiscalClient client = new KitFiscalClient("https://api.exemplu.ro/kitfiscal/");
 * RezultatFiscal rezultat = client.pfa(VenitInput.of(250_000, 60_000));
 * System.out.println(rezultat.formaJuridica() + ": " + rezultat.venitNetRamas() + " lei net");
 * }</pre>
 */
public final class KitFiscalClient implements AutoCloseable {

    /** Seam de testabilitate — vezi {@link #KitFiscalClient(String, String, Duration, int, Transport)}. */
    @FunctionalInterface
    public interface Transport {
        Response send(String method, String url, String jsonBody, Map<String, String> headers) throws IOException;

        record Response(int statusCode, String body) {
        }
    }

    private final String baseUrl;
    private final String apiKey;
    private final Duration timeout;
    private final int maxRetries;
    private final Transport transport;
    private final HttpClient httpClient;

    public KitFiscalClient(String baseUrl) {
        this(baseUrl, null, Duration.ofSeconds(15), 2, null);
    }

    public KitFiscalClient(String baseUrl, String apiKey, Duration timeout, int maxRetries, Transport transport) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        this.apiKey = apiKey;
        this.timeout = timeout;
        this.maxRetries = maxRetries;
        this.transport = transport;
        // Forțăm HTTP/1.1: serverele kitfiscal (uvicorn) nu vorbesc h2c, iar
        // sondele de upgrade la HTTP/2 ale HttpClient pot duce la cereri cu
        // corpul pierdut pe unele implementări de server HTTP/1.1 stricte.
        this.httpClient = transport == null
                ? HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(timeout).build()
                : null;
    }

    public Map<String, Object> health() {
        return request("GET", "health", null);
    }

    public Map<String, Object> parametri() {
        return request("GET", "parametri", null);
    }

    public RezultatFiscal pfa(VenitInput input) {
        return RezultatFiscal.fromJsonMap(request("POST", "pfa", input.toJsonMap()));
    }

    public RezultatFiscal srlMicro(VenitInput input) {
        return RezultatFiscal.fromJsonMap(request("POST", "srl-micro", input.toJsonMap()));
    }

    public RezultatFiscal srlProfit(VenitInput input) {
        return RezultatFiscal.fromJsonMap(request("POST", "srl-profit", input.toJsonMap()));
    }

    public RezultatComparativ compara(VenitInput input) {
        return RezultatComparativ.fromJsonMap(request("POST", "compara", input.toJsonMap()));
    }

    public PragRezultat prag(Map<String, Object> payload) {
        return PragRezultat.fromJsonMap(request("POST", "prag", payload));
    }

    private Map<String, Object> request(String method, String path, Map<String, Object> body) {
        String url = baseUrl + path;
        String jsonBody = body != null ? Json.write(body) : null;

        Map<String, String> headers = new java.util.LinkedHashMap<>();
        headers.put("Accept", "application/json");
        if (apiKey != null) {
            headers.put("Authorization", "Bearer " + apiKey);
        }
        if (jsonBody != null) {
            headers.put("Content-Type", "application/json");
        }

        KitFiscalApiException lastException = null;
        for (int attempt = 1; attempt <= maxRetries + 1; attempt++) {
            try {
                Transport.Response response = transport != null
                        ? transport.send(method, url, jsonBody, headers)
                        : sendViaHttpClient(method, url, jsonBody, headers);

                if (response.statusCode() >= 500 && attempt <= maxRetries) {
                    lastException = new KitFiscalApiException(
                            "Serverul kitfiscal a răspuns cu " + response.statusCode()
                                    + " — reîncercare " + attempt + "/" + maxRetries + ".",
                            response.statusCode(), response.body());
                    sleepBackoff(attempt);
                    continue;
                }

                if (response.statusCode() >= 400) {
                    throw new KitFiscalApiException(
                            "kitfiscal API a răspuns cu status " + response.statusCode()
                                    + " pentru " + method + " " + path + ".",
                            response.statusCode(), response.body());
                }

                return Json.parseObject(response.body());
            } catch (IOException e) {
                lastException = new KitFiscalApiException("Eroare de rețea la apelul către " + url, e);
                if (attempt <= maxRetries) {
                    sleepBackoff(attempt);
                }
            }
        }
        throw lastException != null ? lastException
                : new KitFiscalApiException("Apel eșuat către kitfiscal API, motiv necunoscut.");
    }

    private Transport.Response sendViaHttpClient(String method, String url, String jsonBody, Map<String, String> headers)
            throws IOException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(timeout);
        headers.forEach(builder::header);
        builder.method(method, jsonBody != null
                ? HttpRequest.BodyPublishers.ofString(jsonBody)
                : HttpRequest.BodyPublishers.noBody());
        try {
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return new Transport.Response(response.statusCode(), response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Cerere întreruptă", e);
        }
    }

    private static void sleepBackoff(int attempt) {
        try {
            Thread.sleep(200L * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        // java.net.http.HttpClient nu necesită închidere explicită (JDK < 21);
        // metoda există pentru compatibilitate cu try-with-resources și cu
        // eventuale implementări viitoare care dețin resurse proprii.
    }
}
