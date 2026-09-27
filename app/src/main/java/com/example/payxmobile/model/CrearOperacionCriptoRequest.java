package com.example.payxmobile.model;

import java.math.BigDecimal;

/**
 * POST /api/cripto. tipo = "COMPRA" (monto en PESOS a destinar) o "VENTA" (monto = CANTIDAD de la
 * cripto a vender). simbolo: BTC, ETH, SOL, USDT, BNB o XRP. Hasta 13 enteros y 8 decimales.
 */
public class CrearOperacionCriptoRequest {
    private final String tipo;
    private final String simbolo;
    private final BigDecimal monto;

    public CrearOperacionCriptoRequest(String tipo, String simbolo, BigDecimal monto) {
        this.tipo = tipo;
        this.simbolo = simbolo;
        this.monto = monto;
    }

    public String getTipo() { return tipo; }
    public String getSimbolo() { return simbolo; }
    public BigDecimal getMonto() { return monto; }
}
