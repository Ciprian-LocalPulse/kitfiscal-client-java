package ro.kitfiscal.client.model;

import java.util.Map;

public record RezultatComparativ(
        RezultatFiscal pfa,
        RezultatFiscal srlMicro,
        RezultatFiscal srlProfit,
        String celMaiFavorabil
) {
    @SuppressWarnings("unchecked")
    public static RezultatComparativ fromJsonMap(Map<String, Object> data) {
        return new RezultatComparativ(
                RezultatFiscal.fromJsonMap((Map<String, Object>) data.get("pfa")),
                RezultatFiscal.fromJsonMap((Map<String, Object>) data.get("srl_micro")),
                RezultatFiscal.fromJsonMap((Map<String, Object>) data.get("srl_profit")),
                String.valueOf(data.get("cel_mai_favorabil"))
        );
    }
}
