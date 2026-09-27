package com.example.payxmobile.model;

import java.math.BigDecimal;

/**
 * Una compra/venta de cripto (POST y GET /api/cripto). "cotizacion" es el precio de ESA operación;
 * montoCripto/montoPesos son los reales que movió el backend (no el preview).
 */
public class OperacionCriptoResponse {
    public static final String COMPRA = "COMPRA";

    private String id;
    private String tipo;
    private String simbolo;
    private BigDecimal montoCripto;
    private BigDecimal montoPesos;
    private BigDecimal cotizacion;
    private String fecha;

    public OperacionCriptoResponse() {}

    public OperacionCriptoResponse(String id, String tipo, String simbolo, BigDecimal montoCripto,
                                   BigDecimal montoPesos, BigDecimal cotizacion, String fecha) {
        this.id = id;
        this.tipo = tipo;
        this.simbolo = simbolo;
        this.montoCripto = montoCripto;
        this.montoPesos = montoPesos;
        this.cotizacion = cotizacion;
        this.fecha = fecha;
    }

    public String getId() { return id; }
    public String getTipo() { return tipo; }
    public String getSimbolo() { return simbolo; }
    public BigDecimal getMontoCripto() { return montoCripto; }
    public BigDecimal getMontoPesos() { return montoPesos; }
    public BigDecimal getCotizacion() { return cotizacion; }
    public String getFecha() { return fecha; }
    public boolean esCompra() { return COMPRA.equals(tipo); }
}
