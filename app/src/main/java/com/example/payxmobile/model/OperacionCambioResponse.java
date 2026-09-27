package com.example.payxmobile.model;

import java.math.BigDecimal;

/**
 * Una compra/venta de dólares (POST y GET /api/cambio-dolares). "cotizacion" es la que se usó en
 * ESA operación, no la actual. Los montos son los reales que movió el backend (no el preview).
 */
public class OperacionCambioResponse {
    public static final String COMPRA = "COMPRA";
    public static final String VENTA = "VENTA";

    private String id;
    private String tipo;
    private BigDecimal montoUsd;
    private BigDecimal montoPesos;
    private BigDecimal cotizacion;
    private String fecha;

    public OperacionCambioResponse() {}

    public OperacionCambioResponse(String id, String tipo, BigDecimal montoUsd, BigDecimal montoPesos,
                                   BigDecimal cotizacion, String fecha) {
        this.id = id;
        this.tipo = tipo;
        this.montoUsd = montoUsd;
        this.montoPesos = montoPesos;
        this.cotizacion = cotizacion;
        this.fecha = fecha;
    }

    public String getId() { return id; }
    public String getTipo() { return tipo; }
    public BigDecimal getMontoUsd() { return montoUsd; }
    public BigDecimal getMontoPesos() { return montoPesos; }
    public BigDecimal getCotizacion() { return cotizacion; }
    public String getFecha() { return fecha; }
    public boolean esCompra() { return COMPRA.equals(tipo); }
}
