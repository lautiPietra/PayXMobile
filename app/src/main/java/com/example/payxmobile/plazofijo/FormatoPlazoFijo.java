package com.example.payxmobile.plazofijo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Formatos del módulo (como PlazoFijoModal.jsx / PlazosFijos.jsx / ActividadItem.jsx). Las fechas
 * "yyyy-MM-dd" son DÍAS: se leen como LocalDate, nunca como un instante (en Argentina,
 * medianoche UTC sería el día anterior). Clase pura.
 */
public final class FormatoPlazoFijo {

    private static final Locale ES_AR = new Locale("es", "AR");
    private static final DateTimeFormatter DIA_MES_ANIO = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter LARGA = DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy", ES_AR);

    private FormatoPlazoFijo() {}

    /** TNA como la muestra la web ("TNA {tna}%" con el número de JS): 35.50 -> "35.5", 40.00 -> "40". */
    public static String tna(BigDecimal tna) {
        if (tna == null) return "";
        BigDecimal limpio = tna.stripTrailingZeros();
        return (limpio.scale() < 0 ? limpio.setScale(0) : limpio).toPlainString();
    }

    /** "30 días · TNA 35.5%" (selector, resumen y detalle de la fila del feed). */
    public static String plazoYTna(int dias, BigDecimal tna) {
        return dias + " días · TNA " + tna(tna) + "%";
    }

    /** "yyyy-MM-dd" -> LocalDate; null si no se puede leer. */
    public static LocalDate dia(String iso) {
        if (iso == null || iso.isEmpty()) return null;
        try {
            return LocalDate.parse(iso.length() > 10 ? iso.substring(0, 10) : iso);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** "dd/MM/yyyy" ("Mis plazos fijos"). "" si no se puede leer. */
    public static String diaMesAnio(String iso) {
        LocalDate d = dia(iso);
        return d == null ? "" : d.format(DIA_MES_ANIO);
    }

    /** "dd/MM", SIN hora (fila "Plazo fijo acreditado" del feed). */
    public static String diaMes(String iso) {
        LocalDate d = dia(iso);
        return d == null ? "" : d.format(DIA_MES);
    }

    /** "27 de octubre de 2026" (formulario y éxito). */
    public static String larga(LocalDate d) {
        return d == null ? "" : d.format(LARGA);
    }
}
