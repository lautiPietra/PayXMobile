package com.example.payxmobile.servicios;

import com.example.payxmobile.transferencias.FormatoTransferencia;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Formatos del módulo de servicios (es-AR). Los vencimientos son DÍAS ("yyyy-MM-dd": se leen como
 * LocalDate, nunca como instante); fechaPago es un instante y se muestra en hora LOCAL. Clase pura.
 */
public final class FormatoServicio {

    private static final Locale ES_AR = new Locale("es", "AR");
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter PERIODO = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", ES_AR);

    private FormatoServicio() {}

    public static LocalDate dia(String iso) {
        if (iso == null || iso.length() < 10) return null;
        try {
            return LocalDate.parse(iso.substring(0, 10));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** "2026-10-15" -> "15/10/2026" ("" si no se puede leer). */
    public static String diaTexto(String iso) {
        LocalDate d = dia(iso);
        return d == null ? "" : d.format(DIA);
    }

    /** fechaPago (instante) -> día LOCAL "dd/MM/yyyy": pagada a las 23:30 del 14 es del 14 en Argentina. */
    public static String diaDeInstante(String iso, ZoneId zona) {
        OffsetDateTime f = FormatoTransferencia.parsear(iso);
        return f == null ? "" : f.atZoneSameInstant(zona).toLocalDate().format(DIA);
    }

    /** "2026-09" -> "septiembre de 2026" (el mismo texto si no se puede leer). */
    public static String periodo(String periodo) {
        if (periodo == null) return "";
        try {
            return YearMonth.parse(periodo).format(PERIODO);
        } catch (RuntimeException e) {
            return periodo;
        }
    }
}
