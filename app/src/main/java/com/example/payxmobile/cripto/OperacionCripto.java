package com.example.payxmobile.cripto;

import com.example.payxmobile.model.CotizacionCripto;
import com.example.payxmobile.model.CrearOperacionCriptoRequest;
import com.example.payxmobile.model.OperacionCriptoResponse;
import com.example.payxmobile.model.PerfilResponse;
import com.example.payxmobile.network.ApiErrores;
import com.example.payxmobile.network.ApiService;
import com.example.payxmobile.transferencias.FalloEnvio;
import com.example.payxmobile.transferencias.Moneda;
import com.example.payxmobile.transferencias.MontoInput;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.Map;
import java.util.function.Supplier;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Compra o venta de BTC/ETH/SOL/USDT/BNB/XRP: FORMULARIO -> CONFIRMAR -> EXITO (nada se mueve
 * hasta confirmar el resumen, como en dólares).
 *
 * - COMPRA: el monto son PESOS (2 decimales) y se recibe cripto; VENTA: el monto es CANTIDAD de
 *   esa cripto (8 decimales) y se reciben pesos. Un solo precio por cripto (sin spread).
 * - El preview redondea igual que el backend: HACIA ABAJO (DOWN), nunca HALF_UP.
 * - Las cotizaciones NO se piden acá: vienen de SaldosRepository (las mismas del ticker de Inicio).
 * - El POST NO tiene idempotencia: un solo pedido en vuelo, sin reintentos, INCIERTO si no hay respuesta.
 */
public class OperacionCripto {

    public enum Tipo { COMPRA, VENTA }

    public enum Paso { FORMULARIO, CONFIRMAR, EXITO, INCIERTO }

    public static final String MSG_MONTO_INVALIDO = "Ingresá un monto válido.";
    public static final String MSG_SIN_COTIZACION = "Todavía no tenemos la cotización disponible.";
    public static final String MSG_SIN_SALDO = "Todavía no pudimos cargar tu saldo. Probá de nuevo en unos segundos.";
    public static final String MSG_INSUFICIENTE_COMPRA = "No tenés saldo en pesos suficiente para esta compra.";
    public static final String MSG_NO_SE_PUDO = "No se pudo realizar la operación. Intenta de nuevo.";
    public static final String MSG_INCIERTO =
            "No pudimos confirmar si la operación se realizó. Revisá tus saldos antes de intentar de nuevo";

    public static String msgDecimales(int decimales) {
        return "El monto puede tener como máximo " + decimales + " decimales.";
    }

    public static String msgInsuficienteVenta(Moneda cripto) {
        return "No tenés suficiente " + cripto.codigo() + " para esta venta.";
    }

    public interface Observador {
        void onCambio(OperacionCripto op);
    }

    private final Tipo tipo;
    private final Supplier<ApiService> apiSinReintentos;
    private final Runnable alPosiblementeMoverPlata;
    private Observador observador;

    private Moneda cripto = Moneda.BTC;
    private String montoTexto = "";
    private Map<String, CotizacionCripto> cotizaciones = Collections.emptyMap();
    private boolean sinCotizacionesEnBackend;

    private Paso paso = Paso.FORMULARIO;
    private BigDecimal montoAConfirmar;
    private OperacionCriptoResponse resultado;
    private String error;
    private boolean enviando;

    /** @param alPosiblementeMoverPlata tras operar (o si no se sabe): refrescar saldos, feed y notificaciones. */
    public OperacionCripto(Tipo tipo, Supplier<ApiService> apiSinReintentos, Runnable alPosiblementeMoverPlata) {
        this.tipo = tipo;
        this.apiSinReintentos = apiSinReintentos;
        this.alPosiblementeMoverPlata = alPosiblementeMoverPlata;
    }

    public void setObservador(Observador o) {
        observador = o;
    }

    private void notificar() {
        if (observador != null) observador.onCambio(this);
    }

    // ── Reglas puras ──────────────────────────────────────────────────────────

    /** Lo que ENTREGA el usuario: pesos al comprar, la cripto elegida al vender. */
    public static Moneda monedaEntrada(Tipo tipo, Moneda cripto) {
        return tipo == Tipo.COMPRA ? Moneda.PESOS : cripto;
    }

    /** Saldo de lo que se entrega, de ESTA cripto puntual (SOL sale de "saldoSolana"). null sin perfil. */
    public static BigDecimal saldoDisponible(Tipo tipo, Moneda cripto, PerfilResponse perfil) {
        if (perfil == null) return null;
        return monedaEntrada(tipo, cripto).saldoEn(perfil);
    }

    /** Precio en pesos de 1 unidad, o null si no hay (o no es positivo). */
    public static BigDecimal precioDe(Moneda cripto, Map<String, CotizacionCripto> cotizaciones) {
        CotizacionCripto c = cotizaciones != null ? cotizaciones.get(cripto.codigo()) : null;
        BigDecimal p = c != null ? c.getPrecio() : null;
        return p != null && p.signum() > 0 ? p : null;
    }

    /**
     * Preview con el MISMO redondeo del backend (DOWN): COMPRA -> cripto = pesos / precio (8 dec.);
     * VENTA -> pesos = cantidad × precio (2 dec.). null si falta el monto o el precio.
     */
    public static BigDecimal preview(Tipo tipo, BigDecimal monto, BigDecimal precio) {
        if (monto == null || precio == null || monto.signum() <= 0 || precio.signum() <= 0) return null;
        return tipo == Tipo.COMPRA
                ? monto.divide(precio, 8, RoundingMode.DOWN)
                : monto.multiply(precio).setScale(2, RoundingMode.DOWN);
    }

    /** Validación local ANTES de mandar nada (null = OK). "Monto muy bajo" lo decide el backend. */
    public static String validar(Tipo tipo, Moneda cripto, String montoTexto, BigDecimal saldo, BigDecimal precio) {
        if (precio == null) return MSG_SIN_COTIZACION;
        BigDecimal monto = MontoInput.parsear(montoTexto);
        if (monto == null || monto.signum() <= 0) return MSG_MONTO_INVALIDO;
        Moneda entrada = monedaEntrada(tipo, cripto);
        BigDecimal limpio = monto.stripTrailingZeros();
        if (limpio.scale() > entrada.decimales) return msgDecimales(entrada.decimales);
        if (limpio.precision() - limpio.scale() > Moneda.MAX_ENTEROS) return MSG_MONTO_INVALIDO;
        if (saldo == null) return MSG_SIN_SALDO;
        if (monto.compareTo(saldo) > 0) {
            return tipo == Tipo.COMPRA ? MSG_INSUFICIENTE_COMPRA : msgInsuficienteVenta(cripto);
        }
        return null;
    }

    // ── Formulario ────────────────────────────────────────────────────────────

    /** Cambiar de cripto limpia el monto y el error (como la web). */
    public void setCripto(Moneda nueva) {
        if (nueva == null || !nueva.esCripto() || nueva == cripto || paso != Paso.FORMULARIO) return;
        cripto = nueva;
        montoTexto = "";
        error = null;
        notificar();
    }

    /** false (sin cambios) si el texto no se puede tipear: 2 decimales en pesos, 8 en cripto, 13 enteros. */
    public boolean setMonto(String texto) {
        String t = texto != null ? texto : "";
        if (!MontoInput.esTipeoValido(t, getMonedaEntrada())) return false;
        montoTexto = t;
        if (paso == Paso.FORMULARIO) error = null;
        return true;
    }

    /** "Usar todo": el saldo EXACTO (8 decimales en cripto, sin redondear ni rellenar). */
    public void usarTodo(BigDecimal saldo) {
        if (saldo == null || paso != Paso.FORMULARIO) return;
        montoTexto = MontoInput.aTexto(saldo, getMonedaEntrada());
        error = null;
        notificar();
    }

    /** Cotizaciones de SaldosRepository. {@code sinCotizaciones} = el backend respondió 503. */
    public synchronized void setCotizaciones(Map<String, CotizacionCripto> mapa, boolean sinCotizaciones) {
        cotizaciones = mapa != null ? mapa : Collections.emptyMap();
        sinCotizacionesEnBackend = sinCotizaciones;
    }

    // ── Paso 1 -> 2 (no mueve plata) ─────────────────────────────────────────

    public void continuar(BigDecimal saldoDisponible) {
        if (enviando || paso != Paso.FORMULARIO) return;
        error = validar(tipo, cripto, montoTexto, saldoDisponible, getPrecio());
        if (error == null) {
            montoAConfirmar = MontoInput.parsear(montoTexto);
            paso = Paso.CONFIRMAR;
        }
        notificar();
    }

    public void editar() {
        if (enviando || paso != Paso.CONFIRMAR) return;
        paso = Paso.FORMULARIO;
        error = null;
        notificar();
    }

    // ── Paso 2 -> 3: el ÚNICO lugar donde se mueve plata ─────────────────────

    public void confirmar() {
        if (enviando || paso != Paso.CONFIRMAR || montoAConfirmar == null) return; // doble tap = 1 request
        if (getPrecio() == null) {
            error = MSG_SIN_COTIZACION;
            notificar();
            return;
        }
        enviando = true;
        error = null;
        notificar();
        CrearOperacionCriptoRequest body = new CrearOperacionCriptoRequest(tipo.name(), cripto.codigo(), montoAConfirmar);
        apiSinReintentos.get().crearOperacionCripto(body).enqueue(new Callback<OperacionCriptoResponse>() {
            @Override
            public void onResponse(Call<OperacionCriptoResponse> call, Response<OperacionCriptoResponse> response) {
                enviando = false;
                if (response.isSuccessful() && response.body() != null) {
                    resultado = response.body();
                    paso = Paso.EXITO;
                    alPosiblementeMoverPlata.run();
                } else if (response.isSuccessful()) {
                    paso = Paso.INCIERTO;
                    error = MSG_INCIERTO;
                    alPosiblementeMoverPlata.run();
                } else {
                    // Rechazo explícito (incluida "cotización desactualizada para operar"): texto tal cual
                    error = mensajeError(response);
                }
                notificar();
            }

            @Override
            public void onFailure(Call<OperacionCriptoResponse> call, Throwable t) {
                enviando = false;
                if (FalloEnvio.seguroNoEnviado(t)) {
                    error = ApiErrores.mensajeFallo(t);
                } else {
                    paso = Paso.INCIERTO;
                    error = MSG_INCIERTO;
                    alPosiblementeMoverPlata.run();
                }
                notificar();
            }
        });
    }

    private static String mensajeError(Response<?> response) {
        String mensaje = ApiErrores.mensaje(response);
        // Sin {"error"} (ej. 403 vacío: símbolo o tipo inválidos para el @Valid): genérico de la web
        boolean generico = mensaje.equals(ApiErrores.MSG_DATOS_INVALIDOS)
                || mensaje.equals(ApiErrores.MSG_RESPUESTA_INESPERADA);
        return generico ? MSG_NO_SE_PUDO : mensaje;
    }

    // ── Estado para la UI ─────────────────────────────────────────────────────

    public Tipo getTipo() { return tipo; }
    public boolean esCompra() { return tipo == Tipo.COMPRA; }
    public Moneda getCripto() { return cripto; }
    public Moneda getMonedaEntrada() { return monedaEntrada(tipo, cripto); }
    public String getMontoTexto() { return montoTexto; }
    public BigDecimal getMonto() { return MontoInput.parsear(montoTexto); }
    public BigDecimal getMontoAConfirmar() { return montoAConfirmar; }
    public Paso getPaso() { return paso; }
    public OperacionCriptoResponse getResultado() { return resultado; }
    public String getError() { return error; }
    public boolean isEnviando() { return enviando; }

    /** Precio de la cripto elegida; null si no hay o si el backend dijo que no tiene ninguna (503). */
    public synchronized BigDecimal getPrecio() {
        return sinCotizacionesEnBackend ? null : precioDe(cripto, cotizaciones);
    }

    /** true si el precio mostrado es el último conocido (el backend puede rechazar operar con él). */
    public synchronized boolean isPrecioDesactualizado() {
        CotizacionCripto c = cotizaciones.get(cripto.codigo());
        return c != null && c.isDesactualizada();
    }

    public BigDecimal getPreview() { return preview(tipo, getMonto(), getPrecio()); }

    public BigDecimal getPreviewConfirmar() { return preview(tipo, montoAConfirmar, getPrecio()); }

    // ── Guardado ante muerte del proceso ───────────────────────────────────────

    public void restaurar(Moneda criptoGuardada, Paso paso, OperacionCriptoResponse resultado) {
        if (criptoGuardada != null && criptoGuardada.esCripto()) cripto = criptoGuardada;
        if (paso == Paso.EXITO && resultado != null) {
            this.paso = Paso.EXITO;
            this.resultado = resultado;
        } else if (paso == Paso.CONFIRMAR && getMonto() != null && getMonto().signum() > 0) {
            this.paso = Paso.CONFIRMAR;
            this.montoAConfirmar = getMonto();
        } else if (paso == Paso.INCIERTO) {
            this.paso = Paso.INCIERTO;
            this.error = MSG_INCIERTO;
        }
        notificar();
    }
}
