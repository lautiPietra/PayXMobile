package com.example.payxmobile.model;

public class PerfilResponse {
    private String id;
    private String email;
    private String nombreCompleto;
    private String dni;
    private String telefono;
    private String nombreUsuario;
    private String alias;
    private String cvu;
    private Double saldoPesos;
    private Double saldoUsd;

    public String getId() { return id; }
    public String getEmail() { return email; }
    public String getNombreCompleto() { return nombreCompleto; }
    public String getDni() { return dni; }
    public String getTelefono() { return telefono; }
    public String getNombreUsuario() { return nombreUsuario; }
    public String getAlias() { return alias; }
    public String getCvu() { return cvu; }
    public double getSaldoPesos() { return saldoPesos != null ? saldoPesos : 0; }
    public double getSaldoUsd() { return saldoUsd != null ? saldoUsd : 0; }
}
