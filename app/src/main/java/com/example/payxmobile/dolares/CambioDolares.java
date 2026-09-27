package com.example.payxmobile.dolares;

import com.example.payxmobile.model.CotizacionDolar;
import com.example.payxmobile.model.CrearCambioDolaresRequest;
import com.example.payxmobile.model.OperacionCambioResponse;
import com.example.payxmobile.network.ApiErrores;
import com.example.payxmobile.network.ApiService;
import com.example.payxmobile.transferencias.FalloEnvio;
import com.example.payxmobile.transferencias.Moneda;
import com.example.payxmobile.transferencias.MontoInput;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.function.Supplier;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Compra o venta de dólares al instante: FORMULARIO -> CONFIRMAR -> EXITO. Nada se mueve hasta
 * que el usuario confirma el resumen (a diferencia del modal de la web, que opera de una).
 *
 * - COMPRA: el monto son PESOS y se usa el precio "venta" de la cotización (lo que paga el usuario).
 * - VENTA: el monto son DÓLARES y se usa el precio "compra" (lo que recibe).
 *   Son los precios del lado de la casa de cambio; ver {@link #precioPara}.
 * - El "Recibís" es solo un preview con la cotización mostrada; lo real es lo que devuelve el POST.
 * - El POST NO tiene idempotencia en el backend: un solo pedido en vuelo, por el cliente sin
 *   reintentos, y si no llega respuesta se pasa a INCIERTO (no se reintenta solo).
 * Sin Android: la Activity lo guarda en un ViewModel para que sobreviva a la rotación.
 */
public class CambioDolares {

    public enum Tipo { COMPRA, VENTA }

    public enum Paso { FORMULARIO, CONFIRMAR, EXITO, INCIERTO }

    public static final String MSG_MONTO_INVALIDO = "Ingresá un monto válido.";
    public static final String MSG_DECIMALES = "El monto puede tener como máximo 2 decimales.";
    public static final String MSG_SIN_COTIZACION = "Todavía no tenemos la cotización disponible.";
    public static final String MSG_ERROR_COTIZACION = "No pudimos obtener la cotización del dólar. Probá de nuevo en un momento.";
    public static final String MSG_SIN_SALDO = "Todavía no pudimos cargar tu saldo. Probá de nuevo en unos segundos.";
    public static final String MSG_INSUFICIENTE_COMPRA = "No tenés saldo en pesos suficiente para esta compra.";
    public static final String MSG_INSUFICIENTE_VENTA = "No tenés dólares suficientes para esta venta.";
    public static final String MSG_NO_SE_PUDO = "No se pudo realizar la operación. Intenta de nuevo.";
    public static final String MSG_INCIERTO =
            "No pudimos confirmar si la operación se realizó. Revisá tus saldos antes de intentar de nuevo";

    public interface Observador {
        void onCambio(CambioDolares cambio);
    }

    private final Tipo tipo;
    private final Supplier<ApiService> api;
    private final Supplier<ApiService> apiSinReintentos;
    private final Runnable alPosiblementeMoverPlata;
    private Observador observador;

    private String montoTexto = "";
    private CotizacionDolar cotizacion;
    private String errorCotizacion;
    private boolean cotizacionEnVuelo;

    private Paso paso = Paso.FORMULARIO;
    private BigDecimal montoAConfirmar;
    private OperacionCambioResponse resultado;
    private String error;
    private boolean enviando;

    /**
     * @param alPosiblementeMoverPlata se llama tras operar (o si no se sabe si se operó):
     *                                 refrescar saldos y notificaciones.
     */
    public CambioDolares(Tipo tipo, Supplier<ApiService> api, Supplier<ApiService> apiSinReintentos,
                         Runnable alPosiblementeMoverPlata) {
        this.tipo = tipo;
        this.api = api;
        this.apiSinReintentos = apiSinReintentos;
        this.alPosiblementeMoverPlata = alPosiblementeMoverPlata;
    }

    public void setObservador(Observador o) {
        observador = o;
    }

    private void notificar() {
        if (observador != null) observador.onCambio(this);
    }

    // ── Reglas puras (testeables sin red) ─────────────────────────────────────

    /** Moneda que ENTREGA el usuario (la del input y la del saldo a controlar). */
    public static Moneda monedaEntrada(Tipo tipo) {
        return tipo == Tipo.COMPRA ? Moneda.PESOS : Moneda.USD;
    }

    /**
     * Precio aplicado: COMPRA usa "venta" (PayX le vende dólares al usuario), VENTA usa "compra"
     * (PayX se los compra). Es fácil confundirlos: está cubierto por tests.
     * null si no hay cotización o el precio no es positivo.
     */
    public static BigDecimal precioPara(Tipo tipo, CotizacionDolar c) {
        if (c == null) return null;
        BigDecimal precio = tipo == Tipo.COMPRA ? c.getVenta() : c.getCompra();
        return precio != null && precio.signum() > 0 ? precio : null;
    }

    /**
     * Preview de lo que se recibe, con el mismo redondeo que el backend (2 decimales HALF_UP):
     * COMPRA -> US$ = pesos / venta; VENTA -> $ = dólares × compra. null si falta el monto o el precio.
     */
    public static BigDecimal preview(Tipo tipo, BigDecimal monto, BigDecimal precio) {
        if (monto == null || precio == null || monto.signum() <= 0 || precio.signum() <= 0) return null;
        return tipo == Tipo.COMPRA
                ? monto.divide(precio, 2, RoundingMode.HALF_UP)
                : monto.multiply(precio).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Validación local ANTES de mandar nada. null = OK. El "monto muy bajo" NO se valida acá: lo
     * decide el backend con la cotización real y su mensaje se muestra tal cual.
     */
    public static String validar(Tipo tipo, String montoTexto, BigDecimal saldoDisponible, BigDecimal precio) {
        if (precio == null) return MSG_SIN_COTIZACION;
        BigDecimal monto = MontoInput.parsear(montoTexto);
        if (monto == null || monto.signum() <= 0) return MSG_MONTO_INVALIDO;
        BigDecimal limpio = monto.stripTrailingZeros();
        if (limpio.scale() > 2) return MSG_DECIMALES;
        if (limpio.precision() - limpio.scale() > Moneda.MAX_ENTEROS) return MSG_MONTO_INVALIDO;
        if (saldoDisponible == null) return MSG_SIN_SALDO;
        if (monto.compareTo(saldoDisponible) > 0) {
            return tipo == Tipo.COMPRA ? MSG_INSUFICIENTE_COMPRA : MSG_INSUFICIENTE_VENTA;
        }
        return null;
    }

    // ── Formulario ────────────────────────────────────────────────────────────

    /** Devuelve false (y no cambia nada) si el texto no se puede tipear (más de 2 decimales, etc.). */
    public boolean setMonto(String texto) {
        String t = texto != null ? texto : "";
        if (!MontoInput.esTipeoValido(t, getMonedaEntrada())) return false;
        montoTexto = t;
        if (paso == Paso.FORMULARIO) error = null;
        return true;
    }

    /** "Usar todo": el saldo exacto de la moneda que se entrega. */
    public void usarTodo(BigDecimal saldo) {
        if (saldo == null || paso != Paso.FORMULARIO) return;
        montoTexto = MontoInput.aTexto(saldo, getMonedaEntrada());
        error = null;
        notificar();
    }

    // ── Cotización ────────────────────────────────────────────────────────────

    /**
     * GET /api/cotizacion/dolar, sin solapar pedidos. 503 = el backend no tiene ninguna cotización:
     * se descarta la anterior (sin preview, nunca un 0). Cualquier otro fallo (sin conexión, 429)
     * mantiene la última buena.
     */
    public void cargarCotizacion() {
        synchronized (this) {
            if (cotizacionEnVuelo) return;
            cotizacionEnVuelo = true;
        }
        api.get().obtenerCotizacionDolar().enqueue(new Callback<CotizacionDolar>() {
            @Override
            public void onResponse(Call<CotizacionDolar> call, Response<CotizacionDolar> response) {
                synchronized (CambioDolares.this) {
                    cotizacionEnVuelo = false;
                    CotizacionDolar c = response.body();
                    if (response.isSuccessful() && c != null && precioPara(Tipo.COMPRA, c) != null
                            && precioPara(Tipo.VENTA, c) != null) {
                        cotizacion = c;
                        errorCotizacion = null;
                    } else if (response.code() == 503) {
                        cotizacion = null;
                        errorCotizacion = MSG_ERROR_COTIZACION;
                    } else if (cotizacion == null) {
                        errorCotizacion = MSG_ERROR_COTIZACION;
                    }
                }
                notificar();
            }

            @Override
            public void onFailure(Call<CotizacionDolar> call, Throwable t) {
                synchronized (CambioDolares.this) {
                    cotizacionEnVuelo = false;
                    if (cotizacion == null) errorCotizacion = MSG_ERROR_COTIZACION;
                }
                notificar();
            }
        });
    }

    /**
     * Cotización ya conocida (precargada por Inicio/Inversiones) para mostrar el precio al instante.
     * Solo se usa si todavía no llegó ninguna; el GET de la pantalla la reemplaza enseguida.
     */
    public synchronized void usarCotizacionConocida(CotizacionDolar c) {
        if (cotizacion == null && c != null && precioPara(Tipo.COMPRA, c) != null && precioPara(Tipo.VENTA, c) != null) {
            cotizacion = c;
            errorCotizacion = null;
        }
    }

    // ── Paso 1 -> 2: validar y pedir confirmación (NO mueve plata) ────────────

    /** "Comprar/Vender dólares": valida localmente y, si pasa, muestra el resumen a confirmar. */
    public void continuar(BigDecimal saldoDisponible) {
        if (enviando || paso != Paso.FORMULARIO) return;
        error = validar(tipo, montoTexto, saldoDisponible, getPrecioAplicado());
        if (error == null) {
            montoAConfirmar = MontoInput.parsear(montoTexto);
            paso = Paso.CONFIRMAR;
        }
        notificar();
    }

    /** "Modificar": vuelve al formulario conservando el monto. */
    public void editar() {
        if (enviando || paso != Paso.CONFIRMAR) return;
        paso = Paso.FORMULARIO;
        error = null;
        notificar();
    }

    // ── Paso 2 -> 3: el ÚNICO lugar donde se mueve plata ─────────────────────

    public void confirmar() {
        if (enviando || paso != Paso.CONFIRMAR || montoAConfirmar == null) return; // doble tap = 1 request
        if (getPrecioAplicado() == null) {
            error = MSG_SIN_COTIZACION;
            notificar();
            return;
        }
        enviando = true;
        error = null;
        notificar();
        CrearCambioDolaresRequest body = new CrearCambioDolaresRequest(tipo.name(), montoAConfirmar);
        apiSinReintentos.get().crearCambioDolares(body).enqueue(new Callback<OperacionCambioResponse>() {
            @Override
            public void onResponse(Call<OperacionCambioResponse> call, Response<OperacionCambioResponse> response) {
                enviando = false;
                if (response.isSuccessful() && response.body() != null) {
                    resultado = response.body();
                    paso = Paso.EXITO;
                    alPosiblementeMoverPlata.run();
                } else if (response.isSuccessful()) {
                    // 2xx sin body legible: se hizo, pero no sabemos con qué montos
                    paso = Paso.INCIERTO;
                    error = MSG_INCIERTO;
                    alPosiblementeMoverPlata.run();
                } else {
                    // Rechazo explícito del backend: no se movió nada; su texto se muestra tal cual
                    error = mensajeError(response);
                }
                notificar();
            }

            @Override
            public void onFailure(Call<OperacionCambioResponse> call, Throwable t) {
                enviando = false;
                if (FalloEnvio.seguroNoEnviado(t)) {
                    error = ApiErrores.mensajeFallo(t);
                } else {
                    // Timeout / corte después de enviar / respuesta ilegible: pudo haberse hecho.
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
        // Sin {"error"} en el body (ej. 403 vacío por validación): el genérico de la web
        boolean generico = mensaje.equals(ApiErrores.MSG_DATOS_INVALIDOS)
                || mensaje.equals(ApiErrores.MSG_RESPUESTA_INESPERADA);
        return generico ? MSG_NO_SE_PUDO : mensaje;
    }

    // ── Estado para la UI ─────────────────────────────────────────────────────

    public Tipo getTipo() { return tipo; }
    public boolean esCompra() { return tipo == Tipo.COMPRA; }
    public Moneda getMonedaEntrada() { return monedaEntrada(tipo); }
    public String getMontoTexto() { return montoTexto; }
    public synchronized CotizacionDolar getCotizacion() { return cotizacion; }
    public synchronized String getErrorCotizacion() { return errorCotizacion; }
    public synchronized boolean isCargandoCotizacion() { return cotizacionEnVuelo && cotizacion == null; }
    public Paso getPaso() { return paso; }
    public OperacionCambioResponse getResultado() { return resultado; }
    public String getError() { return error; }
    public boolean isEnviando() { return enviando; }
    /** Monto congelado al pasar a CONFIRMAR (el que se manda). */
    public BigDecimal getMontoAConfirmar() { return montoAConfirmar; }

    /** Monto tipeado (null si no es un número). */
    public BigDecimal getMonto() { return MontoInput.parsear(montoTexto); }

    public synchronized BigDecimal getPrecioAplicado() { return precioPara(tipo, cotizacion); }

    /** "Recibís ..." con la cotización mostrada; null = no mostrar (sin monto o sin cotización). */
    public BigDecimal getPreview() { return preview(tipo, getMonto(), getPrecioAplicado()); }

    /** Saldo de la moneda entregada luego de operar (puede quedar negativo: se muestra en rojo). */
    public BigDecimal saldoLuego(BigDecimal saldo) {
        if (saldo == null) return null;
        BigDecimal monto = getMonto();
        return monto == null ? saldo : saldo.subtract(monto);
    }

    // ── Guardado ante muerte del proceso ───────────────────────────────────────

    /** Restaura un paso guardado. Una request en vuelo no sobrevive a la muerte del proceso. */
    public void restaurar(Paso paso, OperacionCambioResponse resultado) {
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
