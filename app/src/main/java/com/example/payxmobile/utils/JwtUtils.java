package com.example.payxmobile.utils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;

import okio.ByteString;

/**
 * Lee el payload del JWT del backend SIN verificar la firma (eso lo hace el servidor).
 * Solo se usa para saber cuándo vence la sesión (claim "exp", en segundos).
 * Clase pura (sin android.*) para poder testearla en la JVM.
 */
public final class JwtUtils {

    private JwtUtils() {}

    /** Devuelve el "exp" en segundos, o null si el token no es un JWT legible. */
    public static Long obtenerExpSegundos(String token) {
        if (token == null) return null;
        String[] partes = token.split("\\.");
        if (partes.length < 2) return null;
        try {
            // Okio acepta Base64 URL-safe y sin padding, que es lo que usa JWT
            ByteString bytes = ByteString.decodeBase64(partes[1]);
            if (bytes == null) return null;
            JsonElement payload = JsonParser.parseString(bytes.string(StandardCharsets.UTF_8));
            if (!payload.isJsonObject()) return null;
            JsonObject obj = payload.getAsJsonObject();
            if (!obj.has("exp") || !obj.get("exp").isJsonPrimitive()) return null;
            return obj.get("exp").getAsLong();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Un token ilegible o sin "exp" se considera vencido: obliga a loguearse de nuevo. */
    public static boolean estaVencido(String token, long ahoraMillis) {
        Long exp = obtenerExpSegundos(token);
        return exp == null || exp * 1000L <= ahoraMillis;
    }
}
