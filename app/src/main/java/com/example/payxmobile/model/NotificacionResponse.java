package com.example.payxmobile.model;

public class NotificacionResponse {
    private Long id;
    private String usuarioId;
    private String plantillaCodigo;
    private String mensaje;
    private boolean leida;
    private String fecha;

    public Long getId() { return id; }
    public String getMensaje() { return mensaje; }
    public boolean isLeida() { return leida; }
    public String getFecha() { return fecha; }
    public String getPlantillaCodigo() { return plantillaCodigo; }
}
