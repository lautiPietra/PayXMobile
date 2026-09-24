package com.example.payxmobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.payxmobile.network.ApiErrores;
import com.google.gson.JsonSyntaxException;

import org.junit.Test;

import java.io.EOFException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

/** Mapeo de las respuestas de error del backend (sección 2.2 del contrato). */
public class ApiErroresTest {

    @Test
    public void errorDeNegocioSeMuestraTalCual() {
        assertEquals("Credenciales invalidas", ApiErrores.mensaje(401, "{\"error\":\"Credenciales invalidas\"}"));
        assertEquals("El alias ya esta en uso", ApiErrores.mensaje(400, "{\"error\":\"El alias ya esta en uso\"}"));
    }

    @Test
    public void forbiddenConBodyVacioEsErrorGenericoDeDatos() {
        assertEquals(ApiErrores.MSG_DATOS_INVALIDOS, ApiErrores.mensaje(403, ""));
        assertEquals(ApiErrores.MSG_DATOS_INVALIDOS, ApiErrores.mensaje(403, null));
    }

    @Test
    public void rateLimitUsaElMensajeDelBackendOUnoPropio() {
        assertEquals("Demasiadas solicitudes. Intenta de nuevo en unos minutos",
                ApiErrores.mensaje(429, "{\"error\":\"Demasiadas solicitudes. Intenta de nuevo en unos minutos\"}"));
        assertEquals(ApiErrores.MSG_RATE_LIMIT, ApiErrores.mensaje(429, ""));
    }

    @Test
    public void bodiesRarosNoRompen() {
        assertEquals(ApiErrores.MSG_SERVIDOR, ApiErrores.mensaje(500, "<html>Whitelabel Error Page</html>"));
        assertEquals(ApiErrores.MSG_SERVIDOR, ApiErrores.mensaje(502, "{"));
        assertEquals(ApiErrores.MSG_DATOS_INVALIDOS, ApiErrores.mensaje(400, "{\"error\":null}"));
        assertEquals(ApiErrores.MSG_DATOS_INVALIDOS, ApiErrores.mensaje(400, "{\"error\":\"   \"}"));
        assertEquals(ApiErrores.MSG_DATOS_INVALIDOS, ApiErrores.mensaje(400, "{\"error\":{\"x\":1}}"));
        assertEquals(ApiErrores.MSG_RESPUESTA_INESPERADA, ApiErrores.mensaje(401, "[]"));
    }

    @Test
    public void fallosDeRed() {
        assertEquals(ApiErrores.MSG_TIMEOUT, ApiErrores.mensajeFallo(new SocketTimeoutException()));
        assertEquals(ApiErrores.MSG_SIN_CONEXION, ApiErrores.mensajeFallo(new ConnectException()));
        assertEquals(ApiErrores.MSG_SIN_CONEXION, ApiErrores.mensajeFallo(new UnknownHostException()));
        assertEquals(ApiErrores.MSG_RESPUESTA_INESPERADA, ApiErrores.mensajeFallo(new JsonSyntaxException("x")));
        assertEquals(ApiErrores.MSG_RESPUESTA_INESPERADA, ApiErrores.mensajeFallo(new EOFException("End of input at line 1 column 1 path $")));
        assertEquals(ApiErrores.MSG_SIN_CONEXION, ApiErrores.mensajeFallo(new EOFException("\n not found: limit=0")));
    }

    @Test
    public void detectaEmailSinVerificarPorTexto() {
        assertTrue(ApiErrores.esEmailSinVerificar("Debes verificar tu email antes de iniciar sesion"));
        assertFalse(ApiErrores.esEmailSinVerificar("Credenciales invalidas"));
        assertFalse(ApiErrores.esEmailSinVerificar(null));
    }
}
