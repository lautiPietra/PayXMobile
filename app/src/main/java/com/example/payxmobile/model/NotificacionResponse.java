package com.example.payxmobile.model;

/**
 * GET /api/notificaciones (solo las NO leídas). "mensaje" ya viene armado por el backend (con la
 * fecha adentro): se muestra tal cual.
 */
public class NotificacionResponse {
    private Long id;
    private String usuarioId;
    private String plantillaCodigo;
    private String mensaje;
    private boolean leida;
    private String fecha;

    public NotificacionResponse() {}

    public NotificacionResponse(Long id, String plantillaCodigo, String mensaje, String fecha) {
        this.id = id;
        this.plantillaCodigo = plantillaCodigo;
        this.mensaje = mensaje;
        this.fecha = fecha;
    }

    public Long getId() { return id; }
    public String getUsuarioId() { return usuarioId; }
    public String getMensaje() { return mensaje; }
    public boolean isLeida() { return leida; }
    public String getFecha() { return fecha; }
    public String getPlantillaCodigo() { return plantillaCodigo; }
}
