package com.example.payxmobile.utils;

import java.util.regex.Pattern;

/**
 * Mismas reglas que los @Valid del backend (y que la web). Si algo no las cumple el backend
 * responde 403 con body vacío, así que se valida acá ANTES de mandar la request.
 * Cada método devuelve el mensaje de error, o null si el valor es válido.
 */
public final class Validadores {

    private Validadores() {}

    // Mismo patrón que usa la web (Login.jsx / Registro.jsx)
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern TELEFONO = Pattern.compile("^[0-9+\\-\\s()]{8,20}$");
    private static final Pattern DNI = Pattern.compile("^[0-9]{7,10}$");
    private static final Pattern ALIAS = Pattern.compile("^[a-zA-Z0-9._-]{6,30}$");
    private static final Pattern CODIGO = Pattern.compile("^[0-9]{6}$");

    public static String email(String email) {
        if (email == null || email.trim().isEmpty()) return "El email es obligatorio";
        if (!EMAIL.matcher(email.trim()).matches()) return "Email inválido";
        return null;
    }

    /** Login: la contraseña solo tiene que no estar vacía (no se recorta, igual que en la web). */
    public static String passwordObligatoria(String password) {
        if (password == null || password.isEmpty()) return "La contraseña es obligatoria";
        return null;
    }

    public static String passwordNueva(String password) {
        if (password == null || password.isEmpty()) return "La contraseña es obligatoria";
        if (password.length() < 8) return "La contraseña debe tener al menos 8 caracteres";
        return null;
    }

    public static String confirmacion(String nueva, String confirmacion) {
        if (confirmacion == null || confirmacion.isEmpty()) return "Confirmá la contraseña";
        if (!confirmacion.equals(nueva)) return "Las contraseñas no coinciden";
        return null;
    }

    public static String nombreCompleto(String nombre) {
        if (nombre == null || nombre.trim().isEmpty()) return "El nombre completo es obligatorio";
        int largo = nombre.trim().length();
        if (largo < 3 || largo > 100) return "El nombre completo debe tener entre 3 y 100 caracteres";
        return null;
    }

    public static String nombreUsuario(String usuario) {
        if (usuario == null || usuario.trim().isEmpty()) return "El nombre de usuario es obligatorio";
        if (usuario.length() < 3 || usuario.length() > 20) return "El nombre de usuario debe tener entre 3 y 20 caracteres";
        return null;
    }

    public static String telefono(String telefono) {
        if (telefono == null || telefono.trim().isEmpty()) return "El teléfono es obligatorio";
        if (!TELEFONO.matcher(telefono).matches()) return "Teléfono inválido (8 a 20 caracteres: números, espacios, +, -, paréntesis)";
        return null;
    }

    public static String dni(String dni) {
        if (dni == null || dni.trim().isEmpty()) return "El DNI es obligatorio";
        if (!DNI.matcher(dni).matches()) return "El DNI debe tener entre 7 y 10 dígitos";
        return null;
    }

    public static String alias(String alias) {
        if (alias == null || alias.trim().isEmpty()) return "El alias es obligatorio";
        if (!ALIAS.matcher(alias).matches()) return "Alias inválido: solo letras, números, puntos y guiones (6 a 30 caracteres)";
        return null;
    }

    public static String codigo(String codigo) {
        if (codigo == null || !CODIGO.matcher(codigo).matches()) return "Ingresá el código de 6 dígitos";
        return null;
    }
}
