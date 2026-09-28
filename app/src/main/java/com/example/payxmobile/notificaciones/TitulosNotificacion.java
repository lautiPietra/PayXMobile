package com.example.payxmobile.notificaciones;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Título corto de cada notificación según su "plantillaCodigo" (como TITULOS_PLANTILLA de
 * Navbar.jsx). Un código que no esté acá NUNCA rompe ni esconde la fila: usa {@link #GENERICO}.
 *
 * Para sumar un módulo (cripto, plazo fijo, caja de ahorro...) alcanza con agregar sus códigos
 * en el bloque estático de abajo; nada más depende de esta lista.
 */
public final class TitulosNotificacion {

    public static final String GENERICO = "Notificación";

    private static final Map<String, String> TITULOS;

    static {
        Map<String, String> t = new LinkedHashMap<>();
        // Sesión
        t.put("INICIO_SES", "Inicio de sesión");
        // Transferencias
        t.put("TRANSFERENCIA_ENVIADA", "Transferencia enviada");
        t.put("TRANSFERENCIA_RECIBIDA", "Transferencia recibida");
        t.put("TRANSFERENCIA_VENCIDA", "Transferencia vencida");
        // Dólares
        t.put("DOLARES_COMPRADOS", "Compra de dólares");
        t.put("DOLARES_VENDIDOS", "Venta de dólares");
        // Cripto (el "mensaje" ya dice qué símbolo era)
        t.put("CRIPTO_COMPRADA", "Compra de cripto");
        t.put("CRIPTO_VENDIDA", "Venta de cripto");
        // Plazos fijos (el vencimiento lo genera el backend solo al acreditar)
        t.put("PLAZO_FIJO_CONSTITUIDO", "Plazo fijo constituido");
        t.put("PLAZO_FIJO_VENCIDO", "Plazo fijo acreditado");
        // Cajas de ahorro (un depósito que llega a la meta genera DOS: depósito + meta alcanzada).
        // La web todavía no tiene título para META_ALCANZADA: este es propio de la app.
        t.put("CAJA_AHORRO_DEPOSITO", "Depósito en caja de ahorro");
        t.put("CAJA_AHORRO_RETIRO", "Retiro de caja de ahorro");
        t.put("CAJA_AHORRO_META_ALCANZADA", "Meta alcanzada");
        // Servicios (el mensaje ya trae el nombre del servicio y el monto)
        t.put("SERVICIO_PAGADO", "Pago de servicio");
        TITULOS = Collections.unmodifiableMap(t);
    }

    private TitulosNotificacion() {}

    public static String de(String codigo) {
        String titulo = codigo != null ? TITULOS.get(codigo) : null;
        return titulo != null ? titulo : GENERICO;
    }

    /** Los códigos conocidos (solo lectura), para tests. */
    public static Map<String, String> todos() {
        return TITULOS;
    }
}
