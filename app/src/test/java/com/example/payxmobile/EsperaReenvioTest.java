package com.example.payxmobile;

import static org.junit.Assert.assertEquals;

import com.example.payxmobile.utils.EsperaReenvio;

import org.junit.Test;

/** "Reenviar" queda bloqueado 60 s después de tocarlo. */
public class EsperaReenvioTest {

    private static final long AHORA = 1_790_000_000_000L;

    @Test
    public void recienTocadoFaltan60Segundos() {
        assertEquals(60, EsperaReenvio.segundosRestantes(AHORA + EsperaReenvio.ESPERA_MS, AHORA));
    }

    @Test
    public void redondeaHaciaArribaParaNoMostrar0MientrasSigueBloqueado() {
        assertEquals(1, EsperaReenvio.segundosRestantes(AHORA + 1, AHORA));
        assertEquals(30, EsperaReenvio.segundosRestantes(AHORA + 29_500, AHORA));
    }

    @Test
    public void terminadaLaEsperaOSinEsperaSePuedeReenviar() {
        assertEquals(0, EsperaReenvio.segundosRestantes(AHORA, AHORA));
        assertEquals(0, EsperaReenvio.segundosRestantes(AHORA - 5_000, AHORA));
        assertEquals(0, EsperaReenvio.segundosRestantes(0, AHORA));
    }
}
