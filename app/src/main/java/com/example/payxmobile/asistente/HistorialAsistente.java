package com.example.payxmobile.asistente;

import com.example.payxmobile.model.AsistenteMensajeRequest.Turno;

import java.util.ArrayList;
import java.util.List;

/**
 * Lo que se le manda al backend de la conversación guardada. Los topes son los de
 * AsistenteMensajeRequest en el backend: pasarse de cualquiera es un 400.
 */
public final class HistorialAsistente {

    /** @Size(max=30) de "historial". */
    public static final int MAX_TURNOS = 30;
    /** @Size(max=4000) de cada Turno.texto. */
    public static final int MAX_CARACTERES_TURNO = 4000;
    /** @Size(max=1000) del mensaje nuevo. */
    public static final int MAX_CARACTERES_MENSAJE = 1000;

    private HistorialAsistente() {}

    /**
     * Los últimos 30 turnos, con cada texto truncado a 4000 caracteres. Se saltean los mensajes
     * fallidos (el asistente nunca los recibió) y los vacíos (el backend exige @NotBlank).
     */
    public static List<Turno> recortar(List<MensajeChat> mensajes) {
        List<Turno> turnos = new ArrayList<>();
        for (MensajeChat m : mensajes) {
            if (m.fallido || m.texto == null || m.texto.trim().isEmpty()) continue;
            turnos.add(new Turno(m.rol, truncar(m.texto)));
        }
        int desde = Math.max(0, turnos.size() - MAX_TURNOS);
        return new ArrayList<>(turnos.subList(desde, turnos.size()));
    }

    static String truncar(String texto) {
        if (texto.length() <= MAX_CARACTERES_TURNO) return texto;
        int fin = MAX_CARACTERES_TURNO;
        // No partir un emoji (par surrogate) a la mitad: quedaría un carácter inválido
        if (Character.isHighSurrogate(texto.charAt(fin - 1))) fin--;
        return texto.substring(0, fin);
    }

    /** El mensaje nuevo, listo para mandar, o null si no se puede (vacío o más de 1000 caracteres). */
    public static String mensajeValido(String texto) {
        if (texto == null) return null;
        String limpio = texto.trim();
        if (limpio.isEmpty() || limpio.length() > MAX_CARACTERES_MENSAJE) return null;
        return limpio;
    }
}
