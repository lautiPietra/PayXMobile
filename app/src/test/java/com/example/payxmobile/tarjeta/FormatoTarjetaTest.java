package com.example.payxmobile.tarjeta;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.payxmobile.model.TarjetaResponse;
import com.google.gson.Gson;

import org.junit.Test;

import java.util.TimeZone;

/** V1 mapeo y V3 formatos. Datos FICTICIOS (prefijo 9004 como el backend), nunca de una cuenta real. */
public class FormatoTarjetaTest {

    static final String JSON = "{\"numero\":\"9004123456789012\",\"titular\":\"ANA PRUEBA\",\"cvv\":\"007\",\"vencimiento\":\"2031-09-30\"}";

    @Test
    public void v1_mapeoCompleto() {
        TarjetaResponse t = new Gson().fromJson(JSON, TarjetaResponse.class);
        assertEquals("9004123456789012", t.getNumero());
        assertEquals("ANA PRUEBA", t.getTitular());
        assertEquals("CVV con cero adelante: es texto, no número", "007", t.getCvv());
        assertEquals("2031-09-30", t.getVencimiento());
        assertTrue(FormatoTarjeta.esValida(t));
    }

    @Test
    public void v1_respuestasInvalidas() {
        assertFalse(FormatoTarjeta.esValida(null));
        assertFalse(FormatoTarjeta.esValida(new TarjetaResponse("900412345678901", "A", "123", "2031-09-30"))); // 15
        assertFalse(FormatoTarjeta.esValida(new TarjetaResponse("9004 1234 5678 9012", "A", "123", "2031-09-30")));
        assertFalse(FormatoTarjeta.esValida(new TarjetaResponse("9004123456789012", "A", "12", "2031-09-30")));
        assertFalse(FormatoTarjeta.esValida(new TarjetaResponse("9004123456789012", " ", "123", "2031-09-30")));
        assertFalse(FormatoTarjeta.esValida(new TarjetaResponse("9004123456789012", "A", "123", null)));
        assertFalse(FormatoTarjeta.esValida(new TarjetaResponse("9004123456789012", "A", "123", "30/09/2031")));
    }

    @Test
    public void v1_sinToStringConDatos() {
        String s = new TarjetaResponse("9004123456789012", "ANA", "007", "2031-09-30").toString();
        assertFalse("un log accidental no puede mostrar el número", s.contains("9004123456789012"));
        assertFalse(s.contains("007"));
    }

    @Test
    public void v3_numeroEnGruposDe4() {
        assertEquals("9004 1234 5678 9012", FormatoTarjeta.numeroEnGrupos("9004123456789012"));
        assertEquals("9004 1234 5678 9012", FormatoTarjeta.numeroEnGrupos("9004 1234-5678 9012"));
        assertEquals("", FormatoTarjeta.numeroEnGrupos(null));
        assertEquals("•••• •••• •••• 9012", FormatoTarjeta.enmascarado("9012"));
        assertEquals("sin perfil todavía", "•••• •••• •••• ----", FormatoTarjeta.enmascarado(null));
        assertEquals("9004123456789012", FormatoTarjeta.paraCopiar("9004 1234 5678 9012"));
    }

    @Test
    public void v3_vencimientoMmAaSinCorrerseDeMes() {
        TimeZone original = TimeZone.getDefault();
        try {
            for (String zona : new String[]{"America/Argentina/Buenos_Aires", "Pacific/Kiritimati", "Pacific/Pago_Pago"}) {
                TimeZone.setDefault(TimeZone.getTimeZone(zona));
                assertEquals(zona, "09/31", FormatoTarjeta.vencimientoMmAa("2031-09-30"));
                assertEquals(zona, "01/30", FormatoTarjeta.vencimientoMmAa("2030-01-31"));
                assertEquals(zona, "12/29", FormatoTarjeta.vencimientoMmAa("2029-12-01"));
            }
        } finally {
            TimeZone.setDefault(original);
        }
        assertEquals("--/--", FormatoTarjeta.vencimientoMmAa(null));
        assertEquals("--/--", FormatoTarjeta.vencimientoMmAa("basura"));
    }
}
