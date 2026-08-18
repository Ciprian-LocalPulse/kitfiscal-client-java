package io.github.ciprianlocalpulse.kitfiscal;

import org.junit.jupiter.api.Test;
import io.github.ciprianlocalpulse.kitfiscal.exception.KitFiscalApiException;
import io.github.ciprianlocalpulse.kitfiscal.internal.Json;
import io.github.ciprianlocalpulse.kitfiscal.model.RezultatComparativ;
import io.github.ciprianlocalpulse.kitfiscal.model.RezultatFiscal;
import io.github.ciprianlocalpulse.kitfiscal.model.VenitInput;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KitFiscalClientTest {

    @Test
    void pfaDecodeazaRezultatulCorect() {
        KitFiscalClient.Transport transport = (method, url, body, headers) -> {
            assertEquals("POST", method);
            assertTrue(url.endsWith("/pfa"));
            Map<String, Object> decoded = Json.parseObject(body);
            assertEquals(100000.0, ((Number) decoded.get("venit_brut_anual")).doubleValue(), 0.001);

            Map<String, Object> detaliu = new LinkedHashMap<>();
            detaliu.put("cas", 12150.0);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("forma_juridica", "PFA sistem real");
            response.put("venit_brut_anual", 100000.0);
            response.put("total_taxe", 26135.0);
            response.put("venit_net_ramas", 53865.0);
            response.put("rata_efectiva_taxare", 0.26135);
            response.put("detaliu", detaliu);
            return new KitFiscalClient.Transport.Response(200, Json.write(response));
        };

        KitFiscalClient client = new KitFiscalClient("http://test/", null, Duration.ofSeconds(5), 2, transport);
        RezultatFiscal rezultat = client.pfa(VenitInput.of(100000, 20000));

        assertEquals("PFA sistem real", rezultat.formaJuridica());
        assertEquals(26135.0, rezultat.totalTaxe(), 0.001);
        assertEquals(53865.0, rezultat.venitNetRamas(), 0.001);
    }

    @Test
    void eroare4xxAruncaExceptieFaraRetry() {
        AtomicInteger apeluri = new AtomicInteger();
        KitFiscalClient.Transport transport = (method, url, body, headers) -> {
            apeluri.incrementAndGet();
            return new KitFiscalClient.Transport.Response(422, "{\"eroare\":\"ValueError\"}");
        };

        KitFiscalClient client = new KitFiscalClient("http://test/", null, Duration.ofSeconds(5), 2, transport);

        KitFiscalApiException ex = assertThrows(KitFiscalApiException.class,
                () -> client.pfa(VenitInput.of(1000, 0)));
        assertEquals(422, ex.getStatusCode());
        assertEquals(1, apeluri.get(), "Erorile 4xx nu trebuie reîncercate.");
    }

    @Test
    void eroare5xxEsteReincercataApoiEsueaza() {
        AtomicInteger apeluri = new AtomicInteger();
        KitFiscalClient.Transport transport = (method, url, body, headers) -> {
            apeluri.incrementAndGet();
            return new KitFiscalClient.Transport.Response(503, "Service Unavailable");
        };

        KitFiscalClient client = new KitFiscalClient("http://test/", null, Duration.ofSeconds(5), 2, transport);

        assertThrows(KitFiscalApiException.class, client::health);
        assertEquals(3, apeluri.get(), "Trebuie să încerce 1 + maxRetries(2) = 3 ori.");
    }

    @Test
    void venitInputRespingeVenitNegativ() {
        assertThrows(IllegalArgumentException.class, () -> VenitInput.of(-100, 0));
    }

    @Test
    void comparaDecodeazaCelMaiFavorabil() {
        KitFiscalClient.Transport transport = (method, url, body, headers) -> {
            Map<String, Object> sablon = new LinkedHashMap<>();
            sablon.put("forma_juridica", "SRL microîntreprindere");
            sablon.put("venit_brut_anual", 100000.0);
            sablon.put("total_taxe", 10000.0);
            sablon.put("venit_net_ramas", 90000.0);
            sablon.put("rata_efectiva_taxare", 0.1);
            sablon.put("detaliu", new LinkedHashMap<>());

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("pfa", sablon);
            response.put("srl_micro", sablon);
            response.put("srl_profit", sablon);
            response.put("cel_mai_favorabil", "SRL microîntreprindere");
            return new KitFiscalClient.Transport.Response(200, Json.write(response));
        };

        KitFiscalClient client = new KitFiscalClient("http://test/", null, Duration.ofSeconds(5), 2, transport);
        RezultatComparativ comparativ = client.compara(VenitInput.of(100000, 0));

        assertEquals("SRL microîntreprindere", comparativ.celMaiFavorabil());
    }
}
