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
    private boolean sesionInvalida;
    // Cambia en cada limpiar(): las respuestas de una sesión anterior se descartan
    private int generacion;

    private boolean autoRefrescoActivo;
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
                new LinkedHashMap<>(cotizaciones), saldoEnVuelo, cotizacionesEnVuelo, sesionInvalida);
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

    /** Saldo + cotizaciones (pull-to-refresh). */
    public void refrescarTodo() {
        pedirSaldo();
        pedirCotizaciones();
    }

    public void iniciarAutoRefresco() {
        synchronized (this) {
            if (autoRefrescoActivo) return;
            autoRefrescoActivo = true;
        }
        refrescarTodo();
        programarSaldo();
        programarCotizaciones();
    }

    public synchronized void detenerAutoRefresco() {
        autoRefrescoActivo = false;
        if (cancelarSaldo != null) cancelarSaldo.run();
        if (cancelarCotizaciones != null) cancelarCotizaciones.run();
        cancelarSaldo = null;
        cancelarCotizaciones = null;
    }

    /** Logout: borra todo y descarta las respuestas que sigan en vuelo. */
    public void limpiar() {
        synchronized (this) {
            detenerAutoRefresco();
            generacion++;
            perfil = null;
            errorPrimeraCarga = null;
            desactualizado = false;
            cotizaciones = new LinkedHashMap<>();
            saldoEnVuelo = false;
            cotizacionesEnVuelo = false;
            sesionInvalida = false;
        }
        notificar();
    }

    private synchronized void programarSaldo() {
        if (!autoRefrescoActivo) return;
        cancelarSaldo = programador.programar(() -> {
            if (!estaActivo()) return;
            pedirSaldo();
            programarSaldo();
        }, INTERVALO_SALDO_MS);
    }

    private synchronized void programarCotizaciones() {
        if (!autoRefrescoActivo) return;
        cancelarCotizaciones = programador.programar(() -> {
            if (!estaActivo()) return;
            pedirCotizaciones();
            programarCotizaciones();
        }, INTERVALO_COTIZACIONES_MS);
    }

    private synchronized boolean estaActivo() {
        return autoRefrescoActivo;
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
                    } else if (response.code() == 403) {
                        // GET sin body: 403 = token inválido/vencido o cuenta desactivada
                        sesionInvalida = true;
                        detenerAutoRefresco();
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
