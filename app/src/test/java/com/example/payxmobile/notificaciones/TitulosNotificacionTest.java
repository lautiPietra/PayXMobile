package com.example.payxmobile.notificaciones;

import static org.junit.Assert.assertEquals;

import com.example.payxmobile.utils.FormatoFecha;

import org.junit.Test;

import java.time.ZoneId;

/** D2: título corto por código (genérico para uno desconocido) y hora corta de la fila. */
public class TitulosNotificacionTest {

    @Test
    public void d2_losCodigosConocidos() {
        assertEquals("Inicio de sesión", TitulosNotificacion.de("INICIO_SES"));
        assertEquals("Transferencia enviada", TitulosNotificacion.de("TRANSFERENCIA_ENVIADA"));
        assertEquals("Transferencia recibida", TitulosNotificacion.de("TRANSFERENCIA_RECIBIDA"));
        assertEquals("Transferencia vencida", TitulosNotificacion.de("TRANSFERENCIA_VENCIDA"));
        assertEquals("Compra de dólares", TitulosNotificacion.de("DOLARES_COMPRADOS"));
        assertEquals("Venta de dólares", TitulosNotificacion.de("DOLARES_VENDIDOS"));
        // C9: cripto, sin tocar la infraestructura
        assertEquals("Compra de cripto", TitulosNotificacion.de("CRIPTO_COMPRADA"));
        assertEquals("Venta de cripto", TitulosNotificacion.de("CRIPTO_VENDIDA"));
        // P6: plazos fijos, también sin tocar la infraestructura
        assertEquals("Plazo fijo constituido", TitulosNotificacion.de("PLAZO_FIJO_CONSTITUIDO"));
        assertEquals("Plazo fijo acreditado", TitulosNotificacion.de("PLAZO_FIJO_VENCIDO"));
        // K10: cajas de ahorro (META_ALCANZADA no tiene título en la web: es propio de la app)
        assertEquals("Depósito en caja de ahorro", TitulosNotificacion.de("CAJA_AHORRO_DEPOSITO"));
        assertEquals("Retiro de caja de ahorro", TitulosNotificacion.de("CAJA_AHORRO_RETIRO"));
        assertEquals("Meta alcanzada", TitulosNotificacion.de("CAJA_AHORRO_META_ALCANZADA"));
        // S8: servicios
        assertEquals("Pago de servicio", TitulosNotificacion.de("SERVICIO_PAGADO"));
        assertEquals(14, TitulosNotificacion.todos().size());
    }

    @Test
    public void d2_codigoDesconocidoONuloEsGenericoYNoRompe() {
        assertEquals("Notificación", TitulosNotificacion.de("CODIGO_INVENTADO"));
        assertEquals("Notificación", TitulosNotificacion.de(""));
        assertEquals("Notificación", TitulosNotificacion.de(null));
        assertEquals("sensible a mayúsculas como el backend", "Notificación", TitulosNotificacion.de("inicio_ses"));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void elMapaNoSeModificaDesdeAfuera() {
        TitulosNotificacion.todos().put("X", "Y");
    }

    @Test
    public void horaCortaEnHoraLocalComoLaWeb() {
        ZoneId ar = ZoneId.of("America/Argentina/Buenos_Aires");
        assertEquals("27/09, 10:15", FormatoFecha.corta("2026-09-27T13:15:30.123456Z", ar));
        assertEquals("día local, no el de UTC", "26/09, 23:30", FormatoFecha.corta("2026-09-27T02:30:00Z", ar));
        assertEquals("", FormatoFecha.corta(null, ar));
        assertEquals("", FormatoFecha.corta("basura", ar));
    }
}
