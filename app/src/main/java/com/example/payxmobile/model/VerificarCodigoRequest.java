package com.example.payxmobile.model;

public class VerificarCodigoRequest {
    private String email;
    private String codigo;

    public VerificarCodigoRequest(String email, String codigo) {
        this.email = email;
        this.codigo = codigo;
    }

    public String getEmail() { return email; }
    public String getCodigo() { return codigo; }
}
