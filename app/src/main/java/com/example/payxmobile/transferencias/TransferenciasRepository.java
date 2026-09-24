package com.example.payxmobile.transferencias;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.example.payxmobile.model.TransferenciaResponse;
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
 * Transferencias del usuario (enviadas y recibidas, más recientes primero, tope 2.000 del backend).
 * Auto-refresco cada 10 s solo mientras una pantalla lo pide (entre onStart y onStop), sin
 * solapar pedidos. Si un refresco falla se mantiene la última lista buena.
 */
public class TransferenciasRepository {

    public static final long INTERVALO_MS = 10_000L;
    public static final int TOPE_BACKEND = 2000;

    public static final class Estado {
        /** null = todavía no se pudo cargar nunca. */
        public final List<TransferenciaResponse> lista;
        public final String errorPrimeraCarga;
        public final boolean desactualizado;
        public final boolean cargando;

        Estado(List<TransferenciaResponse> lista, String errorPrimeraCarga, boolean desactualizado, boolean cargando) {
            this.lista = lista;
            this.errorPrimeraCarga = errorPrimeraCarga;
            this.desactualizado = desactualizado;
            this.cargando = cargando;
        }

        /** El backend devuelve como máximo las 2.000 más recientes. */
        public boolean llegoAlTope() {
            return lista != null && lista.size() >= TOPE_BACKEND;
        }

        public TransferenciaResponse buscar(String id) {
            if (lista == null || id == null) return null;
            for (TransferenciaResponse t : lista) if (id.equals(t.getId())) return t;
            return null;
        }
    }

    public interface Observador {
        void onCambio(Estado estado);
    }

    private static TransferenciasRepository instancia;

    public static synchronized TransferenciasRepository get(Context context) {
        if (instancia == null) {
            Context app = context.getApplicationContext();
            Handler main = new Handler(Looper.getMainLooper());
            instancia = new TransferenciasRepository(() -> RetrofitClient.getService(app), (tarea, demora) -> {
                main.postDelayed(tarea, demora);
                return () -> main.removeCallbacks(tarea);
            });
        }
        return instancia;
    }

    private final Supplier<ApiService> api;
    private final SaldosRepository.Programador programador;
    private final List<Observador> observadores = new CopyOnWriteArrayList<>();

    private List<TransferenciaResponse> lista;
    private String errorPrimeraCarga;
    private boolean desactualizado;
    private boolean enVuelo;
    private int generacion;
    // Cambia en cada reemplazar(): una lista pedida ANTES de un confirmar/cancelar se descarta
    private int cambiosLocales;
    private int pedidosDeAutoRefresco;
    private Runnable cancelarTimer;

    public TransferenciasRepository(Supplier<ApiService> api, SaldosRepository.Programador programador) {
        this.api = api;
        this.programador = programador;
    }

    public void observar(Observador o) {
        observadores.add(o);
        o.onCambio(getEstado());
    }

    public void dejarDeObservar(Observador o) {
        observadores.remove(o);
    }

    public synchronized Estado getEstado() {
        // Misma instancia inmutable hasta que la lista cambie: las pantallas detectan "no cambió nada"
        return new Estado(lista, errorPrimeraCarga, desactualizado, enVuelo);
    }

    private void notificar() {
        Estado e = getEstado();
        for (Observador o : observadores) o.onCambio(e);
    }

    /**
     * Varias pantallas pueden pedir auto-refresco a la vez (listado + envío): se lleva la cuenta
     * y el timer corre mientras haya al menos una. Al iniciar, refresca en el momento.
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

    /** Reemplaza una transferencia con la versión que devolvió el backend (confirmar/cancelar/concepto). */
    public void reemplazar(TransferenciaResponse nueva) {
        synchronized (this) {
            if (lista == null || nueva == null) return;
            cambiosLocales++;
            for (int i = 0; i < lista.size(); i++) {
                if (lista.get(i).getId().equals(nueva.getId())) {
                    List<TransferenciaResponse> copia = new ArrayList<>(lista);
                    copia.set(i, nueva);
                    lista = Collections.unmodifiableList(copia);
                    break;
                }
            }
        }
        notificar();
    }

    public void refrescar() {
        final int gen;
        final int cambios;
        synchronized (this) {
            if (enVuelo) return; // sin solapar pedidos
            enVuelo = true;
            gen = generacion;
            cambios = cambiosLocales;
        }
        notificar();
        api.get().listarTransferencias().enqueue(new Callback<List<TransferenciaResponse>>() {
            @Override
            public void onResponse(Call<List<TransferenciaResponse>> call, Response<List<TransferenciaResponse>> response) {
                boolean vieja;
                synchronized (TransferenciasRepository.this) {
                    if (gen != generacion) return;
                    enVuelo = false;
                    vieja = cambios != cambiosLocales;
                }
                if (vieja) {
                    // Se pidió antes de un confirmar/cancelar: podría deshacerlo en pantalla
                    refrescar();
                    return;
                }
                synchronized (TransferenciasRepository.this) {
                    if (gen != generacion) return;
                    if (response.isSuccessful() && response.body() != null) {
                        lista = Collections.unmodifiableList(new ArrayList<>(response.body()));
                        errorPrimeraCarga = null;
                        desactualizado = false;
                    } else {
                        registrarFallo(ApiErrores.mensaje(response));
                    }
                }
                notificar();
            }

            @Override
            public void onFailure(Call<List<TransferenciaResponse>> call, Throwable t) {
                synchronized (TransferenciasRepository.this) {
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
