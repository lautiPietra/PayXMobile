package com.example.payxmobile.transferencias;

import com.example.payxmobile.model.PerfilResponse;

import java.math.BigDecimal;

/**
 * Monedas transferibles con el código EXACTO que espera el backend (SOL, no "SOLANA").
 * Pesos y dólares admiten 2 decimales: el backend acepta 8 en todas, pero guarda los saldos
 * fiat con 2 y redondea (transferir 0,005 pesos crea plata de la nada). La app no lo permite.
 */
public enum Moneda {
    PESOS("$", 2),
    USD("US$", 2),
    BTC("BTC", 8),
    ETH("ETH", 8),
    SOL("SOL", 8),
    USDT("USDT", 8),
    BNB("BNB", 8),
    XRP("XRP", 8);

    public static final int MAX_ENTEROS = 13;

    /** "$", "US$" o el símbolo de la cripto. */
    public final String simbolo;
    /** Decimales permitidos al tipear el monto. */
    public final int decimales;

    Moneda(String simbolo, int decimales) {
        this.simbolo = simbolo;
        this.decimales = decimales;
    }

    public boolean esCripto() {
        return decimales == 8;
    }

    /** Código que viaja en el campo "moneda" del POST. */
    public String codigo() {
        return name();
    }

    /** Saldo disponible de ESTA moneda (SOL sale de "saldoSolana"). */
    public BigDecimal saldoEn(PerfilResponse perfil) {
        switch (this) {
            case PESOS: return perfil.getSaldoPesos();
            case USD: return perfil.getSaldoUsd();
            default: return perfil.getSaldoCripto(name());
        }
    }

    /** Acepta también "ARS" (el Home abría la pantalla con ese código). null si no se reconoce. */
    public static Moneda desde(String codigo) {
        if (codigo == null) return null;
        if ("ARS".equals(codigo)) return PESOS;
        try {
            return valueOf(codigo);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
