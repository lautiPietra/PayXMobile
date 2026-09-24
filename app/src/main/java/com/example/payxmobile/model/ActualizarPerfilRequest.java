package com.example.payxmobile.model;

public class ActualizarPerfilRequest {
    private String nombreUsuario;
    private String telefono;
    private String alias;
    // Solo se manda cuando la cuenta todavía no tiene DNI. Si es null, Gson omite el campo
    // (nunca se manda dni:"", que no cumple el regex y da 403).
    private String dni;

    public ActualizarPerfilRequest(String nombreUsuario, String telefono, String alias, String dni) {
        this.nombreUsuario = nombreUsuario;
        this.telefono = telefono;
        this.alias = alias;
        this.dni = (dni == null || dni.trim().isEmpty()) ? null : dni.trim();
    }
}
