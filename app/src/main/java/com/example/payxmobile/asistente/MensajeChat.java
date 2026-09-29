package com.example.payxmobile.asistente;

import com.example.payxmobile.model.AsistenteRespuestaResponse.AccionSugerida;

/**
 * Una burbuja del chat. El saludo inicial NO es un MensajeChat (es solo visual y nunca viaja al
 * backend). "fallido" marca un mensaje del usuario cuyo pedido no obtuvo respuesta: queda en pantalla
 * para reintentarlo, pero no se manda como parte del historial (el asistente nunca lo vio).
 */
public final class MensajeChat {

    public static final String USUARIO = "USUARIO";
    public static final String ASISTENTE = "ASISTENTE";

    public final String rol;
    public final String texto;
    /** Solo en mensajes del asistente; null si no preparó nada. */
    public final AccionSugerida accion;
    public final boolean fallido;

    public MensajeChat(String rol, String texto, AccionSugerida accion, boolean fallido) {
        this.rol = rol;
        this.texto = texto;
        this.accion = accion;
        this.fallido = fallido;
    }

    public static MensajeChat delUsuario(String texto) {
        return new MensajeChat(USUARIO, texto, null, false);
    }

    public static MensajeChat delAsistente(String texto, AccionSugerida accion) {
        return new MensajeChat(ASISTENTE, texto, accion, false);
    }

    public boolean esDelUsuario() {
        return USUARIO.equals(rol);
    }

    /** Hay una transferencia preparada para mostrar como tarjeta. */
    public boolean tieneTransferencia() {
        return accion != null && accion.esTransferencia();
    }

    MensajeChat comoFallido() {
        return new MensajeChat(rol, texto, accion, true);
    }
}
