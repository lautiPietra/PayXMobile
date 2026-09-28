package com.example.payxmobile.model;

import java.math.BigDecimal;

/** POST /api/cajas-ahorro/{id}/depositar y /retirar: { monto } (> 0, hasta 13 enteros y 2 decimales). */
public class MontoCajaAhorroRequest {
    private final BigDecimal monto;

    public MontoCajaAhorroRequest(BigDecimal monto) {
        this.monto = monto;
    }

    public BigDecimal getMonto() { return monto; }
}
