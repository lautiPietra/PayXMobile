package com.example.payxmobile.tarjeta;

import com.example.payxmobile.model.TarjetaResponse;
import com.example.payxmobile.network.ApiErrores;
import com.example.payxmobile.network.ApiService;

import java.util.function.LongSupplier;
import java.util.function.Supplier;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Revelar el número completo y el CVV (GET /api/tarjeta), como TarjetaVirtual.jsx:
 * - El GET sale SOLO cuando el usuario toca "Ver tarjeta completa" (nunca al abrir la app, Inicio
 *   ni esta pantalla). Ocultar/mostrar de nuevo no repite el pedido.
 * - {@link #olvidar()} descarta los datos (al salir de la pantalla): al volver hay que pedirlos otra vez.
 * - 429: la ventana del backend es de 1 minuto, así que se bloquea el reintento 60 s.
 * - 403: token inválido/vencido o cuenta desactivada -> la pantalla cierra la sesión.
 * Nada de esto se loguea ni se guarda fuera de memoria. Sin Android: vive en un ViewModel.
 */
public class RevelarTarjeta {

    public enum Estado { OCULTA, CARGANDO, VISIBLE, ERROR }

    public static final long ESPERA_RATE_LIMIT_MS = 60_000L;
    public static final String MSG_ERROR = "No pudimos obtener los datos de tu tarjeta.";
    public static final String MSG_RATE_LIMIT =
            "Pediste los datos de tu tarjeta muchas veces seguidas. Esperá un minuto y probá de nuevo.";

    public interface Observador {
        void onCambio(RevelarTarjeta r);
    }

    private final Supplier<ApiService> api;
    private final LongSupplier reloj;
    private Observador observador;

    private Estado estado = Estado.OCULTA;
    private TarjetaResponse detalle;
    private String error;
    private boolean sesionInvalida;
    /** Momento (ms) del último 429; 0 = ninguno. */
    private long rateLimitDesde;
    /** Cambia con olvidar(): una respuesta de un pedido anterior se descarta. */
    private int generacion;

    public RevelarTarjeta(Supplier<ApiService> api, LongSupplier reloj) {
        this.api = api;
        this.reloj = reloj;
    }

    public void setObservador(Observador o) {
        observador = o;
    }

    private void notificar() {
        if (observador != null) observador.onCambio(this);
    }

    /** "Ver tarjeta completa" (o "Reintentar"). */
    public void revelar() {
        final int gen;
        synchronized (this) {
            if (estado == Estado.CARGANDO || segundosParaReintentar() > 0) return;
            if (detalle != null) { // ya se pidió en esta visita: no se repite el GET
                estado = Estado.VISIBLE;
                error = null;
                gen = -1;
            } else {
                estado = Estado.CARGANDO;
                error = null;
                gen = generacion;
            }
        }
        notificar();
        if (gen < 0) return;
        api.get().obtenerTarjeta().enqueue(new Callback<TarjetaResponse>() {
            @Override
            public void onResponse(Call<TarjetaResponse> call, Response<TarjetaResponse> response) {
                synchronized (RevelarTarjeta.this) {
                    if (gen != generacion) return;
                    TarjetaResponse t = response.body();
                    if (response.isSuccessful() && FormatoTarjeta.esValida(t)) {
                        detalle = t;
                        estado = Estado.VISIBLE;
                    } else if (response.isSuccessful()) {
                        fallo(ApiErrores.MSG_RESPUESTA_INESPERADA);
                    } else if (response.code() == 429) {
                        rateLimitDesde = reloj.getAsLong();
                        fallo(MSG_RATE_LIMIT);
                    } else if (response.code() == 401 || response.code() == 403) {
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
            public void onFailure(Call<TarjetaResponse> call, Throwable t) {
                synchronized (RevelarTarjeta.this) {
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
    }

    /** "Ocultar": tapa todo de nuevo (los datos quedan en memoria mientras siga en la pantalla). */
    public void ocultar() {
        synchronized (this) {
            if (estado != Estado.VISIBLE) return;
            estado = Estado.OCULTA;
        }
        notificar();
    }

    /** Al salir de la pantalla: descarta número y CVV (y cualquier pedido en vuelo). */
    public void olvidar() {
        synchronized (this) {
            generacion++;
            detalle = null;
            error = null;
            estado = Estado.OCULTA; // una espera por 429 sigue vigente (rateLimitDesde)
        }
        notificar();
    }

    /** Cuánto falta para poder reintentar tras un 429 (0 = ya se puede). */
    public synchronized long segundosParaReintentar() {
        if (rateLimitDesde == 0) return 0;
        long restante = rateLimitDesde + ESPERA_RATE_LIMIT_MS - reloj.getAsLong();
        return restante <= 0 ? 0 : (restante + 999) / 1000;
    }

    public synchronized Estado getEstado() { return estado; }
    public synchronized String getError() { return error; }
    public synchronized boolean isSesionInvalida() { return sesionInvalida; }
    public synchronized boolean isVisible() { return estado == Estado.VISIBLE && detalle != null; }

    /** Los datos completos, SOLO si están a la vista (null si no): la UI no puede mostrarlos por error. */
    public synchronized TarjetaResponse getVisible() {
        return isVisible() ? detalle : null;
    }
}
