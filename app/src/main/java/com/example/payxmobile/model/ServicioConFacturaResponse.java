package com.example.payxmobile.model;

import java.math.BigDecimal;

/**
 * GET /api/facturas: un servicio del catálogo (6 fijos, definidos en el backend) con la factura del
 * período ACTUAL (la crea el backend la primera vez). monto y fechas siempre vienen del backend.
 * fechaVencimiento es un día ("yyyy-MM-dd"); fechaPago un instante con offset (null si no se pagó).
 */
public class ServicioConFacturaResponse {
    public static final String PENDIENTE = "PENDIENTE";
    public static final String PAGADA = "PAGADA";

    private String servicioCodigo;
    private String nombre;
    private String proveedor;
    private String categoria;
    private String facturaId;
    private BigDecimal monto;
    private String fechaVencimiento;
    private String estado;
    private boolean vencida;
    private String fechaPago;

    public ServicioConFacturaResponse() {}

    public ServicioConFacturaResponse(String servicioCodigo, String nombre, String proveedor, String categoria,
                                      String facturaId, BigDecimal monto, String fechaVencimiento, String estado,
                                      boolean vencida, String fechaPago) {
        this.servicioCodigo = servicioCodigo;
        this.nombre = nombre;
        this.proveedor = proveedor;
        this.categoria = categoria;
        this.facturaId = facturaId;
        this.monto = monto;
        this.fechaVencimiento = fechaVencimiento;
        this.estado = estado;
        this.vencida = vencida;
        this.fechaPago = fechaPago;
    }

    /** La misma tarjeta con la factura que devolvió el POST de pago (ya PAGADA: deja de estar vencida). */
    public ServicioConFacturaResponse conPago(FacturaResponse pagada) {
        return new ServicioConFacturaResponse(servicioCodigo, nombre, proveedor, categoria, facturaId,
                pagada.getMonto() != null ? pagada.getMonto() : monto, fechaVencimiento, pagada.getEstado(),
                vencida && !pagada.esPagada(), pagada.getFechaPago());
    }

    public String getServicioCodigo() { return servicioCodigo; }
    public String getNombre() { return nombre; }
    public String getProveedor() { return proveedor; }
    public String getCategoria() { return categoria; }
    public String getFacturaId() { return facturaId; }
    public BigDecimal getMonto() { return monto; }
    public String getFechaVencimiento() { return fechaVencimiento; }
    public String getEstado() { return estado; }
    public boolean isVencida() { return vencida; }
    public String getFechaPago() { return fechaPago; }
    public boolean esPagada() { return PAGADA.equals(estado); }
}
