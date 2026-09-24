package com.example.payxmobile.transferencias;

import java.math.BigDecimal;

/**
 * Validaciones locales del paso 1 (antes de cualquier request), con los textos de la web.
 * Devuelve el mensaje de error o null si se puede seguir.
 */
public final class ValidadorTransferencia {

    public static final String SIN_DESTINATARIO = "Ingresá a quién le querés transferir.";
    public static final String MONTO_INVALIDO = "Ingresá un monto válido.";
    public static final String SALDO_INSUFICIENTE = "No tenés saldo suficiente para esta transferencia.";
    public static final String DESTINATARIO_LARGO = "El destinatario no puede superar los 60 caracteres.";

    private ValidadorTransferencia() {}

    public static String validar(String destinatario, String montoTexto, Moneda moneda, BigDecimal saldo) {
        String dest = destinatario != null ? destinatario.trim() : "";
        if (dest.isEmpty()) return SIN_DESTINATARIO;
        if (dest.length() > 60) return DESTINATARIO_LARGO;

        BigDecimal monto = MontoInput.parsear(montoTexto);
        // Monto vacío, 0, negativo, o con más decimales/enteros de los permitidos
        if (monto == null || monto.signum() <= 0 || !MontoInput.esTipeoValido(montoTexto.trim(), moneda)) {
            return MONTO_INVALIDO;
        }
        if (saldo == null || monto.compareTo(saldo) > 0) return SALDO_INSUFICIENTE;
        return null;
    }
}
