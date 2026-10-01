package com.example.payxmobile.transferencias;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Pattern;

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

    /**
     * El tipeo se rechaza SOLO porque tiene más decimales de los que admite la moneda (ej. "0,005" en
     * pesos): para avisarle al usuario por qué no se escribió. El filtro sigue sin dejar escribir un tercer
     * decimal aunque sea 0: "10.500" en Argentina suele querer decir diez mil quinientos, no 10,50.
     */
    public static boolean sobranDecimales(String texto, Moneda moneda) {
        if (texto == null || esTipeoValido(texto, moneda)) return false;
        int separador = Math.max(texto.indexOf(','), texto.indexOf('.'));
        if (separador < 0 || texto.length() - separador - 1 <= moneda.decimales) return false;
        return esTipeoValido(texto.substring(0, separador + 1 + moneda.decimales), moneda);
    }

    // Dígitos con un separador decimal opcional. new BigDecimal() solo aceptaría también "1e-7", "+5" o "-5".
    private static final Pattern NUMERO_PLANO = Pattern.compile("\\d+[.,]?\\d*|[.,]\\d+");

    /** null si el texto no es un número plano (vacío, solo ",", con signo, notación científica, etc.). */
    public static BigDecimal parsear(String texto) {
        if (texto == null) return null;
        String limpio = texto.trim();
        if (!NUMERO_PLANO.matcher(limpio).matches()) return null;
        return new BigDecimal(limpio.replace(',', '.'));
    }

    /**
     * El monto como número plano, sin ceros de más ni notación científica: 0.0000001 -> "0.0000001" (no
     * "1E-7", como daría toString()), 100.00 -> "100", 100.50 -> "100.5". Así viaja en el JSON.
     */
    public static String plano(BigDecimal monto) {
        // stripTrailingZeros() deja 100.00 como 1E+2; toPlainString() lo escribe "100"
        return monto.stripTrailingZeros().toPlainString();
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
