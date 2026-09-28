package com.example.payxmobile.cajas;

import com.example.payxmobile.model.CajaAhorroRequest;
import com.example.payxmobile.model.CajaAhorroResponse;
import com.example.payxmobile.model.LimiteCajasResponse;
import com.example.payxmobile.network.ApiService;
import com.example.payxmobile.network.EnvioSinReintento;
import com.example.payxmobile.transferencias.Moneda;
import com.example.payxmobile.transferencias.MontoInput;

import java.math.BigDecimal;
import java.util.function.Consumer;
import java.util.function.Supplier;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Crear o editar una caja de ahorro (CajaAhorroModal.jsx): nombre, color e ícono de las
 * whitelists, y meta opcional.
 *
 * - La validación local es la ÚNICA que da textos claros: si falla un @Valid del backend responde
 *   403 sin body (su /error exige token), no "El nombre es obligatorio".
 * - Crear: el máximo de cajas viene de GET /limite (lo configura el admin); con el máximo alcanzado
 *   no se manda el POST. Igual se maneja el 400 por si el límite cambió justo antes.
 * - Editar: la meta en blanco se manda como null EXPLÍCITO (= quitarla).
 * - Un solo pedido en vuelo; sin respuesta -> INCIERTO (crear no tiene idempotencia).
 * Sin Android: la Activity lo guarda en un ViewModel.
 */
public class FormularioCaja {

    public enum Paso { EDITANDO, EXITO, INCIERTO }

    public static final int MAX_NOMBRE = 40;

    public static final String MSG_NOMBRE_VACIO = "Ponele un nombre a la caja.";
    public static final String MSG_NOMBRE_LARGO = "El nombre puede tener hasta 40 caracteres.";
    public static final String MSG_COLOR = "Elegí un color de la lista.";
    public static final String MSG_ICONO = "Elegí un ícono de la lista.";
    public static final String MSG_META_INVALIDA = "Ingresá una meta válida o dejala en blanco.";
    public static final String MSG_META_CERO = "La meta debe ser mayor a cero.";
    public static final String MSG_META_DECIMALES = "La meta puede tener como máximo 2 decimales.";
    public static final String MSG_NO_SE_PUDO = "No se pudo guardar la caja. Intentá de nuevo.";
    public static final String MSG_INCIERTO_CREAR =
            "No pudimos confirmar si la caja se creó. Revisá tus cajas antes de intentar de nuevo";
    public static final String MSG_INCIERTO_EDITAR =
            "No pudimos confirmar si se guardaron los cambios. Revisá tu caja antes de intentar de nuevo";

    public interface Observador {
        void onCambio(FormularioCaja f);
    }

    private final CajaAhorroResponse existente;
    private final Supplier<ApiService> api;
    private final Supplier<ApiService> apiSinReintentos;
    private final Consumer<CajaAhorroResponse> alGuardar;
    private final Runnable alRefrescar;
    private Observador observador;

    private String nombre = "";
    private String color = TemasCaja.COLORES.get(0);
    private String icono = TemasCaja.ICONOS.get(0).clave;
    private String metaTexto = "";

    private Integer maxCajas;
    private boolean limiteEnVuelo;
    private Integer cantidadCajas;

    private Paso paso = Paso.EDITANDO;
    private String error;
    private boolean enviando;
    private CajaAhorroResponse resultado;

    /**
     * @param existente   null = crear; si no, se edita esa caja (el formulario se precarga con ella)
     * @param alGuardar   con la caja que devolvió el backend (mostrarla ya en la lista)
     * @param alRefrescar tras un rechazo o sin saber si se guardó: volver a pedir la lista
     */
    public FormularioCaja(CajaAhorroResponse existente, Supplier<ApiService> api, Supplier<ApiService> apiSinReintentos,
                          Consumer<CajaAhorroResponse> alGuardar, Runnable alRefrescar) {
        this.existente = existente;
        this.api = api;
        this.apiSinReintentos = apiSinReintentos;
        this.alGuardar = alGuardar;
        this.alRefrescar = alRefrescar;
        if (existente != null) {
            nombre = existente.getNombre() != null ? existente.getNombre() : "";
            color = TemasCaja.colorODefecto(existente.getColor());
            icono = TemasCaja.iconoODefecto(existente.getIcono());
            metaTexto = existente.getMontoObjetivo() != null ? MontoInput.aTexto(existente.getMontoObjetivo(), Moneda.PESOS) : "";
        }
    }

    public void setObservador(Observador o) {
        observador = o;
    }

    private void notificar() {
        if (observador != null) observador.onCambio(this);
    }

    // ── Reglas puras ──────────────────────────────────────────────────────────

    /** La meta que se manda: null si está en blanco (= sin meta). */
    public static BigDecimal meta(String metaTexto) {
        if (metaTexto == null || metaTexto.trim().isEmpty()) return null;
        return MontoInput.parsear(metaTexto);
    }

    /** null = OK. Mismo criterio que el backend, con textos claros. */
    public static String validar(String nombre, String color, String icono, String metaTexto) {
        String limpio = nombre != null ? nombre.trim() : "";
        if (limpio.isEmpty()) return MSG_NOMBRE_VACIO;
        if (limpio.length() > MAX_NOMBRE) return MSG_NOMBRE_LARGO;
        if (!TemasCaja.colorValido(color)) return MSG_COLOR;
        if (!TemasCaja.iconoValido(icono)) return MSG_ICONO;
        if (metaTexto != null && !metaTexto.trim().isEmpty()) {
            BigDecimal meta = MontoInput.parsear(metaTexto);
            if (meta == null) return MSG_META_INVALIDA;
            if (meta.signum() <= 0) return MSG_META_CERO;
            BigDecimal m = meta.stripTrailingZeros();
            if (m.scale() > 2) return MSG_META_DECIMALES;
            if (m.precision() - m.scale() > Moneda.MAX_ENTEROS) return MSG_META_INVALIDA;
        }
        return null;
    }

    public static boolean limiteAlcanzado(Integer maxCajas, Integer cantidad) {
        return maxCajas != null && cantidad != null && cantidad >= maxCajas;
    }

    public static String msgLimite(int maxCajas) {
        return "Ya tenés el máximo de " + maxCajas + " cajas de ahorro";
    }

    // ── Límite (solo importa al crear) ────────────────────────────────────────

    /** GET /limite, sin solapar pedidos. Si falla se queda sin saberlo: decide el backend. */
    public void cargarLimite() {
        synchronized (this) {
            if (limiteEnVuelo) return;
            limiteEnVuelo = true;
        }
        api.get().obtenerLimiteCajas().enqueue(new Callback<LimiteCajasResponse>() {
            @Override
            public void onResponse(Call<LimiteCajasResponse> call, Response<LimiteCajasResponse> response) {
                synchronized (FormularioCaja.this) {
                    limiteEnVuelo = false;
                    LimiteCajasResponse r = response.body();
                    if (response.isSuccessful() && r != null && r.getMaxPorUsuario() != null) maxCajas = r.getMaxPorUsuario();
                }
                notificar();
            }

            @Override
            public void onFailure(Call<LimiteCajasResponse> call, Throwable t) {
                synchronized (FormularioCaja.this) {
                    limiteEnVuelo = false;
                }
                notificar();
            }
        });
    }

    /** Cuántas cajas tiene hoy (de la lista); null = todavía no se sabe. */
    public void setCantidadCajas(Integer cantidad) {
        synchronized (this) {
            if (java.util.Objects.equals(cantidadCajas, cantidad)) return;
            cantidadCajas = cantidad;
        }
        notificar();
    }

    // ── Formulario ────────────────────────────────────────────────────────────

    public void setNombre(String texto) {
        nombre = texto != null ? texto : "";
        if (paso == Paso.EDITANDO) error = null;
    }

    /** Solo acepta un color de la whitelist. */
    public boolean elegirColor(String c) {
        if (!TemasCaja.colorValido(c) || enviando) return false;
        color = c;
        notificar();
        return true;
    }

    /** Solo acepta un ícono de la whitelist. */
    public boolean elegirIcono(String i) {
        if (!TemasCaja.iconoValido(i) || enviando) return false;
        icono = i;
        notificar();
        return true;
    }

    /** false (sin cambiar nada) si no se puede tipear (más de 2 decimales, 13 enteros...). */
    public boolean setMeta(String texto) {
        String t = texto != null ? texto : "";
        if (!MontoInput.esTipeoValido(t, Moneda.PESOS)) return false;
        metaTexto = t;
        if (paso == Paso.EDITANDO) error = null;
        return true;
    }

    // ── Guardar ───────────────────────────────────────────────────────────────

    public void guardar() {
        if (enviando || paso != Paso.EDITANDO) return; // doble tap = 1 request
        error = validar(nombre, color, icono, metaTexto);
        if (error == null && !esEdicion() && isLimiteAlcanzado()) error = msgLimite(getMaxCajas());
        if (error != null) {
            notificar();
            return;
        }
        enviando = true;
        notificar();
        CajaAhorroRequest body = new CajaAhorroRequest(nombre.trim(), color, icono, meta(metaTexto));
        ApiService s = apiSinReintentos.get();
        Call<CajaAhorroResponse> call = esEdicion() ? s.editarCajaAhorro(existente.getId(), body) : s.crearCajaAhorro(body);
        EnvioSinReintento.enviar(call, true, new EnvioSinReintento.Manejador<CajaAhorroResponse>() {
            @Override
            public void exito(CajaAhorroResponse caja) {
                enviando = false;
                resultado = caja;
                paso = Paso.EXITO;
                alGuardar.accept(caja);
                notificar();
            }

            @Override
            public void rechazo(String mensaje, int codigo) {
                enviando = false;
                error = EnvioSinReintento.textoRechazo(mensaje, MSG_NO_SE_PUDO);
                // El límite (o la caja, si se editaba) pudo cambiar: se vuelve a pedir
                if (!esEdicion()) cargarLimite();
                alRefrescar.run();
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
                error = esEdicion() ? MSG_INCIERTO_EDITAR : MSG_INCIERTO_CREAR;
                alRefrescar.run();
                notificar();
            }
        });
    }

    // ── Estado para la UI ─────────────────────────────────────────────────────

    public boolean esEdicion() { return existente != null; }
    public CajaAhorroResponse getExistente() { return existente; }
    public String getNombre() { return nombre; }
    public String getColor() { return color; }
    public String getIcono() { return icono; }
    public String getMetaTexto() { return metaTexto; }
    public synchronized Integer getMaxCajas() { return maxCajas; }
    public synchronized Integer getCantidadCajas() { return cantidadCajas; }
    public boolean isLimiteAlcanzado() { return limiteAlcanzado(getMaxCajas(), getCantidadCajas()); }
    public Paso getPaso() { return paso; }
    public String getError() { return error; }
    public boolean isEnviando() { return enviando; }
    public CajaAhorroResponse getResultado() { return resultado; }

    // ── Guardado ante muerte del proceso ───────────────────────────────────────

    /** Restaura lo tipeado/elegido (y un INCIERTO: el pedido en vuelo no sobrevive). */
    public void restaurar(String nombre, String color, String icono, String metaTexto, boolean incierto) {
        setNombre(nombre);
        if (TemasCaja.colorValido(color)) this.color = color;
        if (TemasCaja.iconoValido(icono)) this.icono = icono;
        setMeta(metaTexto);
        if (incierto) {
            paso = Paso.INCIERTO;
            error = esEdicion() ? MSG_INCIERTO_EDITAR : MSG_INCIERTO_CREAR;
        }
        notificar();
    }
}
