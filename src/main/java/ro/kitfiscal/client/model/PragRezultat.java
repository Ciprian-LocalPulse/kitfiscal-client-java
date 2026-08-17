package ro.kitfiscal.client.model;

import java.util.Map;

public record PragRezultat(
        String formaA,
        String formaB,
        Double pragLei,
        String interpretare
) {
    public static PragRezultat fromJsonMap(Map<String, Object> data) {
        Object rawPrag = data.get("prag_lei");
        Double pragLei = rawPrag instanceof Number n ? n.doubleValue() : null;
        return new PragRezultat(
                String.valueOf(data.get("forma_a")),
                String.valueOf(data.get("forma_b")),
                pragLei,
                String.valueOf(data.get("interpretare"))
        );
    }
}
