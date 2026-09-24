package com.example.payxmobile.model;

import java.math.BigDecimal;

/**
 * Nombres de campo verificados contra una respuesta real de GET /api/transferencias.
 * Las fechas quedan como texto ISO-8601 con offset (ej. "2026-09-22T14:13:47.98468Z"):
 * se convierten a hora local recién al mostrarlas (FormatoTransferencia).
 */
public class TransferenciaResponse {
    private String id;
    private String moneda;          // PESOS | USD | BTC | ETH | SOL | USDT | BNB | XRP
    private BigDecimal monto;
    private String concepto;        // puede ser null
    private String estado;          // PENDIENTE | COMPLETADA | CANCELADA
    private String fecha;
    private String fechaConfirmacion; // null si no se confirmó
    private String direccion;       // ENVIADA | RECIBIDA (relativa a quien consulta)
    private String contraparteNombre; // puede ser "Usuario eliminado"
    private String contraparteAlias;  // puede ser "-"
    private boolean esEmisor;

    public String getId() { return id; }
    public String getMoneda() { return moneda; }
    public BigDecimal getMonto() { return monto != null ? monto : BigDecimal.ZERO; }
    public String getConcepto() { return concepto; }
    public String getEstado() { return estado; }
    public String getFecha() { return fecha; }
    public String getFechaConfirmacion() { return fechaConfirmacion; }
    public String getDireccion() { return direccion; }
    public String getContraparteNombre() { return contraparteNombre; }
    public String getContraparteAlias() { return contraparteAlias; }
    public boolean isEsEmisor() { return esEmisor; }

    public boolean esPendiente() { return "PENDIENTE".equals(estado); }
    public boolean esCancelada() { return "CANCELADA".equals(estado); }
    public boolean esRecibida() { return "RECIBIDA".equals(direccion); }
}
