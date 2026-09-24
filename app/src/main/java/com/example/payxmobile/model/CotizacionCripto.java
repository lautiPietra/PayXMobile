package com.example.payxmobile.model;

import java.math.BigDecimal;

/** Elemento de GET /api/cotizacion/cripto: precio en PESOS de UNA unidad. */
public class CotizacionCripto {
    private String simbolo;
    private String nombre;
    private BigDecimal precio;
    // true = último precio bueno conocido (el proveedor no respondió)
    private boolean desactualizada;

    public String getSimbolo() { return simbolo; }
    public String getNombre() { return nombre; }
    public BigDecimal getPrecio() { return precio; }
    public boolean isDesactualizada() { return desactualizada; }
}
