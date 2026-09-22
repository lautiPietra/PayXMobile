package com.example.payxmobile.model;

public class ActualizarPerfilRequest {
    private String nombreUsuario;
    private String telefono;
    private String alias;

    public ActualizarPerfilRequest(String nombreUsuario, String telefono, String alias) {
        this.nombreUsuario = nombreUsuario;
        this.telefono = telefono;
        this.alias = alias;
    }
}
