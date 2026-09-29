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
import java.util.Map;

/** Mapeo de las respuestas de error del backend (sección 2.2 del contrato). */
public class ApiErroresTest {

    @Test
    public void errorDeNegocioSeMuestraTalCual() {
        assertEquals("Credenciales invalidas", ApiErrores.mensaje(401, "{\"error\":\"Credenciales invalidas\"}"));
        assertEquals("El alias ya esta en uso", ApiErrores.mensaje(400, "{\"error\":\"El alias ya esta en uso\"}"));
    }

    @Test
    public void forbiddenEsSinPermiso() {
        assertEquals("No tenes permiso para hacer esto",
                ApiErrores.mensaje(403, "{\"error\":\"No tenes permiso para hacer esto\"}"));
        assertEquals(ApiErrores.MSG_SIN_PERMISO, ApiErrores.mensaje(403, ""));
        assertEquals(ApiErrores.MSG_SIN_PERMISO, ApiErrores.mensaje(403, null));
    }

    @Test
    public void validacionConCampos_muestraElDetalleDeCadaCampo() {
        String body = "{\"error\":\"Datos invalidos\",\"campos\":{\"monto\":\"El monto es obligatorio\","
                + "\"email\":\"El email es obligatorio\"}}";
        assertEquals("El monto es obligatorio. El email es obligatorio", ApiErrores.mensaje(400, body));
        Map<String, String> campos = ApiErrores.campos(body);
        assertEquals(2, campos.size());
        assertEquals("El monto es obligatorio", campos.get("monto"));
        assertEquals("El email es obligatorio", campos.get("email"));
        assertEquals("en el orden del backend", "monto", campos.keySet().iterator().next());
    }

    @Test
    public void validacionSinCamposUtiles_usaElGenericoNoElDatosInvalidosCrudo() {
        assertEquals(ApiErrores.MSG_DATOS_INVALIDOS, ApiErrores.mensaje(400, "{\"error\":\"Datos invalidos\",\"campos\":{}}"));
        assertEquals(ApiErrores.MSG_DATOS_INVALIDOS, ApiErrores.mensaje(400, "{\"error\":\"Datos invalidos\"}"));
        assertEquals("mensajes repetidos, una vez", "Es obligatorio",
                ApiErrores.mensaje(400, "{\"error\":\"Datos invalidos\",\"campos\":{\"a\":\"Es obligatorio\",\"b\":\"Es obligatorio\"}}"));
        assertEquals("campos raros se ignoran", ApiErrores.MSG_DATOS_INVALIDOS,
                ApiErrores.mensaje(400, "{\"error\":\"Datos invalidos\",\"campos\":{\"a\":{\"x\":1},\"b\":\"  \",\"c\":null}}"));
        assertTrue(ApiErrores.campos("[]").isEmpty());
        assertTrue(ApiErrores.campos(null).isEmpty());
        assertTrue(ApiErrores.campos("{\"campos\":[1,2]}").isEmpty());
        assertEquals("un error de negocio sigue igual", "El alias ya esta en uso",
                ApiErrores.mensaje(400, "{\"error\":\"El alias ya esta en uso\"}"));
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
