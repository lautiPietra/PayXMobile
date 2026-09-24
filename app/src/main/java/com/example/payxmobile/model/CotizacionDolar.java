package com.example.payxmobile.model;

import java.math.BigDecimal;

/** GET /api/cotizacion/dolar. En transferencias solo se usa "venta" para el "≈ $". */
public class CotizacionDolar {
    private BigDecimal compra;
    private BigDecimal venta;
    private String fechaActualizacion;
    private boolean desactualizada;

    public BigDecimal getCompra() { return compra; }
    public BigDecimal getVenta() { return venta; }
    public String getFechaActualizacion() { return fechaActualizacion; }
    public boolean isDesactualizada() { return desactualizada; }
}
