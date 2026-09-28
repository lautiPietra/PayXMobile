package com.example.payxmobile.actividad;

import com.example.payxmobile.cajas.MovimientoCaja;
import com.example.payxmobile.model.FacturaResponse;
import com.example.payxmobile.model.OperacionCambioResponse;
import com.example.payxmobile.model.OperacionCriptoResponse;
import com.example.payxmobile.model.PlazoFijoResponse;
import com.example.payxmobile.model.TransferenciaResponse;

import java.time.ZoneId;
import java.util.List;

/**
 * Arma el feed (Inicio y "Mis movimientos") a partir de las listas de cada repositorio y lo
 * reusa mientras ninguna cambie: los repositorios publican listas inmutables, así que alcanza con
 * comparar instancias.
 */
public final class FeedCombinado {

    private final ZoneId zona;
    private List<TransferenciaResponse> transferencias;
    private List<OperacionCambioResponse> dolares;
    private List<OperacionCriptoResponse> cripto;
    private List<PlazoFijoResponse> plazosFijos;
    private List<MovimientoCaja> cajas;
    private List<FacturaResponse> facturas;
    private List<Actividad> feed;
    private boolean armado;

    public FeedCombinado() {
        this(ZoneId.systemDefault());
    }

    /** @param zona en la que se ubica el día de acreditación de un plazo fijo (el del teléfono). */
    public FeedCombinado(ZoneId zona) {
        this.zona = zona;
    }

    public List<Actividad> de(List<TransferenciaResponse> t, List<OperacionCambioResponse> d,
                              List<OperacionCriptoResponse> c) {
        return de(t, d, c, null, null, null);
    }

    public List<Actividad> de(List<TransferenciaResponse> t, List<OperacionCambioResponse> d,
                              List<OperacionCriptoResponse> c, List<PlazoFijoResponse> pf) {
        return de(t, d, c, pf, null, null);
    }

    public List<Actividad> de(List<TransferenciaResponse> t, List<OperacionCambioResponse> d,
                              List<OperacionCriptoResponse> c, List<PlazoFijoResponse> pf,
                              List<MovimientoCaja> ca) {
        return de(t, d, c, pf, ca, null);
    }

    /** Hasta que no se cargan las transferencias no hay feed (null): la pantalla muestra "Cargando". */
    public List<Actividad> de(List<TransferenciaResponse> t, List<OperacionCambioResponse> d,
                              List<OperacionCriptoResponse> c, List<PlazoFijoResponse> pf,
                              List<MovimientoCaja> ca, List<FacturaResponse> sv) {
        if (!armado || t != transferencias || d != dolares || c != cripto || pf != plazosFijos || ca != cajas
                || sv != facturas) {
            transferencias = t;
            dolares = d;
            cripto = c;
            plazosFijos = pf;
            cajas = ca;
            facturas = sv;
            feed = t != null ? Actividades.construir(t, d, c, pf, ca, sv, zona) : null;
            armado = true;
        }
        return feed;
    }
}
