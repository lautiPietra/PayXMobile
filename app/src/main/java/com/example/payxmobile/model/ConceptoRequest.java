package com.example.payxmobile.model;

/** PATCH /api/transferencias/{id}/concepto (máx. 200; vacío permitido). */
public class ConceptoRequest {
    private final String concepto;

    public ConceptoRequest(String concepto) {
        this.concepto = concepto != null ? concepto : "";
    }
}
