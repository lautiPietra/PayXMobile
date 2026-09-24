package com.example.payxmobile.model;

import java.math.BigDecimal;

/** Body de POST /api/transferencias. Si concepto es null, Gson omite el campo (como la web). */
public class CrearTransferenciaRequest {
    private final String destinatario;
    private final String moneda;
    private final BigDecimal monto;
    private final String concepto;
    private final String tipo; // DIRECTA | PENDIENTE

    public CrearTransferenciaRequest(String destinatario, String moneda, BigDecimal monto,
                                     String concepto, String tipo) {
        this.destinatario = destinatario.trim();
        this.moneda = moneda;
        this.monto = monto;
        this.concepto = concepto == null || concepto.trim().isEmpty() ? null : concepto;
        this.tipo = tipo;
    }
}
