package com.example.payxmobile.transferencias;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * El monto que tipea el usuario, siempre como TEXTO -> BigDecimal (nunca double).
 * Acepta coma o punto como separador decimal, sin separador de miles.
 */
public final class MontoInput {

    private MontoInput() {}

    /**
     * Filtro de tipeo: devuelve true si el texto se puede dejar escribir (solo dígitos, un
     * separador, hasta 13 enteros y hasta los decimales de la moneda). Vacío siempre vale.
     */
    public static boolean esTipeoValido(String texto, Moneda moneda) {
        if (texto == null || texto.isEmpty()) return true;
        int separador = -1;
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (c == ',' || c == '.') {
                if (separador >= 0) return false;
                separador = i;
            } else if (c < '0' || c > '9') {
                return false;
            }
        }
        String enteros = separador >= 0 ? texto.substring(0, separador) : texto;
        String decimales = separador >= 0 ? texto.substring(separador + 1) : "";
        if (enteros.length() > Moneda.MAX_ENTEROS) return false;
        if (separador >= 0 && moneda.decimales == 0) return false;
        return decimales.length() <= moneda.decimales;
    }

    /** null si el texto no es un número (vacío, solo "," etc.). */
    public static BigDecimal parsear(String texto) {
        if (texto == null) return null;
        String limpio = texto.trim().replace(',', '.');
        if (limpio.isEmpty() || limpio.equals(".")) return null;
        try {
            return new BigDecimal(limpio);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Texto para el campo a partir de un monto exacto ("Usar todo" y precarga):
     * 3700.00 -> "3700,00"; 0.00012280 BTC -> "0,0001228". Sin notación científica.
     */
    public static String aTexto(BigDecimal monto, Moneda moneda) {
        BigDecimal valor = monto.setScale(Math.min(Math.max(monto.scale(), 0), moneda.decimales), RoundingMode.DOWN);
        if (moneda.esCripto()) valor = valor.stripTrailingZeros();
        if (valor.signum() == 0) return moneda.esCripto() ? "0" : "0,00";
        return valor.toPlainString().replace('.', ',');
    }
}
