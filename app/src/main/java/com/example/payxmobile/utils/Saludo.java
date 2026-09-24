package com.example.payxmobile.utils;

/** Saludo del Home, igual que la web: primera palabra del nombre completo o "de nuevo". */
public final class Saludo {

    private Saludo() {}

    public static String primerNombre(String nombreCompleto) {
        if (nombreCompleto == null || nombreCompleto.trim().isEmpty()) return "de nuevo";
        return nombreCompleto.trim().split("\\s+")[0];
    }
}
