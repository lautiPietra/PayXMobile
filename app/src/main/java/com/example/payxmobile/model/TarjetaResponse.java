package com.example.payxmobile.model;

/**
 * GET /api/tarjeta: número COMPLETO, titular, CVV y vencimiento ("yyyy-MM-dd") de la tarjeta virtual.
 * Dato sensible: no se loguea, no se guarda en disco ni en el Bundle, y a propósito NO tiene
 * toString() (el de Object no muestra los campos).
 */
public class TarjetaResponse {
    private String numero;
    private String titular;
    private String cvv;
    private String vencimiento;

    public TarjetaResponse() {}

    public TarjetaResponse(String numero, String titular, String cvv, String vencimiento) {
        this.numero = numero;
        this.titular = titular;
        this.cvv = cvv;
        this.vencimiento = vencimiento;
    }

    public String getNumero() { return numero; }
    public String getTitular() { return titular; }
    public String getCvv() { return cvv; }
    public String getVencimiento() { return vencimiento; }
}
