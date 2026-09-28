package com.example.payxmobile.actividad;

import com.example.payxmobile.network.ApiErrores;
import com.example.payxmobile.network.ApiService;
import com.example.payxmobile.saldos.SaldosRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Supplier;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Una lista del backend que alimenta el feed de movimientos (compras/ventas de dólares hoy; cripto
 * y plazos fijos después). Mismo comportamiento que TransferenciasRepository:
 * - Auto-refresco cada 10 s solo mientras alguna pantalla lo pide, sin solapar pedidos.
 * - Si un refresco falla se mantiene la última lista buena.
 * - {@link #agregar}, {@link #actualizar} y {@link #quitar} muestran al instante una operación recién
 *   hecha (lo que devolvió el backend); una lista pedida ANTES se descarta y se vuelve a pedir, para
 *   que no la deshaga.
 */
public class ListaRemota<T> {

    public static final long INTERVALO_MS = 10_000L;

    public static final class Estado<T> {
        /** null = todavía no se pudo cargar nunca. */
        public final List<T> lista;
        public final String errorPrimeraCarga;
        public final boolean desactualizado;
        public final boolean cargando;

        Estado(List<T> lista, String errorPrimeraCarga, boolean desactualizado, boolean cargando) {
            this.lista = lista;
            this.errorPrimeraCarga = errorPrimeraCarga;
            this.desactualizado = desactualizado;
            this.cargando = cargando;
        }
    }

    public interface Observador<T> {
        void onCambio(Estado<T> estado);
    }

    private final Supplier<ApiService> api;
    private final Function<ApiService, Call<List<T>>> pedido;
    private final Function<T, String> id;
    private final SaldosRepository.Programador programador;
    private final List<Observador<T>> observadores = new CopyOnWriteArrayList<>();

    private List<T> lista;
    private String errorPrimeraCarga;
    private boolean desactualizado;
    private boolean enVuelo;
    private int generacion;
    private int cambiosLocales;
    private int pedidosDeAutoRefresco;
    private Runnable cancelarTimer;

    /**
     * @param pedido cómo pedir la lista (ej. {@code ApiService::listarCambiosDolares})
     * @param id     identificador único de cada elemento (para no duplicar al agregar)
     */
    public ListaRemota(Supplier<ApiService> api, Function<ApiService, Call<List<T>>> pedido,
                       Function<T, String> id, SaldosRepository.Programador programador) {
        this.api = api;
        this.pedido = pedido;
        this.id = id;
        this.programador = programador;
    }

    public void observar(Observador<T> o) {
        observadores.add(o);
        o.onCambio(getEstado());
    }

    public void dejarDeObservar(Observador<T> o) {
        observadores.remove(o);
    }

    public synchronized Estado<T> getEstado() {
        return new Estado<>(lista, errorPrimeraCarga, desactualizado, enVuelo);
    }

    private void notificar() {
        Estado<T> e = getEstado();
        for (Observador<T> o : observadores) o.onCambio(e);
    }

    public void iniciarAutoRefresco() {
        synchronized (this) {
            pedidosDeAutoRefresco++;
            if (pedidosDeAutoRefresco == 1) programar();
        }
        refrescar();
    }

    public synchronized void detenerAutoRefresco() {
        if (pedidosDeAutoRefresco > 0) pedidosDeAutoRefresco--;
        if (pedidosDeAutoRefresco == 0 && cancelarTimer != null) {
            cancelarTimer.run();
            cancelarTimer = null;
        }
    }

    private synchronized void programar() {
        if (pedidosDeAutoRefresco == 0) return;
        cancelarTimer = programador.programar(() -> {
            synchronized (this) {
                if (pedidosDeAutoRefresco == 0) return;
            }
            refrescar();
            programar();
        }, INTERVALO_MS);
    }

    public void limpiar() {
        synchronized (this) {
            generacion++;
            lista = null;
            errorPrimeraCarga = null;
            desactualizado = false;
            enVuelo = false;
            pedidosDeAutoRefresco = 0;
            if (cancelarTimer != null) cancelarTimer.run();
            cancelarTimer = null;
        }
        notificar();
    }

    /** Agrega al principio (más reciente) un elemento que ya devolvió el backend. Sin duplicar. */
    public void agregar(T nuevo) {
        synchronized (this) {
            if (nuevo == null || lista == null) return;
            String clave = id.apply(nuevo);
            for (T t : lista) if (clave != null && clave.equals(id.apply(t))) return;
            cambiosLocales++;
            List<T> copia = new ArrayList<>(lista.size() + 1);
            copia.add(nuevo);
            copia.addAll(lista);
            lista = Collections.unmodifiableList(copia);
        }
        notificar();
    }

    /**
     * Reemplaza (en su lugar) el elemento con el mismo id por la versión que devolvió el backend, o
     * lo agrega AL FINAL si no estaba (listas ordenadas de la más vieja a la más nueva, ej. cajas).
     */
    public void actualizar(T nuevo) {
        synchronized (this) {
            if (nuevo == null || lista == null) return;
            String clave = id.apply(nuevo);
            List<T> copia = new ArrayList<>(lista);
            boolean reemplazado = false;
            for (int i = 0; i < copia.size(); i++) {
                if (clave != null && clave.equals(id.apply(copia.get(i)))) {
                    copia.set(i, nuevo);
                    reemplazado = true;
                    break;
                }
            }
            if (!reemplazado) copia.add(nuevo);
            cambiosLocales++;
            lista = Collections.unmodifiableList(copia);
        }
        notificar();
    }

    /** Saca el elemento con ese id (ej. una caja recién eliminada). */
    public void quitar(String clave) {
        synchronized (this) {
            if (clave == null || lista == null) return;
            List<T> copia = new ArrayList<>(lista.size());
            for (T t : lista) if (!clave.equals(id.apply(t))) copia.add(t);
            if (copia.size() == lista.size()) return;
            cambiosLocales++;
            lista = Collections.unmodifiableList(copia);
        }
        notificar();
    }

    public void refrescar() {
        final int gen, cambios;
        synchronized (this) {
            if (enVuelo) return;
            enVuelo = true;
            gen = generacion;
            cambios = cambiosLocales;
        }
        notificar();
        pedido.apply(api.get()).enqueue(new Callback<List<T>>() {
            @Override
            public void onResponse(Call<List<T>> call, Response<List<T>> response) {
                boolean vieja;
                synchronized (ListaRemota.this) {
                    if (gen != generacion) return;
                    enVuelo = false;
                    vieja = cambios != cambiosLocales;
                    if (!vieja) {
                        if (response.isSuccessful() && response.body() != null) {
                            List<T> nueva = new ArrayList<>();
                            for (T t : response.body()) if (t != null) nueva.add(t);
                            lista = Collections.unmodifiableList(nueva);
                            errorPrimeraCarga = null;
                            desactualizado = false;
                        } else {
                            registrarFallo(ApiErrores.mensaje(response));
                        }
                    }
                }
                if (vieja) {
                    refrescar(); // se pidió antes de agregar una operación: podría esconderla
                    return;
                }
                notificar();
            }

            @Override
            public void onFailure(Call<List<T>> call, Throwable t) {
                synchronized (ListaRemota.this) {
                    if (gen != generacion) return;
                    enVuelo = false;
                    registrarFallo(ApiErrores.mensajeFallo(t));
                }
                notificar();
            }
        });
    }

    private void registrarFallo(String mensaje) {
        if (lista == null) errorPrimeraCarga = mensaje;
        else desactualizado = true;
    }
}
