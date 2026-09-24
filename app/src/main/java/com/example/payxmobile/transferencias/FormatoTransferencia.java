package com.example.payxmobile.transferencias;

import com.example.payxmobile.utils.MontoFormatter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Formatos del módulo de transferencias (es-AR fijo, como la web). Clase pura. */
public final class FormatoTransferencia {

    public static final int HORAS_VIGENCIA_PENDIENTE = 24;

    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter FECHA_CORTA = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private FormatoTransferencia() {}

    /**
     * Formulario, resumen, confirmación y éxito: fiat con 2 decimales y cripto con 8 FIJOS.
     * "$ 1.234,50", "US$ 10,00", "BTC 0,50000000" (como la web: símbolo adelante).
     */
    public static String montoFormulario(BigDecimal monto, Moneda moneda) {
        String numero = moneda.esCripto() ? cripto8Fijos(monto) : MontoFormatter.fiat(monto);
        return moneda.simbolo + " " + numero;
    }

    /** Listado y detalle: "$ 1.234,50", "US$ 10,00" o "0,5 BTC" (cripto sin ceros de relleno). */
    public static String montoListado(BigDecimal monto, String codigoMoneda) {
        Moneda moneda = Moneda.desde(codigoMoneda);
        if (moneda != null && moneda.esCripto()) return MontoFormatter.cripto(monto) + " " + moneda.simbolo;
        String simbolo = moneda != null ? moneda.simbolo : "";
        return (simbolo.isEmpty() ? "" : simbolo + " ") + MontoFormatter.fiat(monto);
    }

    /** "≈ $ ..." de la transferencia en USD: monto × venta, 2 decimales; null sin cotización. */
    public static String equivalenteUsd(BigDecimal monto, BigDecimal venta) {
        if (monto == null || venta == null) return null;
        return "≈ $ " + MontoFormatter.fiat(monto.multiply(venta))
                + " al tipo de cambio actual ($ " + MontoFormatter.fiat(venta) + ")";
    }

    /** ISO-8601 con offset -> hora LOCAL "dd/MM/yyyy HH:mm". "" si no se puede leer. */
    public static String fechaHora(String iso, ZoneId zona) {
        OffsetDateTime f = parsear(iso);
        return f == null ? "" : f.atZoneSameInstant(zona).format(FECHA_HORA);
    }

    /** Fila del listado: "dd/MM HH:mm" en hora local. */
    public static String fechaCorta(String iso, ZoneId zona) {
        OffsetDateTime f = parsear(iso);
        return f == null ? "" : f.atZoneSameInstant(zona).format(FECHA_CORTA);
    }

    /** Cuándo se cancela sola una pendiente: fecha + 24 h, en hora local. */
    public static String vencimiento(String iso, ZoneId zona) {
        OffsetDateTime f = parsear(iso);
        return f == null ? "" : f.plusHours(HORAS_VIGENCIA_PENDIENTE).atZoneSameInstant(zona).format(FECHA_HORA);
    }

    public static OffsetDateTime parsear(String iso) {
        if (iso == null || iso.isEmpty()) return null;
        try {
            return OffsetDateTime.parse(iso);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String cripto8Fijos(BigDecimal monto) {
        DecimalFormatSymbols s = new DecimalFormatSymbols(new Locale("es", "AR"));
        s.setGroupingSeparator('.');
        s.setDecimalSeparator(',');
        DecimalFormat df = new DecimalFormat("#,##0.00000000", s);
        df.setRoundingMode(RoundingMode.HALF_UP);
        return df.format(monto != null ? monto.setScale(8, RoundingMode.HALF_UP) : BigDecimal.ZERO);
    }
}
