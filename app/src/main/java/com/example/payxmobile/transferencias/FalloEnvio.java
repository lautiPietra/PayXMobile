package com.example.payxmobile.transferencias;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;

/**
 * Qué se sabe cuando el POST de una transferencia falla SIN respuesta. El backend no tiene
 * idempotencia: si no hay certeza de que el pedido no salió, NO se reintenta.
 */
public final class FalloEnvio {

    public static final String MSG_INCIERTO =
            "No pudimos confirmar si la transferencia se realizó. Revisá tus transferencias antes de intentar de nuevo";

    private FalloEnvio() {}

    /**
     * true = seguro que el pedido nunca llegó al servidor (no se pudo abrir la conexión), así que
     * se puede reintentar. Todo lo demás (timeout, corte después de enviar, respuesta ilegible)
     * es incierto.
     */
    public static boolean seguroNoEnviado(Throwable t) {
        return t instanceof ConnectException || t instanceof UnknownHostException
                || t instanceof NoRouteToHostException;
    }
}
