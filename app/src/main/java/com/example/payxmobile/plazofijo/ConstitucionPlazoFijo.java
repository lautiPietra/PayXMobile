package com.example.payxmobile.plazofijo;

import com.example.payxmobile.model.CrearPlazoFijoRequest;
import com.example.payxmobile.model.PlazoFijoResponse;
import com.example.payxmobile.model.TasaPlazoFijo;
import com.example.payxmobile.model.TasasPlazoFijoResponse;
import com.example.payxmobile.network.ApiErrores;
import com.example.payxmobile.network.ApiService;
import com.example.payxmobile.transferencias.FalloEnvio;
import com.example.payxmobile.transferencias.Moneda;
import com.example.payxmobile.transferencias.MontoInput;
import com.example.payxmobile.transferencias.ValidadorMonto;
import com.example.payxmobile.utils.MontoFormatter;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Supplier;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Constituir un plazo fijo: FORMULARIO -> CONFIRMAR -> EXITO. Nada se mueve hasta que el usuario
 * confirma el resumen (a diferencia del modal de la web, que constituye de una).
 *
 * - Plazos, TNA, monto mínimo y máximo de activos vienen SIEMPRE de GET /tasas (los configura el
 *   admin): sin esa respuesta no hay formulario. Solo se puede elegir un "dias" de esa lista.
 * - El interés es un preview con la fórmula del backend ({@link CalculoPlazoFijo}); lo real es lo
 *   que devuelve el 201.
 * - El POST NO tiene idempotencia en el backend: un solo pedido en vuelo, por el cliente sin
 *   reintentos, y si no llega respuesta se pasa a INCIERTO (no se reintenta solo).
 * Sin Android: la Activity lo guarda en un ViewModel para que sobreviva a la rotación.
 */
public class ConstitucionPlazoFijo {

    public enum Paso { FORMULARIO, CONFIRMAR, EXITO, INCIERTO }

    public static final String MSG_PLAZO_INVALIDO = "Elegí un plazo válido.";
    public static final String MSG_MONTO_INVALIDO = ValidadorMonto.MSG_MONTO_INVALIDO;
    public static final String MSG_DECIMALES = ValidadorMonto.msgDecimales(2);
    public static final String MSG_SIN_TASAS = "Todavía no tenemos las tasas disponibles.";
    public static final String MSG_ERROR_TASAS = "No pudimos cargar las tasas disponibles. Probá de nuevo en un momento.";
    public static final String MSG_SIN_SALDO = "Todavía no pudimos cargar tu saldo. Probá de nuevo en unos segundos.";
    public static final String MSG_INSUFICIENTE = "No tenés saldo suficiente para esta inversión.";
    public static final String MSG_NO_SE_PUDO = "No se pudo constituir el plazo fijo. Intenta de nuevo.";
    public static final String MSG_INCIERTO =
            "No pudimos confirmar si el plazo fijo se constituyó. Revisá tus plazos fijos antes de intentar de nuevo";

    public interface Observador {
        void onCambio(ConstitucionPlazoFijo constitucion);
    }

    private final Supplier<ApiService> api;
    private final Supplier<ApiService> apiSinReintentos;
    private final Runnable alPosiblementeMoverPlata;
    private final Runnable alRechazar;
    private Observador observador;

    private TasasPlazoFijoResponse tasas;
    private String errorTasas;
    private boolean tasasEnVuelo;
    /** Plazos fijos ACTIVOS del usuario (de la lista); null = todavía no se sabe. */
    private Integer activos;

    private String montoTexto = "";
    private Integer plazoDias;

    private Paso paso = Paso.FORMULARIO;
    private BigDecimal montoAConfirmar;
    private int diasAConfirmar;
    private PlazoFijoResponse resultado;
    private String error;
    private boolean enviando;

    /**
     * @param alPosiblementeMoverPlata tras constituir (o si no se sabe si se constituyó): refrescar
     *                                 saldos, plazos fijos y notificaciones.
     * @param alRechazar               tras un 400: refrescar la lista de plazos fijos (el tope de
     *                                 activos pudo cambiar). Las tasas se vuelven a pedir solas.
     */
    public ConstitucionPlazoFijo(Supplier<ApiService> api, Supplier<ApiService> apiSinReintentos,
                                 Runnable alPosiblementeMoverPlata, Runnable alRechazar) {
        this.api = api;
        this.apiSinReintentos = apiSinReintentos;
        this.alPosiblementeMoverPlata = alPosiblementeMoverPlata;
        this.alRechazar = alRechazar;
    }

    public void setObservador(Observador o) {
        observador = o;
    }

    private void notificar() {
        if (observador != null) observador.onCambio(this);
    }

    // ── Reglas puras (testeables sin red) ─────────────────────────────────────

    /** La tasa de ESE plazo, solo si llegó en /tasas (null si no existe: no se puede elegir). */
    public static TasaPlazoFijo tasaPara(TasasPlazoFijoResponse t, Integer dias) {
        if (t == null || t.getTasas() == null || dias == null) return null;
        for (TasaPlazoFijo tasa : t.getTasas()) {
            if (tasa != null && tasa.getDias() == dias) return tasa;
        }
        return null;
    }

    /** ¿Una respuesta de /tasas con la que se puede armar el formulario? */
    static boolean tasasValidas(TasasPlazoFijoResponse t) {
        if (t == null || t.getTasas() == null || t.getTasas().isEmpty()
                || t.getMontoMinimo() == null || t.getMaxActivos() == null) return false;
        for (TasaPlazoFijo tasa : t.getTasas()) {
            if (tasa == null || tasa.getDias() <= 0 || tasa.getTna() == null) return false;
        }
        return true;
    }

    /** Ya tiene el máximo de activos: no se puede constituir otro. false si todavía no se sabe. */
    public static boolean limiteAlcanzado(TasasPlazoFijoResponse t, Integer activos) {
        return t != null && t.getMaxActivos() != null && activos != null && activos >= t.getMaxActivos();
    }

    public static String msgLimite(int maxActivos) {
        return "Ya tenés el máximo de " + maxActivos + " plazos fijos activos";
    }

    public static String msgMinimo(BigDecimal montoMinimo) {
        return "El monto mínimo para constituir un plazo fijo es $ " + MontoFormatter.fiat(montoMinimo) + ".";
    }

    /**
     * Validación local ANTES de mandar nada, en el orden de la web. null = OK. El backend vuelve a
     * validar todo (sus textos se muestran tal cual si rechaza).
     */
    public static String validar(TasasPlazoFijoResponse t, Integer dias, String montoTexto,
                                 BigDecimal saldoPesos, Integer activos) {
        if (!tasasValidas(t)) return MSG_SIN_TASAS;
        if (tasaPara(t, dias) == null) return MSG_PLAZO_INVALIDO;
        if (limiteAlcanzado(t, activos)) return msgLimite(t.getMaxActivos());
        String errorMonto = ValidadorMonto.validar(montoTexto, Moneda.PESOS.decimales);
        if (errorMonto != null) return errorMonto;
        BigDecimal monto = MontoInput.parsear(montoTexto);
        if (monto.compareTo(t.getMontoMinimo()) < 0) return msgMinimo(t.getMontoMinimo());
        if (saldoPesos == null) return MSG_SIN_SALDO;
        if (monto.compareTo(saldoPesos) > 0) return MSG_INSUFICIENTE;
        return null;
    }

    // ── Tasas ─────────────────────────────────────────────────────────────────

    /**
     * GET /tasas, sin solapar pedidos. Si llega algo válido reemplaza lo anterior (el admin pudo
     * cambiar tasas, mínimo o máximo). Si falla y nunca hubo tasas: error con "Reintentar".
     */
    public void cargarTasas() {
        synchronized (this) {
            if (tasasEnVuelo) return;
            tasasEnVuelo = true;
        }
        notificar();
        api.get().obtenerTasasPlazoFijo().enqueue(new Callback<TasasPlazoFijoResponse>() {
            @Override
            public void onResponse(Call<TasasPlazoFijoResponse> call, Response<TasasPlazoFijoResponse> response) {
                synchronized (ConstitucionPlazoFijo.this) {
                    tasasEnVuelo = false;
                    TasasPlazoFijoResponse t = response.body();
                    if (response.isSuccessful() && tasasValidas(t)) {
                        usarTasas(t);
                    } else if (tasas == null) {
                        errorTasas = MSG_ERROR_TASAS;
                    }
                }
                notificar();
            }

            @Override
            public void onFailure(Call<TasasPlazoFijoResponse> call, Throwable t) {
                synchronized (ConstitucionPlazoFijo.this) {
                    tasasEnVuelo = false;
                    if (tasas == null) errorTasas = MSG_ERROR_TASAS;
                }
                notificar();
            }
        });
    }

    private void usarTasas(TasasPlazoFijoResponse t) {
        tasas = t;
        errorTasas = null;
        // Por defecto el primer plazo (como la web); si el elegido ya no se ofrece, también
        if (tasaPara(t, plazoDias) == null) plazoDias = t.getTasas().get(0).getDias();
    }

    /** Cuántos ACTIVOS tiene (de la lista de plazos fijos); null = todavía no se cargó. */
    public void setActivos(Integer activos) {
        synchronized (this) {
            if (java.util.Objects.equals(this.activos, activos)) return;
            this.activos = activos;
        }
        notificar();
    }

    // ── Formulario ────────────────────────────────────────────────────────────

    /** Devuelve false (y no cambia nada) si el texto no se puede tipear (más de 2 decimales, etc.). */
    public boolean setMonto(String texto) {
        String t = texto != null ? texto : "";
        if (!MontoInput.esTipeoValido(t, Moneda.PESOS)) return false;
        montoTexto = t;
        if (paso == Paso.FORMULARIO) error = null;
        return true;
    }

    /** El filtro de tipeo frenó un decimal de más: se avisa en el formulario hasta que siga tipeando. */
    public void avisarDecimales() {
        if (paso != Paso.FORMULARIO) return;
        error = MSG_DECIMALES;
        notificar();
    }

    /** Solo acepta un plazo que haya llegado en /tasas. */
    public boolean elegirPlazo(int dias) {
        if (paso != Paso.FORMULARIO || tasaPara(getTasas(), dias) == null) return false;
        plazoDias = dias;
        error = null;
        notificar();
        return true;
    }

    /** "Usar todo": el saldo exacto en pesos. */
    public void usarTodo(BigDecimal saldoPesos) {
        if (saldoPesos == null || paso != Paso.FORMULARIO) return;
        montoTexto = MontoInput.aTexto(saldoPesos, Moneda.PESOS);
        error = null;
        notificar();
    }

    // ── Paso 1 -> 2: validar y pedir confirmación (NO mueve plata) ────────────

    public void continuar(BigDecimal saldoPesos) {
        if (enviando || paso != Paso.FORMULARIO) return;
        error = validar(getTasas(), plazoDias, montoTexto, saldoPesos, getActivos());
        if (error == null) {
            montoAConfirmar = MontoInput.parsear(montoTexto);
            diasAConfirmar = plazoDias;
            paso = Paso.CONFIRMAR;
        }
        notificar();
    }

    /** "Modificar": vuelve al formulario conservando monto y plazo. */
    public void editar() {
        if (enviando || paso != Paso.CONFIRMAR) return;
        paso = Paso.FORMULARIO;
        error = null;
        notificar();
    }

    // ── Paso 2 -> 3: el ÚNICO lugar donde se mueve plata ─────────────────────

    public void confirmar() {
        if (enviando || paso != Paso.CONFIRMAR || montoAConfirmar == null) return; // doble tap = 1 request
        TasasPlazoFijoResponse t = getTasas();
        if (tasaPara(t, diasAConfirmar) == null) {
            error = MSG_SIN_TASAS;
            notificar();
            return;
        }
        // Se llenó mientras miraba el resumen (lo avisó la lista): no vale la pena el POST
        if (limiteAlcanzado(t, getActivos())) {
            error = msgLimite(t.getMaxActivos());
            notificar();
            return;
        }
        enviando = true;
        error = null;
        notificar();
        CrearPlazoFijoRequest body = new CrearPlazoFijoRequest(montoAConfirmar, diasAConfirmar);
        apiSinReintentos.get().crearPlazoFijo(body).enqueue(new Callback<PlazoFijoResponse>() {
            @Override
            public void onResponse(Call<PlazoFijoResponse> call, Response<PlazoFijoResponse> response) {
                enviando = false;
                if (response.isSuccessful() && response.body() != null && response.body().getId() != null) {
                    resultado = response.body();
                    paso = Paso.EXITO;
                    alPosiblementeMoverPlata.run();
                } else if (response.isSuccessful()) {
                    // 2xx sin body legible: se hizo, pero no sabemos con qué datos
                    paso = Paso.INCIERTO;
                    error = MSG_INCIERTO;
                    alPosiblementeMoverPlata.run();
                } else {
                    // Rechazo explícito: no se movió nada; su texto se muestra tal cual. La
                    // configuración (plazos, mínimo, tope) o los activos pudieron cambiar: se re-piden.
                    error = mensajeError(response);
                    cargarTasas();
                    alRechazar.run();
                }
                notificar();
            }

            @Override
            public void onFailure(Call<PlazoFijoResponse> call, Throwable t) {
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
        // Sin {"error"} en el body (ej. validación de @Valid): el genérico de la web
        boolean generico = mensaje.equals(ApiErrores.MSG_DATOS_INVALIDOS)
                || mensaje.equals(ApiErrores.MSG_RESPUESTA_INESPERADA);
        return generico ? MSG_NO_SE_PUDO : mensaje;
    }

    // ── Estado para la UI ─────────────────────────────────────────────────────

    public synchronized TasasPlazoFijoResponse getTasas() { return tasas; }
    public synchronized String getErrorTasas() { return errorTasas; }
    public synchronized boolean isCargandoTasas() { return tasasEnVuelo && tasas == null; }
    public synchronized Integer getActivos() { return activos; }
    public String getMontoTexto() { return montoTexto; }
    public Integer getPlazoDias() { return plazoDias; }
    public Paso getPaso() { return paso; }
    public PlazoFijoResponse getResultado() { return resultado; }
    public String getError() { return error; }
    public boolean isEnviando() { return enviando; }
    public BigDecimal getMontoAConfirmar() { return montoAConfirmar; }
    public int getDiasAConfirmar() { return diasAConfirmar; }

    /** Plazos que se pueden elegir, en el orden del backend (vacío si no hay tasas). */
    public List<TasaPlazoFijo> getOpciones() {
        TasasPlazoFijoResponse t = getTasas();
        return t != null ? t.getTasas() : java.util.Collections.emptyList();
    }

    /** La tasa elegida (null si no hay tasas todavía). */
    public TasaPlazoFijo getTasaElegida() { return tasaPara(getTasas(), plazoDias); }

    /** La tasa del plazo que se está confirmando. */
    public TasaPlazoFijo getTasaAConfirmar() { return tasaPara(getTasas(), diasAConfirmar); }

    public boolean isLimiteAlcanzado() { return limiteAlcanzado(getTasas(), getActivos()); }

    /** Monto tipeado (null si no es un número). */
    public BigDecimal getMonto() { return MontoInput.parsear(montoTexto); }

    /** Preview del interés del formulario (null = sin monto o sin tasa). */
    public BigDecimal getInteresPreview() {
        TasaPlazoFijo tasa = getTasaElegida();
        return tasa == null ? null : CalculoPlazoFijo.interes(getMonto(), tasa.getTna(), tasa.getDias());
    }

    // ── Guardado ante muerte del proceso ───────────────────────────────────────

    /**
     * Restaura un paso guardado. Una request en vuelo no sobrevive a la muerte del proceso. Las
     * tasas no se guardan: se vuelven a pedir (sin ellas no se puede confirmar).
     */
    public void restaurar(Integer plazoDias, Paso paso, PlazoFijoResponse resultado) {
        this.plazoDias = plazoDias;
        if (paso == Paso.EXITO && resultado != null) {
            this.paso = Paso.EXITO;
            this.resultado = resultado;
        } else if (paso == Paso.CONFIRMAR && plazoDias != null && getMonto() != null && getMonto().signum() > 0) {
            this.paso = Paso.CONFIRMAR;
            this.montoAConfirmar = getMonto();
            this.diasAConfirmar = plazoDias;
        } else if (paso == Paso.INCIERTO) {
            this.paso = Paso.INCIERTO;
            this.error = MSG_INCIERTO;
        }
        notificar();
    }
}
