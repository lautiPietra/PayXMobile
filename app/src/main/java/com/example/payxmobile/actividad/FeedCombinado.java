package com.example.payxmobile.actividad;

import com.example.payxmobile.model.OperacionCambioResponse;
import com.example.payxmobile.model.OperacionCriptoResponse;
import com.example.payxmobile.model.TransferenciaResponse;

import java.util.List;

/**
 * Arma el feed (Inicio y "Mis movimientos") a partir de las listas de cada repositorio y lo
 * reusa mientras ninguna cambie: los repositorios publican listas inmutables, así que alcanza con
 * comparar instancias. Para sumar plazos fijos: otra lista más acá y en Actividades.construir.
 */
public final class FeedCombinado {

    private List<TransferenciaResponse> transferencias;
    private List<OperacionCambioResponse> dolares;
    private List<OperacionCriptoResponse> cripto;
    private List<Actividad> feed;
    private boolean armado;

    /** Hasta que no se cargan las transferencias no hay feed (null): la pantalla muestra "Cargando". */
    public List<Actividad> de(List<TransferenciaResponse> t, List<OperacionCambioResponse> d,
                              List<OperacionCriptoResponse> c) {
        if (!armado || t != transferencias || d != dolares || c != cripto) {
            transferencias = t;
            dolares = d;
            cripto = c;
            feed = t != null ? Actividades.construir(t, d, c) : null;
            armado = true;
        }
        return feed;
    }
}
