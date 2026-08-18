package io.github.ciprianlocalpulse.kitfiscal.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Corespunde 1:1 cu {@code RezultatFiscalSchema} din API-ul Python.
 */
public record RezultatFiscal(
        String formaJuridica,
        double venitBrutAnual,
        double totalTaxe,
        double venitNetRamas,
        double rataEfectivaTaxare,
        Map<String, Double> detaliu
) {
    @SuppressWarnings("unchecked")
    public static RezultatFiscal fromJsonMap(Map<String, Object> data) {
        Map<String, Double> detaliu = new LinkedHashMap<>();
        Object rawDetaliu = data.get("detaliu");
        if (rawDetaliu instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                detaliu.put(String.valueOf(e.getKey()), toDouble(e.getValue()));
            }
        }
        return new RezultatFiscal(
                String.valueOf(data.get("forma_juridica")),
                toDouble(data.get("venit_brut_anual")),
                toDouble(data.get("total_taxe")),
                toDouble(data.get("venit_net_ramas")),
                toDouble(data.get("rata_efectiva_taxare")),
                detaliu
        );
    }

    static double toDouble(Object o) {
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        throw new IllegalArgumentException("Valoare numerică așteptată, primit: " + o);
    }
}
