package com.example.payxmobile.plazofijo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Preview del interés con la MISMA cuenta que PlazoFijoService.calcularInteres del backend:
 * monto × tna × días / (100 × 365), a 10 decimales HALF_UP y después a 2 HALF_UP. Siempre
 * BigDecimal. Es solo un preview: lo real es lo que devuelve el 201.
 */
public final class CalculoPlazoFijo {

    private static final BigDecimal DIVISOR = new BigDecimal("36500"); // 100 × 365

    private CalculoPlazoFijo() {}

    /** Interés estimado; null si falta algún dato o el monto no es positivo. */
    public static BigDecimal interes(BigDecimal monto, BigDecimal tna, int dias) {
        if (monto == null || tna == null || monto.signum() <= 0 || dias <= 0) return null;
        return monto.multiply(tna).multiply(new BigDecimal(dias))
                .divide(DIVISOR, 10, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** Lo que se acredita al vencer: monto + interés. null si no hay interés calculable. */
    public static BigDecimal total(BigDecimal monto, BigDecimal tna, int dias) {
        BigDecimal interes = interes(monto, tna, dias);
        return interes == null ? null : monto.add(interes);
    }

    /** Vencimiento estimado si se constituye hoy (el backend usa su propio "hoy"; el real viene en el 201). */
    public static LocalDate vencimientoEstimado(LocalDate hoy, int dias) {
        return hoy.plusDays(dias);
    }
}
