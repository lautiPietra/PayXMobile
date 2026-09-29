package com.example.payxmobile.estadisticas;

import com.example.payxmobile.model.EstadisticaGastosResponse;
import com.example.payxmobile.network.ApiErrores;
import com.example.payxmobile.network.ApiService;

import java.time.LocalDate;
import java.util.function.Supplier;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Pedido de estadísticas por período. Sin polling: se pide al abrir la pantalla, al volver a ella
 * y al cambiar de atajo.
 * - Los datos se guardan JUNTO con el período que los pidió y se reemplazan de una sola vez: nunca
 *   se muestra una mezcla de respuestas.
 * - Si se tocan varios atajos seguido, una respuesta vieja que llega tarde se descarta (como el
 *   "cancelado" de Estadisticas.jsx).
 * - Un error NO deja datos del período anterior bajo el atajo nuevo ni inventa un $ 0.
 * Sin Android: la pantalla lo guarda en un ViewModel.
 */
public class CargaEstadisticas {

    public enum Estado { CARGANDO, LISTO, ERROR }

    public static final String MSG_ERROR = "No pudimos cargar tus estadísticas. Probá de nuevo en un momento.";
    public static final String MSG_RATE_LIMIT =
            "Consultaste tus estadísticas muchas veces seguidas. Esperá un minuto y volvé a intentar.";

    public interface Observador {
        void onCambio(CargaEstadisticas c);
    }

    private final Supplier<ApiService> api;
    private final Supplier<LocalDate> hoy;
    private Observador observador;

    private PeriodoEstadistica periodo = PeriodoEstadistica.POR_DEFECTO;
    private Estado estado = Estado.CARGANDO;
    /** La última respuesta buena y el período al que corresponde (van siempre juntos). */
    private EstadisticaGastosResponse datos;
    private PeriodoEstadistica periodoDatos;
    private String error;
    private boolean sesionInvalida;
    private int generacion;

    public CargaEstadisticas(Supplier<ApiService> api, Supplier<LocalDate> hoy) {
        this.api = api;
        this.hoy = hoy;
    }

    public void setObservador(Observador o) {
        observador = o;
    }

    private void notificar() {
        if (observador != null) observador.onCambio(this);
    }

    /** Cambiar de atajo: nueva consulta (el mismo atajo ya cargado no vuelve a pedir). */
    public void seleccionar(PeriodoEstadistica nuevo) {
        synchronized (this) {
            if (nuevo == null || (nuevo == periodo && estado != Estado.ERROR)) return;
            periodo = nuevo;
        }
        pedir();
    }

    /** Al abrir o volver a la pantalla, y "Reintentar": vuelve a pedir el período elegido. */
    public void refrescar() {
        pedir();
    }

    private void pedir() {
        final int gen;
        final PeriodoEstadistica pedido;
        synchronized (this) {
            gen = ++generacion;
            pedido = periodo;
            estado = Estado.CARGANDO;
            error = null;
        }
        notificar();
        api.get().obtenerEstadisticaGastos(pedido.dias(hoy.get())).enqueue(new Callback<EstadisticaGastosResponse>() {
            @Override
            public void onResponse(Call<EstadisticaGastosResponse> call, Response<EstadisticaGastosResponse> response) {
                synchronized (CargaEstadisticas.this) {
                    if (gen != generacion) return; // llegó tarde: ya se pidió otro período
                    EstadisticaGastosResponse r = response.body();
                    if (response.isSuccessful() && esValida(r)) {
                        datos = r;
                        periodoDatos = pedido;
                        estado = Estado.LISTO;
                    } else if (response.isSuccessful()) {
                        fallo(ApiErrores.MSG_RESPUESTA_INESPERADA);
                    } else if (response.code() == 429) {
                        fallo(MSG_RATE_LIMIT);
                    } else if (response.code() == 403) {
                        fallo(ApiErrores.mensaje(response)); // sin permiso: error normal, la sesión sigue
                    } else if (response.code() == 401) {
                        sesionInvalida = true;
                        fallo(MSG_ERROR);
                    } else if (response.code() >= 500) {
                        fallo(ApiErrores.MSG_SERVIDOR);
                    } else {
                        fallo(MSG_ERROR);
                    }
                }
                notificar();
            }

            @Override
            public void onFailure(Call<EstadisticaGastosResponse> call, Throwable t) {
                synchronized (CargaEstadisticas.this) {
                    if (gen != generacion) return;
                    fallo(ApiErrores.mensajeFallo(t));
                }
                notificar();
            }
        });
    }

    private void fallo(String mensaje) {
        estado = Estado.ERROR;
        error = mensaje;
        // Los datos de OTRO período no se muestran bajo el atajo elegido
        if (periodoDatos != periodo) {
            datos = null;
            periodoDatos = null;
        }
    }

    /** Lo mínimo para dibujar sin inventar nada: total, listas y "hasta". */
    static boolean esValida(EstadisticaGastosResponse r) {
        return r != null && r.getTotalGastado() != null && r.getCategorias() != null && r.getPorDia() != null
                && r.getHasta() != null;
    }

    public synchronized PeriodoEstadistica getPeriodo() { return periodo; }
    public synchronized Estado getEstado() { return estado; }
    public synchronized String getError() { return error; }
    public synchronized boolean isSesionInvalida() { return sesionInvalida; }

    /** Los datos a dibujar, solo si son del período elegido (null si no hay). */
    public synchronized EstadisticaGastosResponse getDatos() {
        return periodoDatos == periodo ? datos : null;
    }

    /** Restaurar el atajo elegido (rotación con proceso muerto): no pide nada, lo hace refrescar(). */
    public synchronized void restaurar(PeriodoEstadistica p) {
        if (p != null) periodo = p;
    }
}
