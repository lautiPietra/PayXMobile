package com.example.payxmobile.cripto;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.math.BigDecimal;

/** Curvas del cambio de precio: nada aparece ni desaparece de golpe. */
public class TransicionPrecioTest {

    private static final float EPS = 0.0001f;

    @Test
    public void direccion() {
        assertEquals(TransicionPrecio.Direccion.SUBE,
                TransicionPrecio.direccion(new BigDecimal("128127714"), new BigDecimal("128200000")));
        assertEquals(TransicionPrecio.Direccion.BAJA,
                TransicionPrecio.direccion(new BigDecimal("2322.37"), new BigDecimal("2322.36")));
        assertNull("mismo precio", TransicionPrecio.direccion(new BigDecimal("174485"), new BigDecimal("174485.00")));
        assertNull("primera vez", TransicionPrecio.direccion(null, BigDecimal.ONE));
    }

    @Test
    public void elColorApareceSeMantieneYSeDesvaneceSinSaltos() {
        assertEquals("arranca en el color original", 0f, TransicionPrecio.intensidadColor(0), EPS);
        float anterior = 0f;
        for (long ms = 10; ms <= TransicionPrecio.APARICION_MS; ms += 10) {
            float i = TransicionPrecio.intensidadColor(ms);
            assertTrue("sube de a poco", i >= anterior && i - anterior < 0.12f);
            anterior = i;
        }
        assertEquals(1f, TransicionPrecio.intensidadColor(1500), EPS);
        long inicioSalida = TransicionPrecio.DURACION_MS - TransicionPrecio.DESVANECIMIENTO_MS;
        assertEquals("pleno justo antes de desvanecerse", 1f, TransicionPrecio.intensidadColor(inicioSalida), EPS);
        anterior = 1f;
        for (long ms = inicioSalida + 10; ms < TransicionPrecio.DURACION_MS; ms += 10) {
            float i = TransicionPrecio.intensidadColor(ms);
            assertTrue("baja de a poco", i <= anterior && anterior - i < 0.05f);
            anterior = i;
        }
        assertEquals("termina en el color original", 0f, TransicionPrecio.intensidadColor(TransicionPrecio.DURACION_MS), EPS);
        assertEquals(3000L, TransicionPrecio.DURACION_MS);
    }

    @Test
    public void elNumeroCuentaFrenandoYTerminaExacto() {
        assertEquals(0f, TransicionPrecio.progresoConteo(0), EPS);
        assertTrue("rápido al principio", TransicionPrecio.progresoConteo(TransicionPrecio.CONTEO_MS / 4) > 0.5f);
        assertEquals(1f, TransicionPrecio.progresoConteo(TransicionPrecio.CONTEO_MS), EPS);
        BigDecimal desde = new BigDecimal("128127714"), hasta = new BigDecimal("128200000");
        assertEquals(new BigDecimal("128163857.00"), TransicionPrecio.interpolar(desde, hasta, 0.5f));
        assertEquals("último cuadro exacto", hasta, TransicionPrecio.interpolar(desde, hasta, 1f));
        assertEquals(new BigDecimal("2322.37"),
                TransicionPrecio.interpolar(new BigDecimal("2322.37"), new BigDecimal("2322.36"), 0f));
    }

    @Test
    public void elTextoSeMueveApenasYVuelveASuLugar() {
        assertEquals("sube: arranca corrido hacia arriba", -TransicionPrecio.DESPLAZAMIENTO_DP,
                TransicionPrecio.desplazamientoDp(0, TransicionPrecio.Direccion.SUBE), EPS);
        assertEquals("baja: hacia abajo", TransicionPrecio.DESPLAZAMIENTO_DP,
                TransicionPrecio.desplazamientoDp(0, TransicionPrecio.Direccion.BAJA), EPS);
        assertEquals(0f, TransicionPrecio.desplazamientoDp(TransicionPrecio.DESPLAZAMIENTO_MS, TransicionPrecio.Direccion.SUBE), EPS);
        float mitad = TransicionPrecio.desplazamientoDp(TransicionPrecio.DESPLAZAMIENTO_MS / 2, TransicionPrecio.Direccion.SUBE);
        assertTrue(mitad < 0 && mitad > -TransicionPrecio.DESPLAZAMIENTO_DP);
    }
}
