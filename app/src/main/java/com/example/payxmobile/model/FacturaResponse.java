package com.example.payxmobile.model;

import java.math.BigDecimal;

/**
 * Una factura (GET /api/facturas/historial y la respuesta de POST /api/facturas/{id}/pagar).
 * periodo "yyyy-MM"; fechaVencimiento un día; fechaPago un instante con offset (null si PENDIENTE).
 */
public class FacturaResponse {
    private String id;
    private String servicioCodigo;
    private String servicioNombre;
    private String periodo;
    private BigDecimal monto;
    private String fechaVencimiento;
    private String estado;
    private String fechaPago;

    public FacturaResponse() {}

    public FacturaResponse(String id, String servicioCodigo, String servicioNombre, String periodo, BigDecimal monto,
                           String fechaVencimiento, String estado, String fechaPago) {
        this.id = id;
        this.servicioCodigo = servicioCodigo;
        this.servicioNombre = servicioNombre;
        this.periodo = periodo;
        this.monto = monto;
        this.fechaVencimiento = fechaVencimiento;
        this.estado = estado;
        this.fechaPago = fechaPago;
    }

    public String getId() { return id; }
    public String getServicioCodigo() { return servicioCodigo; }
    public String getServicioNombre() { return servicioNombre; }
    public String getPeriodo() { return periodo; }
    public BigDecimal getMonto() { return monto; }
    public String getFechaVencimiento() { return fechaVencimiento; }
    public String getEstado() { return estado; }
    public String getFechaPago() { return fechaPago; }
    public boolean esPagada() { return ServicioConFacturaResponse.PAGADA.equals(estado); }
}
