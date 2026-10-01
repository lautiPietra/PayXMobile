package com.example.payxmobile.transferencias;

import java.math.BigDecimal;

/**
 * Validaciones locales del paso 1 (antes de cualquier request), con los textos de la web.
 * Devuelve el mensaje de error o null si se puede seguir.
 */
public final class ValidadorTransferencia {

    public static final String SIN_DESTINATARIO = "Ingresá a quién le querés transferir.";
    public static final String MONTO_INVALIDO = ValidadorMonto.MSG_MONTO_INVALIDO;
    public static final String SALDO_INSUFICIENTE = "No tenés saldo suficiente para esta transferencia.";
    public static final String DESTINATARIO_LARGO = "El destinatario no puede superar los 60 caracteres.";

    private ValidadorTransferencia() {}

    public static String validar(String destinatario, String montoTexto, Moneda moneda, BigDecimal saldo) {
        String dest = destinatario != null ? destinatario.trim() : "";
        if (dest.isEmpty()) return SIN_DESTINATARIO;
        if (dest.length() > 60) return DESTINATARIO_LARGO;

        // Vacío, 0, negativo, más de 13 enteros o más decimales de los que admite la moneda (2 en PESOS
        // y USD, 8 en cripto; los ceros de más no cuentan)
        String errorMonto = ValidadorMonto.validar(montoTexto, moneda.decimales);
        if (errorMonto != null) return errorMonto;
        BigDecimal monto = MontoInput.parsear(montoTexto);
        if (saldo == null || monto.compareTo(saldo) > 0) return SALDO_INSUFICIENTE;
        return null;
    }
}
