package ro.kitfiscal.client.exception;

/**
 * Aruncată când API-ul kitfiscal răspunde cu un status HTTP de eroare
 * (4xx/5xx), când răspunsul nu poate fi decodat ca JSON valid, sau la
 * eroare de rețea/timeout.
 */
public class KitFiscalApiException extends RuntimeException {

    private final int statusCode;
    private final String responseBody;

    public KitFiscalApiException(String message) {
        this(message, 0, null);
    }

    public KitFiscalApiException(String message, int statusCode, String responseBody) {
        super(message);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public KitFiscalApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
        this.responseBody = null;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }
}
