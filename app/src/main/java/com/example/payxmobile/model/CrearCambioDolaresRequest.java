package com.example.payxmobile.model;

import java.math.BigDecimal;

/**
 * POST /api/cambio-dolares. tipo = "COMPRA" (monto en PESOS a destinar) o "VENTA" (monto en
 * DÓLARES a vender). El backend acepta hasta 13 enteros y 2 decimales.
 */
public class CrearCambioDolaresRequest {
    private final String tipo;
    private final BigDecimal monto;

    public CrearCambioDolaresRequest(String tipo, BigDecimal monto) {
        this.tipo = tipo;
        this.monto = monto;
    }

    public String getTipo() { return tipo; }
    public BigDecimal getMonto() { return monto; }
}
