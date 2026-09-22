package com.example.payxmobile.model;

public class RegistroRequest {
    private String nombreCompleto;
    private String email;
    private String telefono;
    private String nombreUsuario;
    private String dni;
    private String password;

    public RegistroRequest(String nombreCompleto, String email, String telefono,
                           String nombreUsuario, String dni, String password) {
        this.nombreCompleto = nombreCompleto;
        this.email = email;
        this.telefono = telefono;
        this.nombreUsuario = nombreUsuario;
        this.dni = dni;
        this.password = password;
    }

    public String getNombreCompleto() { return nombreCompleto; }
    public String getEmail() { return email; }
    public String getTelefono() { return telefono; }
    public String getNombreUsuario() { return nombreUsuario; }
    public String getDni() { return dni; }
    public String getPassword() { return password; }
}
