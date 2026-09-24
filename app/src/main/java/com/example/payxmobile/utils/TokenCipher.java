package com.example.payxmobile.utils;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Cifra el JWT con una clave AES-GCM que vive en el Android Keystore (no se puede extraer).
 * Equivalente a EncryptedSharedPreferences (deprecado) sin sumar dependencias.
 */
final class TokenCipher {

    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String ALIAS = "payx_token_key";
    private static final String TRANSFORMACION = "AES/GCM/NoPadding";

    private TokenCipher() {}

    static String cifrar(String texto) throws Exception {
        Cipher cipher = Cipher.getInstance(TRANSFORMACION);
        cipher.init(Cipher.ENCRYPT_MODE, obtenerClave());
        byte[] cifrado = cipher.doFinal(texto.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":"
                + Base64.encodeToString(cifrado, Base64.NO_WRAP);
    }

    static String descifrar(String guardado) throws Exception {
        String[] partes = guardado.split(":", 2);
        if (partes.length != 2) throw new IllegalArgumentException("Formato inválido");
        byte[] iv = Base64.decode(partes[0], Base64.NO_WRAP);
        byte[] cifrado = Base64.decode(partes[1], Base64.NO_WRAP);
        Cipher cipher = Cipher.getInstance(TRANSFORMACION);
        cipher.init(Cipher.DECRYPT_MODE, obtenerClave(), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(cifrado), StandardCharsets.UTF_8);
    }

    private static SecretKey obtenerClave() throws Exception {
        KeyStore ks = KeyStore.getInstance(KEYSTORE);
        ks.load(null);
        if (ks.containsAlias(ALIAS)) {
            return ((KeyStore.SecretKeyEntry) ks.getEntry(ALIAS, null)).getSecretKey();
        }
        KeyGenerator generador = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        generador.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return generador.generateKey();
    }
}
