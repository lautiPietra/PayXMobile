package com.example.payxmobile.model;

import java.math.BigDecimal;

/** Una categoría de gasto: etiqueta ya traducida por el backend y porcentaje (0-100, 1 decimal) sobre el total. */
public class CategoriaGastoResponse {
    private String codigo;
    private String etiqueta;
    private BigDecimal monto;
    private BigDecimal porcentaje;

    public CategoriaGastoResponse() {}

    public CategoriaGastoResponse(String codigo, String etiqueta, BigDecimal monto, BigDecimal porcentaje) {
        this.codigo = codigo;
        this.etiqueta = etiqueta;
        this.monto = monto;
        this.porcentaje = porcentaje;
    }

    public String getCodigo() { return codigo; }
    public String getEtiqueta() { return etiqueta; }
    public BigDecimal getMonto() { return monto; }
    public BigDecimal getPorcentaje() { return porcentaje; }
}
