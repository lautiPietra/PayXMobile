package com.example.payxmobile.model;

import java.math.BigDecimal;

/**
 * Un plazo fijo (POST y GET /api/plazos-fijos). "monto" es lo invertido; "montoTotal" (monto +
 * interesEstimado) es lo que se acredita al vencer. fechaInicio y fechaVencimiento son días
 * ("yyyy-MM-dd", SIN hora); fechaCreacion sí tiene hora y offset (solo para ordenar).
 */
public class PlazoFijoResponse {
    public static final String ACTIVO = "ACTIVO";
    public static final String VENCIDO = "VENCIDO";

    private String id;
    private BigDecimal monto;
    private BigDecimal tna;
    private int plazoDias;
    private BigDecimal interesEstimado;
    private BigDecimal montoTotal;
    private String estado;
    private String fechaInicio;
    private String fechaVencimiento;
    private String fechaCreacion;

    public PlazoFijoResponse() {}

    public PlazoFijoResponse(String id, BigDecimal monto, BigDecimal tna, int plazoDias, BigDecimal interesEstimado,
                             BigDecimal montoTotal, String estado, String fechaInicio, String fechaVencimiento,
                             String fechaCreacion) {
        this.id = id;
        this.monto = monto;
        this.tna = tna;
        this.plazoDias = plazoDias;
        this.interesEstimado = interesEstimado;
        this.montoTotal = montoTotal;
        this.estado = estado;
        this.fechaInicio = fechaInicio;
        this.fechaVencimiento = fechaVencimiento;
        this.fechaCreacion = fechaCreacion;
    }

    public String getId() { return id; }
    public BigDecimal getMonto() { return monto; }
    public BigDecimal getTna() { return tna; }
    public int getPlazoDias() { return plazoDias; }
    public BigDecimal getInteresEstimado() { return interesEstimado; }
    public BigDecimal getMontoTotal() { return montoTotal; }
    public String getEstado() { return estado; }
    public String getFechaInicio() { return fechaInicio; }
    public String getFechaVencimiento() { return fechaVencimiento; }
    public String getFechaCreacion() { return fechaCreacion; }
    public boolean esActivo() { return ACTIVO.equals(estado); }
    public boolean esVencido() { return VENCIDO.equals(estado); }
}
