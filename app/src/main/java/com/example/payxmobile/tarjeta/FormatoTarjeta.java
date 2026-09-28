package com.example.payxmobile.tarjeta;

import com.example.payxmobile.model.TarjetaResponse;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** Formatos de la tarjeta virtual (como TarjetaVirtual.jsx). Clase pura. */
public final class FormatoTarjeta {

    public static final String PUNTOS = "••••";
    public static final String CVV_OCULTO = "•••";
    public static final String SIN_VENCIMIENTO = "--/--";

    private static final DateTimeFormatter MM_AA = DateTimeFormatter.ofPattern("MM/yy");

    private FormatoTarjeta() {}

    /** "9004123412341234" -> "9004 1234 1234 1234". Espacios u otros separadores de entrada se ignoran. */
    public static String numeroEnGrupos(String numero) {
        if (numero == null) return "";
        String digitos = soloDigitos(numero);
        StringBuilder sb = new StringBuilder(digitos.length() + 3);
        for (int i = 0; i < digitos.length(); i++) {
            if (i > 0 && i % 4 == 0) sb.append(' ');
            sb.append(digitos.charAt(i));
        }
        return sb.toString();
    }

    /** Tapado con los últimos 4 del perfil: "•••• •••• •••• 1234" ("----" si todavía no hay perfil). */
    public static String enmascarado(String ultimosCuatro) {
        String fin = ultimosCuatro != null && ultimosCuatro.matches("\\d{4}") ? ultimosCuatro : "----";
        return PUNTOS + " " + PUNTOS + " " + PUNTOS + " " + fin;
    }

    /**
     * "2031-09-30" -> "09/31". Es un día (LocalDate): se lee como texto, nunca como instante (en
     * Argentina, medianoche UTC del 1/10 sería el 30/9 y podría correr el mes).
     */
    public static String vencimientoMmAa(String iso) {
        if (iso == null || iso.length() < 10) return SIN_VENCIMIENTO;
        try {
            return LocalDate.parse(iso.substring(0, 10)).format(MM_AA);
        } catch (RuntimeException e) {
            return SIN_VENCIMIENTO;
        }
    }

    /** Lo que va al portapapeles: solo los dígitos, sin espacios. */
    public static String paraCopiar(String numero) {
        return numero == null ? "" : soloDigitos(numero);
    }

    /** ¿La respuesta trae una tarjeta usable? 16 dígitos, CVV de 3, titular y un vencimiento legible. */
    public static boolean esValida(TarjetaResponse t) {
        return t != null && t.getNumero() != null && t.getNumero().matches("\\d{16}")
                && t.getCvv() != null && t.getCvv().matches("\\d{3}")
                && t.getTitular() != null && !t.getTitular().trim().isEmpty()
                && !SIN_VENCIMIENTO.equals(vencimientoMmAa(t.getVencimiento()));
    }

    private static String soloDigitos(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') sb.append(c);
        }
        return sb.toString();
    }
}
