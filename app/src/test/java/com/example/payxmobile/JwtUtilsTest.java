package com.example.payxmobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.payxmobile.utils.JwtUtils;

import org.junit.Test;

/** D1 / D2: la vigencia de la sesión se decide leyendo el "exp" del JWT. */
public class JwtUtilsTest {

    private static final long AHORA_MS = 1_790_000_000_000L;
    private static final long AHORA_S = AHORA_MS / 1000;

    @Test
    public void leeElExpDelPayload() {
        assertEquals(Long.valueOf(AHORA_S + 3600), JwtUtils.obtenerExpSegundos(JwtFalso.conExp(AHORA_S + 3600)));
    }

    @Test
    public void tokenVigenteNoEstaVencido() {
        assertFalse(JwtUtils.estaVencido(JwtFalso.conExp(AHORA_S + 7200), AHORA_MS));
    }

    @Test
    public void tokenConExpEnElPasadoEstaVencido() {
        assertTrue(JwtUtils.estaVencido(JwtFalso.conExp(AHORA_S - 1), AHORA_MS));
    }

    @Test
    public void exactamenteEnElExpYaEstaVencido() {
        assertTrue(JwtUtils.estaVencido(JwtFalso.conExp(AHORA_S), AHORA_MS));
    }

    @Test
    public void payloadConCaracteresUrlSafeSeDecodifica() {
        // Nombres con acentos generan bytes que en Base64 URL-safe producen '-' y '_'
        String token = JwtFalso.conPayload("{\"nombre\":\"Ñandú ¿?>>>~~~\",\"exp\":" + (AHORA_S + 60) + "}");
        assertEquals(Long.valueOf(AHORA_S + 60), JwtUtils.obtenerExpSegundos(token));
    }

    @Test
    public void tokensIlegiblesSeConsideranVencidos() {
        assertNull(JwtUtils.obtenerExpSegundos(null));
        assertNull(JwtUtils.obtenerExpSegundos("abc"));
        assertNull(JwtUtils.obtenerExpSegundos("abc.def.ghi"));
        assertNull(JwtUtils.obtenerExpSegundos(JwtFalso.conPayload("{\"sub\":\"x\"}")));
        assertNull(JwtUtils.obtenerExpSegundos(JwtFalso.conPayload("[1,2]")));
        assertTrue(JwtUtils.estaVencido("abc.def.ghi", AHORA_MS));
        assertTrue(JwtUtils.estaVencido(null, AHORA_MS));
    }
}
