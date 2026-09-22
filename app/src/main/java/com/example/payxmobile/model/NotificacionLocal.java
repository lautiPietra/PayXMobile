package com.example.payxmobile.model;

public class NotificacionLocal {
    private String tipo;
    private String mensaje;
    private long timestamp;
    private boolean leida;

    public NotificacionLocal() {}

    public NotificacionLocal(String tipo, String mensaje, long timestamp) {
        this.tipo = tipo;
        this.mensaje = mensaje;
        this.timestamp = timestamp;
        this.leida = false;
    }

    public String getTipo() { return tipo; }
    public String getMensaje() { return mensaje; }
    public long getTimestamp() { return timestamp; }
    public boolean isLeida() { return leida; }
    public void setLeida(boolean leida) { this.leida = leida; }
}
