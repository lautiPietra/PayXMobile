package com.example.payxmobile.cajas;

import com.example.payxmobile.model.CajaAhorroResponse;
import com.example.payxmobile.model.MontoCajaAhorroRequest;
import com.example.payxmobile.network.ApiService;
import com.example.payxmobile.network.EnvioSinReintento;
import com.example.payxmobile.transferencias.Moneda;
import com.example.payxmobile.transferencias.MontoInput;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.function.Consumer;
import java.util.function.Supplier;

import retrofit2.Call;

/**
 * Depositar en una caja (saldo PRINCIPAL en pesos -> caja) o retirar (caja -> saldo principal), como
 * MontoCajaModal.jsx: monto con "Usar todo" y un solo paso (es plata que sigue siendo del usuario).
 *
 * - "Usar todo" y el tope: el saldo principal al depositar; el saldo de la CAJA al retirar.
 * - 200: la caja con su saldo nuevo -> se muestra ya, se registra el movimiento en la sesión (feed)
 *   y se refrescan saldos y notificaciones (un depósito puede generar DOS: depósito y meta alcanzada).
 * - Sin idempotencia en el backend: un solo pedido en vuelo; sin respuesta -> INCIERTO + refresco.
 */
public class OperacionMontoCaja {

    public enum Paso { FORMULARIO, EXITO, INCIERTO }

    public static final String MSG_MONTO_INVALIDO = "Ingresá un monto válido.";
    public static final String MSG_DECIMALES = "El monto puede tener como máximo 2 decimales.";
    public static final String MSG_SIN_SALDO = "Todavía no pudimos cargar tu saldo. Probá de nuevo en unos segundos.";
    public static final String MSG_INSUFICIENTE_DEPOSITO = "No tenés saldo suficiente en tu cuenta.";
    public static final String MSG_INSUFICIENTE_RETIRO = "La caja no tiene suficiente saldo.";
    public static final String MSG_NO_SE_PUDO = "No se pudo completar la operación. Intentá de nuevo.";
    public static final String MSG_INCIERTO =
            "No pudimos confirmar si la operación se realizó. Revisá tu caja y tu saldo antes de intentar de nuevo";

    public interface Observador {
        void onCambio(OperacionMontoCaja op);
    }

    private final MovimientoCaja.Tipo tipo;
    private final Supplier<ApiService> apiSinReintentos;
    private final MovimientosCajaSesion sesion;
    private final Clock reloj;
    private final Consumer<CajaAhorroResponse> alExito;
    private final Runnable alRefrescar;
    private Observador observador;

    /** La caja tal como estaba antes de operar (se actualiza con la lista mientras no se opere). */
    private CajaAhorroResponse caja;
    private String montoTexto = "";
    private Paso paso = Paso.FORMULARIO;
    private String error;
    private boolean enviando;
    private BigDecimal montoEnviado;
    private CajaAhorroResponse resultado;
    private boolean metaAlcanzada;

    /**
     * @param alExito     con la caja devuelta (mostrarla ya en la lista)
     * @param alRefrescar tras mover (o no saber si se movió) plata, o un rechazo: saldos, cajas y campana
     */
    public OperacionMontoCaja(MovimientoCaja.Tipo tipo, CajaAhorroResponse caja, Supplier<ApiService> apiSinReintentos,
                              MovimientosCajaSesion sesion, Clock reloj, Consumer<CajaAhorroResponse> alExito,
                              Runnable alRefrescar) {
        this.tipo = tipo;
        this.caja = caja;
        this.apiSinReintentos = apiSinReintentos;
        this.sesion = sesion;
        this.reloj = reloj;
        this.alExito = alExito;
        this.alRefrescar = alRefrescar;
    }

    public void setObservador(Observador o) {
        observador = o;
    }

    private void notificar() {
        if (observador != null) observador.onCambio(this);
    }

    // ── Reglas puras ──────────────────────────────────────────────────────────

    /** null = OK. disponible: saldo principal (depósito) o de la caja (retiro); null = no cargado. */
    public static String validar(MovimientoCaja.Tipo tipo, String montoTexto, BigDecimal disponible) {
        BigDecimal monto = MontoInput.parsear(montoTexto);
        if (monto == null || monto.signum() <= 0) return MSG_MONTO_INVALIDO;
        BigDecimal limpio = monto.stripTrailingZeros();
        if (limpio.scale() > 2) return MSG_DECIMALES;
        if (limpio.precision() - limpio.scale() > Moneda.MAX_ENTEROS) return MSG_MONTO_INVALIDO;
        if (disponible == null) return MSG_SIN_SALDO;
        if (monto.compareTo(disponible) > 0) {
            return tipo == MovimientoCaja.Tipo.DEPOSITO ? MSG_INSUFICIENTE_DEPOSITO : MSG_INSUFICIENTE_RETIRO;
        }
        return null;
    }

    /** Mismo criterio que el backend para CAJA_AHORRO_META_ALCANZADA: antes < meta <= después. */
    public static boolean alcanzaMeta(CajaAhorroResponse antes, CajaAhorroResponse despues) {
        if (antes == null || despues == null || despues.getMontoObjetivo() == null
                || antes.getSaldo() == null || despues.getSaldo() == null) return false;
        BigDecimal meta = despues.getMontoObjetivo();
        return antes.getSaldo().compareTo(meta) < 0 && despues.getSaldo().compareTo(meta) >= 0;
    }

    // ── Formulario ────────────────────────────────────────────────────────────

    public boolean setMonto(String texto) {
        String t = texto != null ? texto : "";
        if (!MontoInput.esTipeoValido(t, Moneda.PESOS)) return false;
        montoTexto = t;
        if (paso == Paso.FORMULARIO) error = null;
        return true;
    }

    /** "Usar todo": el disponible exacto. */
    public void usarTodo(BigDecimal disponible) {
        if (disponible == null || paso != Paso.FORMULARIO || enviando) return;
        montoTexto = MontoInput.aTexto(disponible, Moneda.PESOS);
        error = null;
        notificar();
    }

    /** La lista trajo la caja más nueva (ej. otro depósito): se usa mientras no se esté operando. */
    public void setCaja(CajaAhorroResponse nueva) {
        if (nueva == null || enviando || paso != Paso.FORMULARIO) return;
        caja = nueva;
        notificar();
    }

    // ── El único lugar donde se mueve plata ──────────────────────────────────

    /** @param saldoPrincipal saldo en pesos de la cuenta (solo se usa al depositar); null = no cargado */
    public void confirmar(BigDecimal saldoPrincipal) {
        if (enviando || paso != Paso.FORMULARIO) return; // doble tap = 1 request
        error = validar(tipo, montoTexto, disponible(saldoPrincipal));
        if (error != null) {
            notificar();
            return;
        }
        enviando = true;
        montoEnviado = MontoInput.parsear(montoTexto);
        final CajaAhorroResponse antes = caja;
        notificar();
        MontoCajaAhorroRequest body = new MontoCajaAhorroRequest(montoEnviado);
        ApiService s = apiSinReintentos.get();
        Call<CajaAhorroResponse> call = tipo == MovimientoCaja.Tipo.DEPOSITO
                ? s.depositarEnCaja(antes.getId(), body) : s.retirarDeCaja(antes.getId(), body);
        EnvioSinReintento.enviar(call, true, new EnvioSinReintento.Manejador<CajaAhorroResponse>() {
            @Override
            public void exito(CajaAhorroResponse nueva) {
                enviando = false;
                resultado = nueva;
                metaAlcanzada = tipo == MovimientoCaja.Tipo.DEPOSITO && alcanzaMeta(antes, nueva);
                paso = Paso.EXITO;
                sesion.registrar(MovimientoCaja.de(tipo, nueva, montoEnviado, reloj));
                alExito.accept(nueva);
                alRefrescar.run();
                notificar();
            }

            @Override
            public void rechazo(String mensaje, int codigo) {
                enviando = false;
                error = EnvioSinReintento.textoRechazo(mensaje, MSG_NO_SE_PUDO);
                alRefrescar.run(); // el saldo que se mostraba pudo estar viejo
                notificar();
            }

            @Override
            public void noEnviado(String mensaje) {
                enviando = false;
                error = mensaje;
                notificar();
            }

            @Override
            public void incierto() {
                enviando = false;
                paso = Paso.INCIERTO;
                error = MSG_INCIERTO;
                alRefrescar.run();
                notificar();
            }
        });
    }

    // ── Estado para la UI ─────────────────────────────────────────────────────

    public MovimientoCaja.Tipo getTipo() { return tipo; }
    public boolean esDeposito() { return tipo == MovimientoCaja.Tipo.DEPOSITO; }
    public CajaAhorroResponse getCaja() { return caja; }
    public String getMontoTexto() { return montoTexto; }
    public BigDecimal getMonto() { return MontoInput.parsear(montoTexto); }
    public Paso getPaso() { return paso; }
    public String getError() { return error; }
    public boolean isEnviando() { return enviando; }
    public BigDecimal getMontoEnviado() { return montoEnviado; }
    public CajaAhorroResponse getResultado() { return resultado; }
    /** El depósito recién hecho cruzó la meta (el backend manda además "Meta alcanzada"). */
    public boolean isMetaAlcanzada() { return metaAlcanzada; }

    /** Contra qué se valida y qué pone "Usar todo". */
    public BigDecimal disponible(BigDecimal saldoPrincipal) {
        return tipo == MovimientoCaja.Tipo.DEPOSITO ? saldoPrincipal : caja.getSaldo();
    }

    /** Un pedido en vuelo no sobrevive a la muerte del proceso: al volver, INCIERTO. */
    public void restaurarIncierto() {
        paso = Paso.INCIERTO;
        error = MSG_INCIERTO;
        notificar();
    }
}
