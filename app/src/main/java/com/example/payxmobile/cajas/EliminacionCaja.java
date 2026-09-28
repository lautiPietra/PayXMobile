package com.example.payxmobile.cajas;

import com.example.payxmobile.model.CajaAhorroResponse;
import com.example.payxmobile.network.ApiService;
import com.example.payxmobile.network.EnvioSinReintento;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Eliminar una caja (DELETE, 204 sin body). El backend (CajaAhorroService.eliminar) DEVUELVE el
 * saldo que tenía a la cuenta principal antes de borrarla: la plata no se pierde. No genera
 * notificación. En el feed de la sesión se registra como un retiro de ese saldo.
 *
 * Un solo pedido en vuelo (para cualquier caja). Sin respuesta -> INCIERTO + refresco.
 */
public class EliminacionCaja {

    public enum Estado { INACTIVO, ENVIANDO, ELIMINADA, ERROR, INCIERTO }

    public static final String MSG_NO_SE_PUDO = "No se pudo eliminar la caja. Intentá de nuevo.";
    public static final String MSG_INCIERTO =
            "No pudimos confirmar si la caja se eliminó. Revisá tus cajas y tu saldo antes de intentar de nuevo";

    public interface Observador {
        void onCambio(EliminacionCaja e);
    }

    private final Supplier<ApiService> apiSinReintentos;
    private final MovimientosCajaSesion sesion;
    private final Clock reloj;
    private final Consumer<CajaAhorroResponse> alEliminar;
    private final Runnable alRefrescar;
    private Observador observador;

    private Estado estado = Estado.INACTIVO;
    private CajaAhorroResponse caja;
    private String mensaje;

    /**
     * @param alEliminar  con la caja eliminada (sacarla ya de la lista)
     * @param alRefrescar tras eliminar o no saber si se eliminó: saldos (sube si tenía plata) y cajas
     */
    public EliminacionCaja(Supplier<ApiService> apiSinReintentos, MovimientosCajaSesion sesion, Clock reloj,
                           Consumer<CajaAhorroResponse> alEliminar, Runnable alRefrescar) {
        this.apiSinReintentos = apiSinReintentos;
        this.sesion = sesion;
        this.reloj = reloj;
        this.alEliminar = alEliminar;
        this.alRefrescar = alRefrescar;
    }

    public void setObservador(Observador o) {
        observador = o;
    }

    private void notificar() {
        if (observador != null) observador.onCambio(this);
    }

    /** ¿Hace falta avisar que el saldo vuelve a la cuenta? */
    public static boolean tieneSaldo(CajaAhorroResponse c) {
        return c != null && c.getSaldo() != null && c.getSaldo().signum() > 0;
    }

    public void eliminar(CajaAhorroResponse aEliminar) {
        if (aEliminar == null || estado == Estado.ENVIANDO) return; // un solo pedido en vuelo
        caja = aEliminar;
        estado = Estado.ENVIANDO;
        mensaje = null;
        notificar();
        EnvioSinReintento.enviar(apiSinReintentos.get().eliminarCajaAhorro(aEliminar.getId()), false,
                new EnvioSinReintento.Manejador<Void>() {
                    @Override
                    public void exito(Void body) {
                        estado = Estado.ELIMINADA;
                        if (tieneSaldo(aEliminar)) sesion.registrar(MovimientoCaja.deEliminacion(aEliminar, reloj));
                        alEliminar.accept(aEliminar);
                        alRefrescar.run();
                        notificar();
                    }

                    @Override
                    public void rechazo(String texto, int codigo) {
                        estado = Estado.ERROR;
                        mensaje = EnvioSinReintento.textoRechazo(texto, MSG_NO_SE_PUDO);
                        alRefrescar.run();
                        notificar();
                    }

                    @Override
                    public void noEnviado(String texto) {
                        estado = Estado.ERROR;
                        mensaje = texto;
                        notificar();
                    }

                    @Override
                    public void incierto() {
                        estado = Estado.INCIERTO;
                        mensaje = MSG_INCIERTO;
                        alRefrescar.run();
                        notificar();
                    }
                });
    }

    /** La UI ya mostró el resultado: vuelve a INACTIVO (no se muestra dos veces tras rotar). */
    public void consumir() {
        if (estado == Estado.ENVIANDO) return;
        estado = Estado.INACTIVO;
        mensaje = null;
        notificar();
    }

    public Estado getEstado() { return estado; }
    public CajaAhorroResponse getCaja() { return caja; }
    public String getMensaje() { return mensaje; }
    public boolean isEnviando() { return estado == Estado.ENVIANDO; }
    /** El id de la caja que se está eliminando (para deshabilitar su tarjeta), o null. */
    public String idEnviando() { return estado == Estado.ENVIANDO && caja != null ? caja.getId() : null; }
    /** Lo que volvió a la cuenta principal (0 si estaba vacía). */
    public BigDecimal saldoDevuelto() { return caja != null && caja.getSaldo() != null ? caja.getSaldo() : BigDecimal.ZERO; }
}
