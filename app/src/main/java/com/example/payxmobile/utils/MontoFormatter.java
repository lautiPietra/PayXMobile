package com.example.payxmobile.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Formato de montos igual al de la web (toLocaleString('es-AR')): punto para miles y coma para
 * decimales, SIEMPRE, sin importar el idioma del teléfono. Trabaja con BigDecimal y redondea
 * HALF_UP (1.005 -> "1,01"). Clase pura: se testea sin Android.
 */
public final class MontoFormatter {

    public static final String OCULTO = "••••••";
    public static final String SIN_DATO = "—";

    private static final Locale ES_AR = new Locale("es", "AR");

    private MontoFormatter() {}

    /** Pesos y dólares: siempre 2 decimales. 1234.5 -> "1.234,50" */
    public static String fiat(BigDecimal monto) {
        return formato("#,##0.00").format(escalar(monto, 2));
    }

    /** Cripto: de 0 a 8 decimales, sin ceros de relleno. 0.5 -> "0,5", 1000 -> "1.000" */
    public static String cripto(BigDecimal monto) {
        return formato("#,##0.########").format(escalar(monto, 8));
    }

    /** "≈ $ ..." de la tarjeta cripto: saldo × precio (en pesos) con 2 decimales, o "—" sin precio. */
    public static String equivalentePesos(BigDecimal saldo, BigDecimal precio) {
        if (saldo == null || precio == null) return SIN_DATO;
        return fiat(saldo.multiply(precio));
    }

    private static BigDecimal escalar(BigDecimal monto, int decimales) {
        BigDecimal valor = monto != null ? monto : BigDecimal.ZERO;
        BigDecimal redondeado = valor.setScale(decimales, RoundingMode.HALF_UP);
        // Evita "-0,00" cuando un negativo muy chico redondea a cero
        return redondeado.signum() == 0 ? redondeado.abs() : redondeado;
    }

    // DecimalFormat no es thread-safe: se crea uno por llamada (es barato)
    private static DecimalFormat formato(String patron) {
        // Símbolos de es-AR explícitos: con el teléfono en árabe, por ejemplo, saldrían otros dígitos
        DecimalFormatSymbols simbolos = new DecimalFormatSymbols(ES_AR);
        simbolos.setGroupingSeparator('.');
        simbolos.setDecimalSeparator(',');
        simbolos.setMinusSign('-');
        DecimalFormat df = new DecimalFormat(patron, simbolos);
        df.setRoundingMode(RoundingMode.HALF_UP);
        df.setParseBigDecimal(true);
        return df;
    }
}
