package com.example.payxmobile.model;

import java.util.List;

/**
 * POST /api/asistente/mensaje. "historial" es la conversación hasta ahora SIN el mensaje nuevo (que va
 * en "mensaje"): el backend no guarda nada, así que se manda completa en cada pedido, ya recortada
 * (ver asistente/HistorialAsistente: máx. 30 turnos y 4000 caracteres por texto).
 */
public class AsistenteMensajeRequest {
    private final String mensaje;
    private final List<Turno> historial;

    public AsistenteMensajeRequest(String mensaje, List<Turno> historial) {
        this.mensaje = mensaje;
        this.historial = historial;
    }

    public String getMensaje() { return mensaje; }
    public List<Turno> getHistorial() { return historial; }

    /** rol: "USUARIO" o "ASISTENTE" (el backend rechaza cualquier otro). */
    public static class Turno {
        private final String rol;
        private final String texto;

        public Turno(String rol, String texto) {
            this.rol = rol;
            this.texto = texto;
        }

        public String getRol() { return rol; }
        public String getTexto() { return texto; }
    }
}
