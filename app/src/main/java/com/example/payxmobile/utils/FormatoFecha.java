package com.example.payxmobile.utils;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Fechas del backend para mostrar: "24/09/2026 17:53" en la hora LOCAL del teléfono.
 * Acepta ISO-8601 con cualquier cantidad de decimales de segundo ("...:07.815346Z") y con o
 * sin zona. Nunca devuelve el texto crudo: si no se puede leer, devuelve "".
 */
public final class FormatoFecha {

    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private FormatoFecha() {}

    public static String fechaHora(String iso, ZoneId zona) {
        ZonedDateTime f = parsear(iso, zona);
        return f == null ? "" : f.format(FECHA_HORA);
    }

    public static String fecha(String iso, ZoneId zona) {
        ZonedDateTime f = parsear(iso, zona);
        return f == null ? "" : f.format(FECHA);
    }

    /** null si no es una fecha válida. Sin zona, se toma como hora local del teléfono. */
    public static ZonedDateTime parsear(String iso, ZoneId zona) {
        if (iso == null || iso.trim().isEmpty()) return null;
        String texto = iso.trim();
        try {
            return OffsetDateTime.parse(texto).atZoneSameInstant(zona);
        } catch (RuntimeException sinZona) {
            try {
                return LocalDateTime.parse(texto).atZone(zona);
            } catch (RuntimeException e) {
                return null;
            }
        }
    }
}
