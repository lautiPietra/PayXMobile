package com.example.payxmobile.model;

/** GET /api/transferencias/destinatario: a quién se le va a transferir (sin crear nada). */
public class DestinatarioResponse {
    private String nombreCompleto;
    private String alias;
    private String cvu;

    public DestinatarioResponse() {}

    public DestinatarioResponse(String nombreCompleto, String alias, String cvu) {
        this.nombreCompleto = nombreCompleto;
        this.alias = alias;
        this.cvu = cvu;
    }

    public String getNombreCompleto() { return nombreCompleto; }
    public String getAlias() { return alias; }
    public String getCvu() { return cvu; }
}
