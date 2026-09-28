package com.example.payxmobile.model;

import java.math.BigDecimal;

/** Un día de la serie de gastos: fecha "yyyy-MM-dd" (día, sin hora) y lo gastado ese día. */
public class PuntoGastoDiarioResponse {
    private String fecha;
    private BigDecimal monto;

    public PuntoGastoDiarioResponse() {}

    public PuntoGastoDiarioResponse(String fecha, BigDecimal monto) {
        this.fecha = fecha;
        this.monto = monto;
    }

    public String getFecha() { return fecha; }
    public BigDecimal getMonto() { return monto; }
}
