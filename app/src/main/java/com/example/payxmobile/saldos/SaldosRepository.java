package com.example.payxmobile.saldos;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.example.payxmobile.model.CotizacionCripto;
import com.example.payxmobile.model.PerfilResponse;
import com.example.payxmobile.network.ApiErrores;
import com.example.payxmobile.network.ApiService;
import com.example.payxmobile.network.RetrofitClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * ÚNICA fuente de saldos de la app (GET /api/perfil) y de cotizaciones cripto
 * (GET /api/cotizacion/cripto). Todas las pantallas leen de acá y, cuando mueven plata
 * (transferencia, compra/venta, plazo fijo, pago...), llaman a {@link #refrescar()}.
 *
 * - Auto-refresco solo mientras alguien lo pide (el Home entre onStart y onStop): saldo cada
 *   10 s y cotizaciones cada 20 s. Al iniciarlo refresca en el momento.
 * - Nunca hay dos requests del mismo tipo en vuelo: si una sigue pendiente, la siguiente se saltea.
 * - Si falla un refresco se mantiene el último dato bueno; nunca se inventa un 0.
 * - {@link #limpiar()} (logout) descarta el estado y las respuestas que lleguen tarde.
 */
public class SaldosRepository {

    public static final long INTERVALO_SALDO_MS = 10_000L;
    public static final long INTERVALO_COTIZACIONES_MS = 20_000L;
    /**
     * Con la pantalla de compra/venta de cripto abierta. Es lo mínimo seguro: el backend limita
     * GET /api/cotizacion/cripto a 30/min por usuario (cada 2 s ya lo agotaría, sin margen) y además
     * refresca el precio de CoinGecko como mucho cada 60 s, así que pedirlo más seguido no trae nada nuevo.
     */
    public static final long INTERVALO_COTIZACIONES_RAPIDO_MS = 3_000L;

    /** Programa una tarea con demora y devuelve cómo cancelarla. */
    public interface Programador {
        Runnable programar(Runnable tarea, long demoraMs);
    }

    public interface Observador {
        void onCambio(EstadoSaldos estado);
    }

    private static SaldosRepository instancia;

    public static synchronized SaldosRepository get(Context context) {
        if (instancia == null) {
            Context app = context.getApplicationContext();
            Handler main = new Handler(Looper.getMainLooper());
            instancia = new SaldosRepository(() -> RetrofitClient.getService(app), (tarea, demora) -> {
                main.postDelayed(tarea, demora);
                return () -> main.removeCallbacks(tarea);
            });
        }
        return instancia;
    }

    private final Supplier<ApiService> api;
    private final Programador programador;
    private final List<Observador> observadores = new CopyOnWriteArrayList<>();

    // Estado (protegido por "this")
    private PerfilResponse perfil;
    private String errorPrimeraCarga;
    private boolean desactualizado;
    private Map<String, CotizacionCripto> cotizaciones = new LinkedHashMap<>();
    private boolean saldoEnVuelo;
    private boolean cotizacionesEnVuelo;
    private boolean sinCotizacionesEnBackend;
    private boolean sesionInvalida;
    // Cambia en cada limpiar(): las respuestas de una sesión anterior se descartan
    private int generacion;

    private int pedidosDeAutoRefresco;
    private int pedidosDeCotizacionesRapidas;
    private Runnable cancelarSaldo;
    private Runnable cancelarCotizaciones;

    public SaldosRepository(Supplier<ApiService> api, Programador programador) {
        this.api = api;
        this.programador = programador;
    }

    // ── Observación ───────────────────────────────────────────────────────────

    /** Registra y le entrega el estado actual en el momento. */
    public void observar(Observador o) {
        observadores.add(o);
        o.onCambio(getEstado());
    }

    public void dejarDeObservar(Observador o) {
        observadores.remove(o);
    }

    public synchronized EstadoSaldos getEstado() {
        return new EstadoSaldos(perfil, errorPrimeraCarga, desactualizado,
                new LinkedHashMap<>(cotizaciones), saldoEnVuelo, cotizacionesEnVuelo, sesionInvalida,
                sinCotizacionesEnBackend);
    }

    private void notificar() {
        EstadoSaldos estado = getEstado();
        for (Observador o : observadores) o.onCambio(estado);
    }

    // ── Refresco ──────────────────────────────────────────────────────────────

    /** Pide el saldo ya. Llamalo después de cualquier operación que mueva plata. */
    public void refrescar() {
        pedirSaldo();
    }

    /** Solo las cotizaciones cripto (pantalla de compra/venta al abrirse). Sin solapar pedidos. */
    public void refrescarCotizaciones() {
        pedirCotizaciones();
    }

    /** Saldo + cotizaciones (pull-to-refresh). */
    public void refrescarTodo() {
        pedirSaldo();
        pedirCotizaciones();
    }

    /** Pantallas que pidieron auto-refresco (Inicio, compra/venta de cripto...). Timer mientras haya >= 1. */
    public void iniciarAutoRefresco() {
        synchronized (this) {
            pedidosDeAutoRefresco++;
            if (pedidosDeAutoRefresco > 1) return;
        }
        refrescarTodo();
        programarSaldo();
        programarCotizaciones();
    }

    public synchronized void detenerAutoRefresco() {
        if (pedidosDeAutoRefresco > 0) pedidosDeAutoRefresco--;
        if (pedidosDeAutoRefresco == 0) cortarTimers();
    }

    private synchronized void cortarTimers() {
        if (cancelarSaldo != null) cancelarSaldo.run();
        if (cancelarCotizaciones != null) cancelarCotizaciones.run();
        cancelarSaldo = null;
        cancelarCotizaciones = null;
    }

    /** Logout: borra todo y descarta las respuestas que sigan en vuelo. */
    public void limpiar() {
        synchronized (this) {
            pedidosDeAutoRefresco = 0;
            pedidosDeCotizacionesRapidas = 0;
            cortarTimers();
            generacion++;
            perfil = null;
            errorPrimeraCarga = null;
            desactualizado = false;
            cotizaciones = new LinkedHashMap<>();
            saldoEnVuelo = false;
            cotizacionesEnVuelo = false;
            sesionInvalida = false;
            sinCotizacionesEnBackend = false;
        }
        notificar();
    }

    private synchronized void programarSaldo() {
        if (pedidosDeAutoRefresco == 0) return;
        cancelarSaldo = programador.programar(() -> {
            if (!estaActivo()) return;
            pedirSaldo();
            programarSaldo();
        }, INTERVALO_SALDO_MS);
    }

    /**
     * Precios cripto "en vivo" (cada 3 s) mientras alguna pantalla lo pida; al soltarlo se vuelve a
     * 20 s. Solo tiene efecto con el auto-refresco activo.
     */
    public synchronized void iniciarCotizacionesRapidas() {
        pedidosDeCotizacionesRapidas++;
        if (pedidosDeCotizacionesRapidas == 1) reprogramarCotizaciones();
    }

    public synchronized void detenerCotizacionesRapidas() {
        if (pedidosDeCotizacionesRapidas > 0) pedidosDeCotizacionesRapidas--;
        if (pedidosDeCotizacionesRapidas == 0) reprogramarCotizaciones();
    }

    private synchronized void reprogramarCotizaciones() {
        if (pedidosDeAutoRefresco == 0) return;
        if (cancelarCotizaciones != null) cancelarCotizaciones.run();
        programarCotizaciones();
    }

    private synchronized void programarCotizaciones() {
        if (pedidosDeAutoRefresco == 0) return;
        cancelarCotizaciones = programador.programar(() -> {
            if (!estaActivo()) return;
            pedirCotizaciones();
            programarCotizaciones();
        }, pedidosDeCotizacionesRapidas > 0 ? INTERVALO_COTIZACIONES_RAPIDO_MS : INTERVALO_COTIZACIONES_MS);
    }

    private synchronized boolean estaActivo() {
        return pedidosDeAutoRefresco > 0;
    }

    private void pedirSaldo() {
        final int gen;
        synchronized (this) {
            if (saldoEnVuelo || sesionInvalida) return; // sin solapamiento
            saldoEnVuelo = true;
            gen = generacion;
        }
        notificar();
        api.get().obtenerPerfil().enqueue(new Callback<PerfilResponse>() {
            @Override
            public void onResponse(Call<PerfilResponse> call, Response<PerfilResponse> response) {
                synchronized (SaldosRepository.this) {
                    if (gen != generacion) return;
                    saldoEnVuelo = false;
                    if (response.isSuccessful() && response.body() != null) {
                        perfil = response.body();
                        errorPrimeraCarga = null;
                        desactualizado = false;
                    } else if (response.code() == 401) {
                        // 401 = sin sesión válida (token vencido/inválido o cuenta desactivada): el interceptor
                        // ya cierra la sesión; acá solo se corta el polling. Un 403 (sin permiso) es un error más.
                        sesionInvalida = true;
                        pedidosDeAutoRefresco = 0;
                        cortarTimers();
                    } else {
                        registrarFallo(ApiErrores.mensaje(response));
                    }
                }
                notificar();
            }

            @Override
            public void onFailure(Call<PerfilResponse> call, Throwable t) {
                synchronized (SaldosRepository.this) {
                    if (gen != generacion) return;
                    saldoEnVuelo = false;
                    registrarFallo(ApiErrores.mensajeFallo(t));
                }
                notificar();
            }
        });
    }

    // Con saldo previo se mantiene y se marca desactualizado; sin saldo, error de primera carga
    private void registrarFallo(String mensaje) {
        if (perfil == null) {
            errorPrimeraCarga = mensaje;
        } else {
            desactualizado = true;
        }
    }

    private void pedirCotizaciones() {
        final int gen;
        synchronized (this) {
            if (cotizacionesEnVuelo || sesionInvalida) return;
            cotizacionesEnVuelo = true;
            gen = generacion;
        }
        notificar();
        api.get().obtenerCotizacionesCripto().enqueue(new Callback<List<CotizacionCripto>>() {
            @Override
            public void onResponse(Call<List<CotizacionCripto>> call, Response<List<CotizacionCripto>> response) {
                synchronized (SaldosRepository.this) {
                    if (gen != generacion) return;
                    cotizacionesEnVuelo = false;
                    // Si falla (503, 429...) se mantienen las últimas conocidas
                    if (response.isSuccessful() && response.body() != null) {
                        Map<String, CotizacionCripto> nuevas = new LinkedHashMap<>();
                        for (CotizacionCripto c : new ArrayList<>(response.body())) {
                            if (c != null && c.getSimbolo() != null && c.getPrecio() != null) {
                                nuevas.put(c.getSimbolo(), c);
                            }
                        }
                        cotizaciones = nuevas;
                        sinCotizacionesEnBackend = false;
                    } else if (response.code() == 503) {
                        sinCotizacionesEnBackend = true;
                    }
                }
                notificar();
            }

            @Override
            public void onFailure(Call<List<CotizacionCripto>> call, Throwable t) {
                synchronized (SaldosRepository.this) {
                    if (gen != generacion) return;
                    cotizacionesEnVuelo = false;
                }
                notificar();
            }
        });
    }
}
