package com.example.payxmobile;

import okio.ByteString;

/** Arma JWTs falsos (firma basura): la app solo lee el payload, no verifica la firma. */
public final class JwtFalso {

    private JwtFalso() {}

    public static String conPayload(String payloadJson) {
        return b64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}") + "." + b64(payloadJson) + ".firmaFalsa";
    }

    public static String conExp(long expSegundos) {
        return conPayload("{\"sub\":\"8a6e0804-2bd0-4672-b79d-d97027f9071a\",\"email\":\"test@payx.com\","
                + "\"rol\":\"USUARIO\",\"iat\":" + (expSegundos - 7200) + ",\"exp\":" + expSegundos + "}");
    }

    private static String b64(String s) {
        // Base64 URL-safe sin padding, como el JWT real
        return ByteString.encodeUtf8(s).base64Url().replace("=", "");
    }
}
