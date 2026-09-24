package com.example.payxmobile.model;

public class LoginResponse {
    private String token;
    private String id;
    private String nombreCompleto;
    private String email;
    private String nombreUsuario;
    private String rol;
    private String fotoPerfilUrl;

    public String getToken() { return token; }
    public String getId() { return id; }
    public String getNombreCompleto() { return nombreCompleto; }
    public String getEmail() { return email; }
    public String getNombreUsuario() { return nombreUsuario; }
    public String getRol() { return rol; }
    public String getFotoPerfilUrl() { return fotoPerfilUrl; }
}
