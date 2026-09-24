package com.example.payxmobile.transferencias;

import com.example.payxmobile.model.ConceptoRequest;
import com.example.payxmobile.model.TransferenciaResponse;
import com.example.payxmobile.network.ApiErrores;
import com.example.payxmobile.network.ApiService;

import java.util.function.Consumer;
import java.util.function.Supplier;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Detalle de una transferencia: cambiar concepto, confirmar y cancelar (solo el emisor).
 * Una sola acción en vuelo a la vez. Los errores del backend se muestran tal cual, incluido el
 * 403 CON body ("Solo quien envio la transferencia..."), que NO es sesión vencida.
 */
public class DetalleTransferencia {

    public static final int MAX_CONCEPTO = 200;
    public static final String MSG_CONCEPTO_LARGO = "El concepto no puede superar los 200 caracteres";

    public interface Observador {
        void onCambio(DetalleTransferencia detalle);
    }

    private final Supplier<ApiService> api;
    private final Consumer<TransferenciaResponse> alActualizar;
    private final Runnable alMoverPlata;
    private Observador observador;

    private TransferenciaResponse transferencia;
    private String error;
    private boolean procesando; // confirmar / cancelar
    private boolean guardando;  // concepto

    public DetalleTransferencia(TransferenciaResponse inicial, Supplier<ApiService> api,
                                Consumer<TransferenciaResponse> alActualizar, Runnable alMoverPlata) {
        this.transferencia = inicial;
        this.api = api;
        this.alActualizar = alActualizar;
        this.alMoverPlata = alMoverPlata;
    }

    public void setObservador(Observador o) {
        observador = o;
    }

    private void notificar() {
        if (observador != null) observador.onCambio(this);
    }

    public TransferenciaResponse getTransferencia() { return transferencia; }
    public String getError() { return error; }
    public boolean isProcesando() { return procesando; }
    public boolean isGuardando() { return guardando; }

    /** Solo quien la envió puede cambiar el concepto (en cualquier estado). */
    public boolean puedeCambiarConcepto() {
        return transferencia.isEsEmisor();
    }

    /** Confirmar / cancelar: solo el emisor y solo si está PENDIENTE. */
    public boolean puedeConfirmarOCancelar() {
        return transferencia.isEsEmisor() && transferencia.esPendiente();
    }

    /** Versión nueva que trajo el refresco del listado (el otro lado o el vencimiento la cambiaron). */
    public void actualizarDesdeListado(TransferenciaResponse nueva) {
        if (nueva == null || procesando || guardando || !nueva.getId().equals(transferencia.getId())) return;
        transferencia = nueva;
        notificar();
    }

    /** GET /{id}: trae la versión más nueva al abrir el detalle. */
    public void refrescar() {
        if (procesando || guardando) return;
        api.get().obtenerTransferencia(transferencia.getId()).enqueue(new Callback<TransferenciaResponse>() {
            @Override
            public void onResponse(Call<TransferenciaResponse> call, Response<TransferenciaResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    if (procesando || guardando) return;
                    transferencia = response.body();
                    alActualizar.accept(transferencia);
                } else {
                    error = ApiErrores.mensaje(response); // 404 / 403 con {"error"}
                }
                notificar();
            }

            @Override
            public void onFailure(Call<TransferenciaResponse> call, Throwable t) {
                // Se sigue mostrando la versión del listado; el próximo refresco reintenta
            }
        });
    }

    public void guardarConcepto(String texto) {
        if (guardando || procesando || !puedeCambiarConcepto()) return;
        String concepto = texto != null ? texto : "";
        if (concepto.length() > MAX_CONCEPTO) {
            error = MSG_CONCEPTO_LARGO;
            notificar();
            return;
        }
        guardando = true;
        error = null;
        notificar();
        api.get().actualizarConcepto(transferencia.getId(), new ConceptoRequest(concepto))
                .enqueue(accion(() -> guardando = false, "No se pudo actualizar el detalle", false));
    }

    public void confirmar() {
        if (procesando || guardando || !puedeConfirmarOCancelar()) return; // doble tap -> un pedido
        procesando = true;
        error = null;
        notificar();
        api.get().confirmarTransferencia(transferencia.getId())
                .enqueue(accion(() -> procesando = false, "No se pudo confirmar la transferencia", true));
    }

    public void cancelar() {
        if (procesando || guardando || !puedeConfirmarOCancelar()) return;
        procesando = true;
        error = null;
        notificar();
        api.get().cancelarTransferencia(transferencia.getId())
                .enqueue(accion(() -> procesando = false, "No se pudo cancelar la transferencia", false));
    }

    private Callback<TransferenciaResponse> accion(Runnable terminar, String porDefecto, boolean muevePlata) {
        return new Callback<TransferenciaResponse>() {
            @Override
            public void onResponse(Call<TransferenciaResponse> call, Response<TransferenciaResponse> response) {
                terminar.run();
                if (response.isSuccessful() && response.body() != null) {
                    transferencia = response.body();
                    alActualizar.accept(transferencia);
                    if (muevePlata) alMoverPlata.run();
                } else {
                    String mensaje = ApiErrores.mensaje(response);
                    error = mensaje.equals(ApiErrores.MSG_RESPUESTA_INESPERADA) ? porDefecto : mensaje;
                }
                notificar();
            }

            @Override
            public void onFailure(Call<TransferenciaResponse> call, Throwable t) {
                terminar.run();
                error = ApiErrores.mensajeFallo(t);
                // Un confirmar sin respuesta pudo haberse aplicado: refrescar para ver el estado real
                if (muevePlata) alMoverPlata.run();
                notificar();
            }
        };
    }
}
