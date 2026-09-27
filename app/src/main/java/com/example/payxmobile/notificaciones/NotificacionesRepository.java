package com.example.payxmobile.notificaciones;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.example.payxmobile.model.NotificacionResponse;
import com.example.payxmobile.model.SinLeerResponse;
import com.example.payxmobile.network.ApiErrores;
import com.example.payxmobile.network.ApiService;
import com.example.payxmobile.network.RetrofitClient;
import com.example.payxmobile.saldos.SaldosRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * ÚNICA fuente de notificaciones de la app (campana, badge y panel). Genérico: no sabe de qué
 * módulo viene cada notificación; el título sale de {@link TitulosNotificacion}.
 *
 * - Badge = GET /sin-leer. Lista = GET /api/notificaciones (solo NO leídas), pedida solo mientras
 *   el panel está abierto o, con el panel cerrado, cuando el conteo no coincide (precarga).
 * - Auto-refresco cada 10 s solo mientras alguna pantalla lo pide (entre onStart y onStop), sin
 *   solapar pedidos del mismo tipo.
 * - {@link #refrescar()} después de cualquier operación que mueva plata (ver Refrescos).
 * - Si un refresco falla se mantiene lo último bueno: nunca se muestra "no tenés notificaciones"
 *   por un error.
 * - "Marcar todas como leídas" vacía lista y badge AL INSTANTE; si el PATCH falla se restauran. Una
 *   respuesta pedida antes o durante el PATCH se descarta (no puede volver a mostrar las leídas).
 */
public class NotificacionesRepository {

    public static final long INTERVALO_MS = 10_000L;
    public static final String MSG_NO_SE_PUDO_MARCAR = "No pudimos marcar las notificaciones como leídas.";

    public static final class Estado {
        /** Cantidad sin leer, o null si todavía no se sabe (sin badge). */
        public final Long sinLeer;
        /** null = la lista todavía no se pudo cargar nunca. */
        public final List<NotificacionResponse> lista;
        /** Falló la primera carga de la lista: mostrar error con "Reintentar", NO "vacía". */
        public final String errorPrimeraCarga;
        /** Hay lista, pero el último refresco falló: se muestra la anterior con un aviso. */
        public final boolean desactualizado;
        public final boolean cargandoLista;
        public final boolean marcando;
        /** El último "marcar todas como leídas" falló (la lista NO se vació). */
        public final String errorMarcar;
        /** 403 en un GET: token inválido o cuenta desactivada. */
        public final boolean sesionInvalida;

        Estado(Long sinLeer, List<NotificacionResponse> lista, String errorPrimeraCarga, boolean desactualizado,
               boolean cargandoLista, boolean marcando, String errorMarcar, boolean sesionInvalida) {
            this.sinLeer = sinLeer;
            this.lista = lista;
            this.errorPrimeraCarga = errorPrimeraCarga;
            this.desactualizado = desactualizado;
            this.cargandoLista = cargandoLista;
            this.marcando = marcando;
            this.errorMarcar = errorMarcar;
            this.sesionInvalida = sesionInvalida;
        }

        public boolean mostrarBadge() {
            return sinLeer != null && sinLeer > 0;
        }

        /** "3", "99+". Solo tiene sentido si {@link #mostrarBadge()}. */
        public String textoBadge() {
            if (sinLeer == null) return "";
            return sinLeer > 99 ? "99+" : String.valueOf(sinLeer);
        }

        /** "No tenés notificaciones nuevas" SOLO tras una carga exitosa con 0. */
        public boolean vacia() {
            return lista != null && lista.isEmpty();
        }

        /** Primera carga en curso (ni lista ni error todavía). */
        public boolean esPrimeraCarga() {
            return lista == null && errorPrimeraCarga == null;
        }
    }

    public interface Observador {
        void onCambio(Estado estado);
    }

    private static NotificacionesRepository instancia;

    public static synchronized NotificacionesRepository get(Context context) {
        if (instancia == null) {
            Context app = context.getApplicationContext();
            Handler main = new Handler(Looper.getMainLooper());
            instancia = new NotificacionesRepository(() -> RetrofitClient.getService(app), (tarea, demora) -> {
                main.postDelayed(tarea, demora);
                return () -> main.removeCallbacks(tarea);
            });
        }
        return instancia;
    }

    private final Supplier<ApiService> api;
    private final SaldosRepository.Programador programador;
    private final List<Observador> observadores = new CopyOnWriteArrayList<>();

    // Estado (protegido por "this")
    private Long sinLeer;
    private List<NotificacionResponse> lista;
    private String errorPrimeraCarga;
    private boolean desactualizado;
    private boolean cantidadEnVuelo;
    private boolean listaEnVuelo;
    private boolean marcando;
    private String errorMarcar;
    private boolean sesionInvalida;
    // Cambia en cada limpiar(): respuestas de una sesión anterior se descartan
    private int generacion;
    // Cambia al marcar todas como leídas: respuestas pedidas antes se descartan y se vuelven a pedir
    private int cambiosLocales;

    private int pedidosDeAutoRefresco;
    private int panelesAbiertos;
    private Runnable cancelarTimer;

    public NotificacionesRepository(Supplier<ApiService> api, SaldosRepository.Programador programador) {
        this.api = api;
        this.programador = programador;
    }

    // ── Observación ───────────────────────────────────────────────────────────

    public void observar(Observador o) {
        observadores.add(o);
        o.onCambio(getEstado());
    }

    public void dejarDeObservar(Observador o) {
        observadores.remove(o);
    }

    public synchronized Estado getEstado() {
        return new Estado(sinLeer, lista, errorPrimeraCarga, desactualizado, listaEnVuelo, marcando,
                errorMarcar, sesionInvalida);
    }

    private void notificar() {
        Estado e = getEstado();
        for (Observador o : observadores) o.onCambio(e);
    }

    // ── Ciclo de vida ─────────────────────────────────────────────────────────

    /**
     * Cada pantalla con campana lo pide en onStart y lo suelta en onStop: el timer corre mientras
     * haya al menos una (solo en primer plano). Al iniciar refresca en el momento.
     */
    public void iniciarAutoRefresco() {
        synchronized (this) {
            pedidosDeAutoRefresco++;
            if (pedidosDeAutoRefresco == 1) programar();
        }
        refrescar();
    }

    public synchronized void detenerAutoRefresco() {
        if (pedidosDeAutoRefresco > 0) pedidosDeAutoRefresco--;
        if (pedidosDeAutoRefresco == 0) cancelarTimer();
    }

    /** El panel se muestra: a partir de ahora el polling también trae la lista. */
    public void abrirPanel() {
        synchronized (this) {
            panelesAbiertos++;
            errorMarcar = null;
        }
        refrescar();
    }

    public synchronized void cerrarPanel() {
        if (panelesAbiertos > 0) panelesAbiertos--;
    }

    /** Logout: borra todo, corta el timer y descarta las respuestas que sigan en vuelo. */
    public void limpiar() {
        synchronized (this) {
            generacion++;
            sinLeer = null;
            lista = null;
            errorPrimeraCarga = null;
            desactualizado = false;
            cantidadEnVuelo = false;
            listaEnVuelo = false;
            marcando = false;
            errorMarcar = null;
            sesionInvalida = false;
            pedidosDeAutoRefresco = 0;
            panelesAbiertos = 0;
            cancelarTimer();
        }
        notificar();
    }

    private synchronized void programar() {
        if (pedidosDeAutoRefresco == 0 || sesionInvalida) return;
        cancelarTimer = programador.programar(() -> {
            synchronized (this) {
                if (pedidosDeAutoRefresco == 0) return;
            }
            refrescar();
            programar();
        }, INTERVALO_MS);
    }

    private synchronized void cancelarTimer() {
        if (cancelarTimer != null) cancelarTimer.run();
        cancelarTimer = null;
    }

    // ── Pedidos ───────────────────────────────────────────────────────────────

    /**
     * Badge siempre; la lista si el panel está abierto. Con el panel cerrado, la lista se precarga
     * sola cuando el conteo no coincide con lo que ya hay (así el panel abre al instante).
     */
    public void refrescar() {
        boolean conLista;
        synchronized (this) {
            conLista = panelesAbiertos > 0 || errorPrimeraCarga != null;
        }
        pedirCantidad();
        if (conLista) pedirLista();
    }

    private void pedirCantidad() {
        final int gen, cambios;
        synchronized (this) {
            if (cantidadEnVuelo || sesionInvalida) return;
            cantidadEnVuelo = true;
            gen = generacion;
            cambios = cambiosLocales;
        }
        api.get().contarSinLeer().enqueue(new Callback<SinLeerResponse>() {
            @Override
            public void onResponse(Call<SinLeerResponse> call, Response<SinLeerResponse> response) {
                boolean vieja, repedir = false, precargarLista = false;
                synchronized (NotificacionesRepository.this) {
                    if (gen != generacion) return;
                    cantidadEnVuelo = false;
                    // Pedida antes de (o durante) "marcar leídas": podría resucitar las ya leídas
                    vieja = cambios != cambiosLocales || marcando;
                    if (vieja) {
                        repedir = !marcando;
                    } else if (response.isSuccessful() && response.body() != null) {
                        long cantidad = response.body().getCantidad();
                        sinLeer = cantidad;
                        if (cantidad == 0) {
                            // 0 sin leer = lista vacía: no hace falta pedirla
                            lista = Collections.emptyList();
                            errorPrimeraCarga = null;
                            desactualizado = false;
                        } else if (lista == null || lista.size() != cantidad) {
                            precargarLista = true;
                        }
                    } else if (response.code() == 403) {
                        marcarSesionInvalida();
                    }
                    // Otro error: se mantiene el último número conocido
                }
                if (vieja) {
                    if (repedir) pedirCantidad();
                    return;
                }
                notificar();
                if (precargarLista) precargarLista();
            }

            @Override
            public void onFailure(Call<SinLeerResponse> call, Throwable t) {
                synchronized (NotificacionesRepository.this) {
                    if (gen != generacion) return;
                    cantidadEnVuelo = false;
                }
                notificar();
            }
        });
    }

    /** Pide la lista solo si todavía no coincide con el conteo (chequeado al momento de pedir). */
    private void precargarLista() {
        synchronized (this) {
            if (lista != null && sinLeer != null && lista.size() == sinLeer) return;
        }
        pedirLista();
    }

    private void pedirLista() {
        final int gen, cambios;
        synchronized (this) {
            if (listaEnVuelo || sesionInvalida) return;
            listaEnVuelo = true;
            gen = generacion;
            cambios = cambiosLocales;
        }
        notificar();
        api.get().obtenerNotificaciones().enqueue(new Callback<List<NotificacionResponse>>() {
            @Override
            public void onResponse(Call<List<NotificacionResponse>> call, Response<List<NotificacionResponse>> response) {
                boolean vieja, repedir;
                synchronized (NotificacionesRepository.this) {
                    if (gen != generacion) return;
                    listaEnVuelo = false;
                    vieja = cambios != cambiosLocales || marcando;
                    repedir = !marcando;
                    if (!vieja) {
                        if (response.isSuccessful() && response.body() != null) {
                            List<NotificacionResponse> nuevas = new ArrayList<>();
                            for (NotificacionResponse n : response.body()) if (n != null) nuevas.add(n);
                            lista = Collections.unmodifiableList(nuevas);
                            errorPrimeraCarga = null;
                            desactualizado = false;
                        } else if (response.code() == 403) {
                            marcarSesionInvalida();
                        } else {
                            registrarFallo(ApiErrores.mensaje(response));
                        }
                    }
                }
                if (vieja) {
                    if (repedir) pedirLista();
                    else notificar(); // se dejó de cargar
                    return;
                }
                notificar();
            }

            @Override
            public void onFailure(Call<List<NotificacionResponse>> call, Throwable t) {
                synchronized (NotificacionesRepository.this) {
                    if (gen != generacion) return;
                    listaEnVuelo = false;
                    registrarFallo(ApiErrores.mensajeFallo(t));
                }
                notificar();
            }
        });
    }

    /**
     * PATCH /leer (marca TODAS; no existe "marcar una"). Un solo pedido en vuelo. Lista y badge se
     * vacían AL INSTANTE, sin esperar al backend; si el PATCH falla se restauran tal cual estaban,
     * con el error, y se puede reintentar. Mientras está en vuelo se ignoran los polls (podrían
     * traer las que se están marcando).
     */
    public void marcarTodasLeidas() {
        final int gen;
        final List<NotificacionResponse> listaAntes;
        final Long sinLeerAntes;
        synchronized (this) {
            if (marcando || sesionInvalida) return;
            marcando = true;
            errorMarcar = null;
            gen = generacion;
            listaAntes = lista;
            sinLeerAntes = sinLeer;
            cambiosLocales++;
            lista = Collections.emptyList();
            sinLeer = 0L;
            errorPrimeraCarga = null;
            desactualizado = false;
        }
        notificar();
        api.get().marcarTodasLeidas().enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                synchronized (NotificacionesRepository.this) {
                    if (gen != generacion) return;
                    marcando = false;
                    cambiosLocales++; // lo pedido mientras tanto ya no sirve
                    if (response.isSuccessful()) {
                        // ya estaba vacío
                    } else if (response.code() == 403) {
                        restaurar(listaAntes, sinLeerAntes);
                        marcarSesionInvalida();
                    } else {
                        restaurar(listaAntes, sinLeerAntes);
                        errorMarcar = mensajeMarcar(ApiErrores.mensaje(response));
                    }
                }
                notificar();
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                synchronized (NotificacionesRepository.this) {
                    if (gen != generacion) return;
                    marcando = false;
                    cambiosLocales++;
                    restaurar(listaAntes, sinLeerAntes);
                    errorMarcar = mensajeMarcar(ApiErrores.mensajeFallo(t));
                }
                notificar();
            }
        });
    }

    private void restaurar(List<NotificacionResponse> listaAntes, Long sinLeerAntes) {
        lista = listaAntes;
        sinLeer = sinLeerAntes;
    }

    private static String mensajeMarcar(String detalle) {
        return MSG_NO_SE_PUDO_MARCAR + " " + detalle;
    }

    // Estos endpoints no tienen body validado: un 403 es de autenticación. Se corta el polling
    // (sin bucle de pedidos) y la pantalla manda al login.
    private void marcarSesionInvalida() {
        sesionInvalida = true;
        cancelarTimer();
    }

    private void registrarFallo(String mensaje) {
        if (lista == null) errorPrimeraCarga = mensaje;
        else desactualizado = true;
    }
}
