package com.example.payxmobile.network;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.stream.MalformedJsonException;

import java.io.EOFException;
import java.io.IOException;
import java.io.InterruptedIOException;

import okhttp3.ResponseBody;
import retrofit2.Response;

/**
 * Traduce las respuestas de error del backend a un mensaje para el usuario.
 * - Errores de negocio: {"error":"..."} (401 en login/google, 400 en el resto, 429 rate limit).
 * - 403 con body VACÍO: falló un @Valid o el token es inválido. No se trata como sesión vencida
 *   acá (eso lo decide el interceptor mirando el "exp" del token).
 * Nunca lanza excepciones: un body vacío o que no es JSON cae en un mensaje genérico.
 */
public final class ApiErrores {

    private ApiErrores() {}

    public static final String MSG_DATOS_INVALIDOS = "No se pudo completar la operación, revisá los datos";
    public static final String MSG_RATE_LIMIT = "Demasiadas solicitudes. Intentá de nuevo en unos minutos";
    public static final String MSG_SERVIDOR = "El servidor no está disponible en este momento. Intentá más tarde";
    public static final String MSG_SIN_CONEXION = "Sin conexión con el servidor. Revisá tu conexión e intentá de nuevo";
    public static final String MSG_TIMEOUT = "El servidor tardó demasiado en responder. Intentá de nuevo";
    public static final String MSG_RESPUESTA_INESPERADA = "Respuesta inesperada del servidor. Intentá de nuevo";

    /** Mensaje para una respuesta HTTP no exitosa. Lee (y consume) el errorBody. */
    public static String mensaje(Response<?> response) {
        String raw = null;
        ResponseBody body = response.errorBody();
        if (body != null) {
            try {
                raw = body.string();
            } catch (IOException | RuntimeException ignored) {
                // se usa el mensaje genérico según el código
            }
        }
        return mensaje(response.code(), raw);
    }

    public static String mensaje(int codigo, String rawBody) {
        String error = extraerError(rawBody);
        if (error != null) return error;
        if (codigo == 429) return MSG_RATE_LIMIT;
        if (codigo == 403 || codigo == 400) return MSG_DATOS_INVALIDOS;
        if (codigo >= 500) return MSG_SERVIDOR;
        return MSG_RESPUESTA_INESPERADA;
    }

    /** Mensaje para onFailure de Retrofit (sin conexión, timeout, JSON inválido en un 200). */
    public static String mensajeFallo(Throwable t) {
        // MalformedJsonException hereda de IOException: se chequea antes que "sin conexión".
        // EOFException puede ser un 200 con body vacío (Gson: "End of input...") o un corte de red.
        if (t instanceof JsonParseException || t instanceof MalformedJsonException
                || t instanceof IllegalStateException || esBodyVacio(t)) {
            return MSG_RESPUESTA_INESPERADA;
        }
        // SocketTimeoutException (connect/read) y el timeout de llamada completa de OkHttp
        if (t instanceof InterruptedIOException) return MSG_TIMEOUT;
        if (t instanceof IOException) return MSG_SIN_CONEXION;
        return MSG_RESPUESTA_INESPERADA;
    }

    private static boolean esBodyVacio(Throwable t) {
        return t instanceof EOFException && t.getMessage() != null && t.getMessage().startsWith("End of input");
    }

    /** El backend detecta "email sin verificar" solo por texto (igual que la web). */
    public static boolean esEmailSinVerificar(String mensaje) {
        return mensaje != null && mensaje.toLowerCase().contains("verificar tu email");
    }

    static String extraerError(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        try {
            JsonElement json = JsonParser.parseString(raw);
            if (json.isJsonObject() && json.getAsJsonObject().has("error")) {
                JsonElement error = json.getAsJsonObject().get("error");
                if (error.isJsonPrimitive()) {
                    String texto = error.getAsString().trim();
                    return texto.isEmpty() ? null : texto;
                }
            }
        } catch (RuntimeException ignored) {
            // no era JSON
        }
        return null;
    }
}
