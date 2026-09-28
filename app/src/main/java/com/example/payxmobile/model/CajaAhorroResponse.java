package com.example.payxmobile.model;

import java.math.BigDecimal;

/**
 * Una caja de ahorro (GET/POST/PUT /api/cajas-ahorro y depositar/retirar). "saldo" es el de la
 * CAJA, no el del usuario. montoObjetivo null = sin meta. Depositar/retirar devuelven la caja con
 * el saldo ya actualizado (no un registro de la operación: no traen id de operación ni fecha).
 */
public class CajaAhorroResponse {
    private String id;
    private String nombre;
    private String color;
    private String icono;
    private BigDecimal saldo;
    private BigDecimal montoObjetivo;
    private String fechaCreacion;

    public CajaAhorroResponse() {}

    public CajaAhorroResponse(String id, String nombre, String color, String icono, BigDecimal saldo,
                              BigDecimal montoObjetivo, String fechaCreacion) {
        this.id = id;
        this.nombre = nombre;
        this.color = color;
        this.icono = icono;
        this.saldo = saldo;
        this.montoObjetivo = montoObjetivo;
        this.fechaCreacion = fechaCreacion;
    }

    public String getId() { return id; }
    public String getNombre() { return nombre; }
    public String getColor() { return color; }
    public String getIcono() { return icono; }
    public BigDecimal getSaldo() { return saldo; }
    public BigDecimal getMontoObjetivo() { return montoObjetivo; }
    public String getFechaCreacion() { return fechaCreacion; }
    public boolean tieneMeta() { return montoObjetivo != null; }
}
