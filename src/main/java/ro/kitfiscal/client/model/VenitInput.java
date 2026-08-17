package ro.kitfiscal.client.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Corespunde 1:1 cu {@code VenitInputSchema} din API-ul Python (kitfiscal.api).
 */
public record VenitInput(
        double venitBrutAnual,
        double cheltuieliAnuale,
        double salariuAdministratorLunar,
        int numarSalariati,
        double amortizareAnuala,
        String bazaCasOptiune,
        double bazaCasManuala
) {
    public static final String BAZA_CAS_MINIM = "Minim (12 salarii)";
    public static final String BAZA_CAS_MAXIM = "Maxim (24 salarii)";
    public static final String BAZA_CAS_MANUAL = "Manual";

    public VenitInput {
        if (venitBrutAnual < 0) {
            throw new IllegalArgumentException("venitBrutAnual nu poate fi negativ");
        }
        if (cheltuieliAnuale < 0) {
            throw new IllegalArgumentException("cheltuieliAnuale nu poate fi negativ");
        }
        if (salariuAdministratorLunar < 0) {
            throw new IllegalArgumentException("salariuAdministratorLunar nu poate fi negativ");
        }
        if (numarSalariati < 0) {
            throw new IllegalArgumentException("numarSalariati nu poate fi negativ");
        }
    }

    /** Constructor de conveniență cu valorile implicite ale API-ului (fără salariați, bază CAS minimă). */
    public static VenitInput of(double venitBrutAnual, double cheltuieliAnuale) {
        return new VenitInput(venitBrutAnual, cheltuieliAnuale, 0.0, 0, 0.0, BAZA_CAS_MINIM, 0.0);
    }

    public Map<String, Object> toJsonMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("venit_brut_anual", venitBrutAnual);
        map.put("cheltuieli_anuale", cheltuieliAnuale);
        map.put("salariu_administrator_lunar", salariuAdministratorLunar);
        map.put("numar_salariati", numarSalariati);
        map.put("amortizare_anuala", amortizareAnuala);
        map.put("baza_cas_optiune", bazaCasOptiune);
        map.put("baza_cas_manuala", bazaCasManuala);
        return map;
    }
}
