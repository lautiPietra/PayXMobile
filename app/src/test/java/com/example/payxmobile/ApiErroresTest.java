package com.example.payxmobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.payxmobile.network.ApiErrores;
import com.example.payxmobile.network.ApiService;
import com.example.payxmobile.network.RetrofitClient;
import com.google.gson.JsonSyntaxException;

import org.junit.Test;

import java.io.EOFException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Map;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

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
        assertEquals("el del primer campo, no \"Datos invalidos\"", "El monto es obligatorio", ApiErrores.mensaje(400, body));
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

    // ── (e) Auditoría del backend: formato de errores ────────────────────────

    @Test
    public void e_errorYPrimerCampo() {
        assertEquals("x", ApiErrores.mensaje(400, "{\"error\":\"x\"}"));
        assertEquals("m", ApiErrores.mensaje(400, "{\"error\":\"Datos invalidos\",\"campos\":{\"monto\":\"m\"}}"));
        assertEquals("El monto tiene mas decimales o digitos de los permitidos", ApiErrores.mensaje(400,
                "{\"error\":\"Datos invalidos\",\"campos\":{\"monto\":\"El monto tiene mas decimales o digitos de los permitidos\"}}"));
    }

    @Test
    public void e_502EnHtmlOVacioDelProxy_mensajeGenericoSinExcepcion() throws Exception {
        assertEquals(ApiErrores.MSG_SERVIDOR, ApiErrores.mensaje(502, "<html><body><h1>502 Bad Gateway</h1></body></html>"));
        assertEquals(ApiErrores.MSG_SERVIDOR, ApiErrores.mensaje(502, ""));
        assertEquals(ApiErrores.MSG_SERVIDOR, ApiErrores.mensaje(502, null));
        assertEquals(ApiErrores.MSG_SERVIDOR, ApiErrores.mensaje(503, "Service Unavailable"));

        // Y con respuestas HTTP de verdad, como las lee cada pantalla
        MockWebServer server = new MockWebServer();
        server.start();
        try {
            ApiService api = RetrofitClient.crear(server.url("/").toString(), () -> null, () -> {}, false);
            server.enqueue(new MockResponse().setResponseCode(502).setHeader("Content-Type", "text/html")
                    .setBody("<html><head><title>502 Bad Gateway</title></head><body>nginx</body></html>"));
            assertEquals(ApiErrores.MSG_SERVIDOR, ApiErrores.mensaje(api.obtenerTasasPlazoFijo().execute()));
            server.enqueue(new MockResponse().setResponseCode(502));
            assertEquals(ApiErrores.MSG_SERVIDOR, ApiErrores.mensaje(api.obtenerTasasPlazoFijo().execute()));
            server.enqueue(new MockResponse().setResponseCode(502));
            ApiErrores.Rechazo r = ApiErrores.rechazo(api.obtenerTasasPlazoFijo().execute());
            assertEquals(ApiErrores.MSG_SERVIDOR, r.mensaje);
            assertTrue(r.campos.isEmpty());
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void e_codigosNuevosConSuTextoTalCual() {
        String[][] casos = {
                {"404", "El recurso pedido no existe"},
                {"405", "Metodo HTTP no permitido para esta ruta"},
                {"400", "El parametro 'id' no tiene un valor valido"},
                {"400", "Falta el parametro 'dias'"},
                {"413", "El archivo es demasiado grande (maximo 5 MB)"},
                {"415", "Formato de contenido no soportado"},
                {"429", "Demasiadas solicitudes. Intenta de nuevo en unos minutos"},
                {"500", "Ocurrio un error inesperado. Intenta de nuevo en unos segundos"},
        };
        for (String[] c : casos) {
            assertEquals(c[0], c[1], ApiErrores.mensaje(Integer.parseInt(c[0]), "{\"error\":\"" + c[1] + "\"}"));
        }
        // Sin cuerpo útil: uno propio según el código, nunca una excepción
        assertEquals(ApiErrores.MSG_RATE_LIMIT, ApiErrores.mensaje(429, "<html>Too Many Requests</html>"));
        assertEquals(ApiErrores.MSG_RESPUESTA_INESPERADA, ApiErrores.mensaje(413, ""));
    }

    @Test
    public void e_mensajesDeNegocioNuevosTalCual() {
        String[] mensajes = {
                "Esta cuenta se creo con Google: ingresa con el boton de Google, o crea una contraseña desde \\\"Olvide mi contraseña\\\"",
                "Tu cuenta se creo con Google y todavia no tiene contraseña. Crea una desde \\\"Olvide mi contraseña\\\" en la pantalla de inicio de sesion",
                "Demasiados intentos fallidos con este codigo. Solicita uno nuevo",
                "No pudimos enviarte el email de verificacion. Intenta de nuevo en unos minutos",
                "No pudimos enviarte el email de recuperacion. Intenta de nuevo en unos minutos",
                "El email ya esta registrado",
        };
        for (String m : mensajes) {
            assertEquals(m.replace("\\\"", "\""), ApiErrores.mensaje(400, "{\"error\":\"" + m + "\"}"));
        }
    }

    @Test
    public void e_codigoAgotadoSeDetectaPorTexto() {
        assertTrue(ApiErrores.esCodigoAgotado("Demasiados intentos fallidos con este codigo. Solicita uno nuevo"));
        assertFalse(ApiErrores.esCodigoAgotado("Codigo incorrecto"));
        assertFalse("el 429 es otra cosa", ApiErrores.esCodigoAgotado("Demasiadas solicitudes. Intenta de nuevo en unos minutos"));
        assertFalse(ApiErrores.esCodigoAgotado(null));
    }

    @Test
    public void e_erroresQueVanDebajoDeSuCampo() {
        assertEquals("nombreUsuario", ApiErrores.campoDelError("El nombre de usuario ya esta en uso"));
        assertEquals("alias", ApiErrores.campoDelError("El alias ya esta en uso"));
        assertEquals("email", ApiErrores.campoDelError("El email ya esta registrado"));
        assertEquals("dni", ApiErrores.campoDelError("El DNI ya esta registrado"));
        assertEquals("passwordActual", ApiErrores.campoDelError("La contrasena actual es incorrecta"));
        assertEquals("passwordActual", ApiErrores.campoDelError(
                "Tu cuenta se creo con Google y todavia no tiene contraseña. Crea una desde \"Olvide mi contraseña\" en la pantalla de inicio de sesion"));
        assertEquals("codigo", ApiErrores.campoDelError("Codigo incorrecto"));
        assertEquals("codigo", ApiErrores.campoDelError("Demasiados intentos fallidos con este codigo. Solicita uno nuevo"));
        assertEquals("codigo", ApiErrores.campoDelError("El codigo expiro. Solicita uno nuevo"));
        assertNull("general: va en un Toast", ApiErrores.campoDelError("No pudimos enviarte el email de verificacion. Intenta de nuevo en unos minutos"));
        assertNull(ApiErrores.campoDelError(null));

        Map<String, String> negocio = ApiErrores.erroresPorCampo("{\"error\":\"El alias ya esta en uso\"}");
        assertEquals(1, negocio.size());
        assertEquals("El alias ya esta en uso", negocio.get("alias"));
        Map<String, String> valid = ApiErrores.erroresPorCampo(
                "{\"error\":\"Datos invalidos\",\"campos\":{\"nombreUsuario\":\"Solo letras, numeros, puntos, guiones y guiones bajos\"}}");
        assertEquals("Solo letras, numeros, puntos, guiones y guiones bajos", valid.get("nombreUsuario"));
        assertTrue(ApiErrores.erroresPorCampo("<html>502</html>").isEmpty());
        assertTrue(ApiErrores.erroresPorCampo(null).isEmpty());
    }

    @Test
    public void detectaEmailSinVerificarPorTexto() {
        assertTrue(ApiErrores.esEmailSinVerificar("Debes verificar tu email antes de iniciar sesion"));
        assertFalse(ApiErrores.esEmailSinVerificar("Credenciales invalidas"));
        assertFalse(ApiErrores.esEmailSinVerificar(null));
    }
}
