package com.example.payxmobile.estadisticas;

import com.example.payxmobile.utils.MontoFormatter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * "Gastaste X más/menos que en el período anterior": (total - anterior) / anterior × 100, con
 * BigDecimal y sin dividir por cero. null = no se muestra ("todo el tiempo" no tiene anterior).
 */
public final class ComparacionPeriodo {

    public enum Tipo { SUBE, BAJA, IGUAL, SIN_ANTERIOR }

    public static final String MSG_SIN_ANTERIOR = "No gastaste nada en el período anterior";
    public static final String MSG_IGUAL = "Gastaste lo mismo que en el período anterior";
    public static final String MSG_SIN_GASTOS_AMBOS = "Sin gastos, igual que en el período anterior";

    public final Tipo tipo;
    /** Variación en % con 1 decimal (siempre positiva; el signo lo da el tipo). null si no aplica. */
    public final BigDecimal porcentaje;
    public final String texto;

    private ComparacionPeriodo(Tipo tipo, BigDecimal porcentaje, String texto) {
        this.tipo = tipo;
        this.porcentaje = porcentaje;
        this.texto = texto;
    }

    /** @return null si anterior es null (período "todo el tiempo"): la comparación se oculta. */
    public static ComparacionPeriodo de(BigDecimal total, BigDecimal anterior) {
        if (anterior == null || total == null) return null;
        if (anterior.signum() == 0) {
            return total.signum() == 0
                    ? new ComparacionPeriodo(Tipo.IGUAL, null, MSG_SIN_GASTOS_AMBOS)
                    : new ComparacionPeriodo(Tipo.SIN_ANTERIOR, null, MSG_SIN_ANTERIOR);
        }
        BigDecimal diferencia = total.subtract(anterior);
        BigDecimal pct = diferencia.abs().multiply(new BigDecimal("100")).divide(anterior, 1, RoundingMode.HALF_UP);
        if (pct.signum() == 0) return new ComparacionPeriodo(Tipo.IGUAL, BigDecimal.ZERO.setScale(1), MSG_IGUAL);
        boolean sube = diferencia.signum() > 0;
        String texto = "Gastaste $ " + MontoFormatter.fiat(diferencia.abs()) + (sube ? " más" : " menos")
                + " que en el período anterior (" + (sube ? "+" : "-") + porcentajeTexto(pct) + ")";
        return new ComparacionPeriodo(sube ? Tipo.SUBE : Tipo.BAJA, pct, texto);
    }

    /** 12.5 -> "12,5%", 40.0 -> "40%" (es-AR, hasta 1 decimal, como la web). */
    public static String porcentajeTexto(BigDecimal pct) {
        DecimalFormatSymbols s = new DecimalFormatSymbols(new Locale("es", "AR"));
        s.setDecimalSeparator(',');
        s.setGroupingSeparator('.');
        DecimalFormat df = new DecimalFormat("#,##0.#", s);
        df.setRoundingMode(RoundingMode.HALF_UP);
        return df.format(pct != null ? pct : BigDecimal.ZERO) + "%";
    }
}
