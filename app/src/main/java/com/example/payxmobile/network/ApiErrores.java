package com.example.payxmobile.network;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.stream.MalformedJsonException;

import java.io.EOFException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.LinkedHashMap;
import java.util.Map;

import okhttp3.ResponseBody;
import retrofit2.Response;

/**
 * Traduce las respuestas de error del backend a un mensaje para el usuario.
 * - Errores de negocio: {"error":"..."} (401 en login/google, 400 en el resto, 404/405/413/415, 429 rate
 *   limit, 500 "Ocurrio un error inesperado..."). Se muestran tal cual.
 * - 400 de validación: {"error":"Datos invalidos","campos":{"monto":"El monto es obligatorio",...}}.
 *   Se muestra el mensaje del primer campo, no el "Datos invalidos" genérico (ver campos()).
 * - 401 en un pedido autenticado: sin sesión válida. Eso lo maneja el interceptor (cierra la sesión y
 *   va al login); acá solo se evita mostrar el texto técnico del backend ("token ausente...").
 * - 403: autenticado pero sin permiso para esa acción. NUNCA cierra la sesión: es un error más.
 * Nunca lanza excepciones: un body vacío o que no es JSON (ej. un 502 en HTML del proxy del hosting)
 * cae en un mensaje genérico.
 */
public final class ApiErrores {

    private ApiErrores() {}

    public static final String MSG_DATOS_INVALIDOS = "No se pudo completar la operación, revisá los datos";
    public static final String MSG_SIN_PERMISO = "No tenés permiso para hacer esto";
    public static final String MSG_RATE_LIMIT = "Demasiadas solicitudes. Intentá de nuevo en unos minutos";
    public static final String MSG_SERVIDOR = "El servidor no está disponible en este momento. Intentá más tarde";
    public static final String MSG_SIN_CONEXION = "Sin conexión con el servidor. Revisá tu conexión e intentá de nuevo";
    public static final String MSG_TIMEOUT = "El servidor tardó demasiado en responder. Intentá de nuevo";
    public static final String MSG_RESPUESTA_INESPERADA = "Respuesta inesperada del servidor. Intentá de nuevo";
    public static final String MSG_SESION_INVALIDA = "Tu sesión ya no es válida. Iniciá sesión de nuevo";

    // Lo que manda GlobalExceptionHandler del backend cuando falla un @Valid: el detalle está en "campos"
    static final String ERROR_VALIDACION_BACKEND = "Datos invalidos";

    /** Mensaje para una respuesta HTTP no exitosa. Lee (y consume) el errorBody. */
    public static String mensaje(Response<?> response) {
        String raw = leerBody(response);
        return esSesionInvalida(response) ? MSG_SESION_INVALIDA : mensaje(response.code(), raw);
    }

    /**
     * 401 a un pedido que llevaba el token (todo lo que no es /api/auth/**): la sesión ya no vale. El
     * interceptor ya la cerró y lleva al login con su propio aviso; la pantalla no tiene que mostrar nada.
     * Un 401 de /api/auth/login o /api/auth/google NO es esto: es un login fallido, con su texto.
     */
    public static boolean esSesionInvalida(Response<?> response) {
        return response.code() == 401 && response.raw().request().header("Authorization") != null;
    }

    /** Un rechazo del backend leído una sola vez: el mensaje general y, si corresponden, los errores por campo. */
    public static final class Rechazo {
        public final String mensaje;
        /** Ver erroresPorCampo(): vacío si el error no es de un campo puntual. */
        public final Map<String, String> campos;

        Rechazo(String mensaje, Map<String, String> campos) {
            this.mensaje = mensaje;
            this.campos = campos;
        }
    }

    /** Lee (y consume) el errorBody de una respuesta no exitosa. */
    public static Rechazo rechazo(Response<?> response) {
        String raw = leerBody(response);
        if (esSesionInvalida(response)) return new Rechazo(MSG_SESION_INVALIDA, new LinkedHashMap<>());
        return new Rechazo(mensaje(response.code(), raw), erroresPorCampo(raw));
    }

    /** El errorBody crudo (lo consume): para quien quiera el mensaje Y los campos del mismo error. */
    public static String leerBody(Response<?> response) {
        ResponseBody body = response.errorBody();
        if (body == null) return null;
        try {
            return body.string();
        } catch (IOException | RuntimeException ignored) {
            return null; // se usa el mensaje genérico según el código
        }
    }

    public static String mensaje(int codigo, String rawBody) {
        Map<String, String> campos = campos(rawBody);
        if (!campos.isEmpty()) {
            // El del primer campo (en el orden del backend): corto y concreto. Los Toast de Android 12+
            // muestran solo 2 líneas; quien quiera cada error en su campo usa campos().
            return campos.values().iterator().next();
        }
        String error = extraerError(rawBody);
        if (error != null && !ERROR_VALIDACION_BACKEND.equals(error)) return error;
        if (codigo == 429) return MSG_RATE_LIMIT;
        if (codigo == 400) return MSG_DATOS_INVALIDOS;
        if (codigo == 403) return MSG_SIN_PERMISO;
        if (codigo >= 500) return MSG_SERVIDOR;
        return MSG_RESPUESTA_INESPERADA;
    }

    /**
     * Errores por campo de un 400 de validación, en el orden del backend: {"monto": "El monto es
     * obligatorio"}. Vacío si no vinieron (o el body no tiene esa forma).
     */
    public static Map<String, String> campos(String rawBody) {
        Map<String, String> campos = new LinkedHashMap<>();
        if (rawBody == null || rawBody.trim().isEmpty()) return campos;
        try {
            JsonElement json = JsonParser.parseString(rawBody);
            if (!json.isJsonObject()) return campos;
            JsonElement crudo = json.getAsJsonObject().get("campos");
            if (crudo == null || !crudo.isJsonObject()) return campos;
            for (Map.Entry<String, JsonElement> e : crudo.getAsJsonObject().entrySet()) {
                if (!e.getValue().isJsonPrimitive()) continue;
                String texto = e.getValue().getAsString().trim();
                if (!texto.isEmpty()) campos.put(e.getKey(), texto);
            }
        } catch (RuntimeException ignored) {
            // no era JSON
        }
        return campos;
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

    /**
     * "Demasiados intentos fallidos con este codigo. Solicita uno nuevo": cada código de 6 dígitos admite
     * 5 errores y después se rechaza aunque sea el correcto. Hay que pedir otro (solo por texto, como arriba).
     */
    public static boolean esCodigoAgotado(String mensaje) {
        return mensaje != null && mensaje.toLowerCase().contains("demasiados intentos fallidos");
    }

    // Errores de negocio que son de UN campo del formulario, con el mismo nombre que usa "campos"
    private static final String[][] ERRORES_DE_CAMPO = {
            {"el email ya esta registrado", "email"},
            {"el nombre de usuario ya esta en uso", "nombreUsuario"},
            {"el alias ya esta en uso", "alias"},
            {"el dni ya esta registrado", "dni"},
            {"el dni es obligatorio", "dni"},
            {"la contrasena actual es incorrecta", "passwordActual"},
            {"tu cuenta se creo con google", "passwordActual"},
            {"la nueva contrasena debe ser diferente", "nuevaPassword"},
            // Código de 6 dígitos (verificar email y reset de contraseña)
            {"codigo incorrecto", "codigo"},
            {"codigo invalido", "codigo"},
            {"el codigo expiro", "codigo"},
            {"no hay un codigo activo", "codigo"},
            {"demasiados intentos fallidos", "codigo"},
    };

    /**
     * El campo del formulario al que corresponde un error de negocio ("El alias ya esta en uso" -> "alias"),
     * para mostrarlo debajo de ese campo. null si el error es general.
     */
    public static String campoDelError(String mensaje) {
        if (mensaje == null) return null;
        String texto = mensaje.trim().toLowerCase();
        for (String[] e : ERRORES_DE_CAMPO) {
            if (texto.startsWith(e[0])) return e[1];
        }
        return null;
    }

    /**
     * Errores por campo de un rechazo: los de "campos" (400 de validación) o, si no vinieron, el error de
     * negocio que corresponde a un campo (ver campoDelError). Vacío si el error es general.
     */
    public static Map<String, String> erroresPorCampo(String rawBody) {
        Map<String, String> campos = campos(rawBody);
        if (!campos.isEmpty()) return campos;
        String error = extraerError(rawBody);
        String campo = campoDelError(error);
        if (campo != null) campos.put(campo, error);
        return campos;
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
