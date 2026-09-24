package com.example.payxmobile;

import static org.junit.Assert.assertEquals;

import com.example.payxmobile.utils.FormatoFecha;

import org.junit.Test;

import java.time.ZoneId;

/** Fechas de notificaciones: nunca el texto crudo "2026-09-24T20:53:07.815346Z". */
public class FormatoFechaTest {

    private static final ZoneId BA = ZoneId.of("America/Argentina/Buenos_Aires");

    @Test
    public void formatoRealDelBackendConMicrosegundosEnHoraLocal() {
        // Tal cual llega de GET /api/notificaciones: UTC con 6 decimales
        assertEquals("24/09/2026 17:53", FormatoFecha.fechaHora("2026-09-24T20:53:07.815346Z", BA));
        assertEquals("24/09/2026", FormatoFecha.fecha("2026-09-24T20:53:07.815346Z", BA));
        assertEquals("07/09/2026 09:05", FormatoFecha.fechaHora("2026-09-07T12:05:00Z", BA));
    }

    @Test
    public void otrosFormatosIso() {
        assertEquals("24/09/2026 20:53", FormatoFecha.fechaHora("2026-09-24T20:53:07", BA)); // sin zona: local
        assertEquals("24/09/2026 20:53", FormatoFecha.fechaHora("2026-09-24T20:53:07-03:00", BA));
        assertEquals("24/09/2026 20:53", FormatoFecha.fechaHora("2026-09-24T20:53:07.1-03:00", BA));
        assertEquals("25/09/2026 08:53", FormatoFecha.fechaHora("2026-09-24T20:53:07-03:00", ZoneId.of("Asia/Tokyo")));
    }

    @Test
    public void ilegibleNoMuestraCodigoRaro() {
        assertEquals("", FormatoFecha.fechaHora("basura", BA));
        assertEquals("", FormatoFecha.fechaHora("", BA));
        assertEquals("", FormatoFecha.fechaHora(null, BA));
    }
}
