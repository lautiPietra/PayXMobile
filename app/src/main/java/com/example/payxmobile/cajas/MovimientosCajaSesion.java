package com.example.payxmobile.cajas;

import android.content.Context;

import com.example.payxmobile.utils.SessionManager;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Los depósitos/retiros de cajas que hizo la app, para el feed ("Últimas actividades" y "Mis
 * movimientos"). El backend no tiene historial de estas operaciones (depositar/retirar solo
 * devuelven la caja), así que la app los GUARDA EN EL TELÉFONO, cifrados y por usuario:
 * - Sobreviven a cerrar sesión y volver a entrar, y a cerrar la app.
 * - Otra cuenta en el mismo teléfono no los ve (cada usuario tiene los suyos).
 * - NO aparecen los hechos desde la web u otro teléfono, ni vuelven tras reinstalar la app o
 *   borrar sus datos: no hay de dónde recuperarlos.
 *
 * Publica listas inmutables (más reciente primero): el feed compara instancias para saber si cambió.
 */
public final class MovimientosCajaSesion {

    /** Dónde se guarda la lista de cada usuario (en la app: SharedPreferences cifradas). */
    public interface Almacen {
        /** null si ese usuario no tiene nada guardado (o no se pudo leer). */
        String leer(String usuarioId);

        void guardar(String usuarioId, String contenido);
    }

    public interface Observador {
        void onCambio(List<MovimientoCaja> lista);
    }

    /** Tope por usuario: los más viejos se descartan (el feed muestra de a 100 igual). */
    static final int MAX_GUARDADOS = 500;

    private static MovimientosCajaSesion instancia;

    private final Almacen almacen;
    private final List<Observador> observadores = new CopyOnWriteArrayList<>();
    private String usuarioId;
    private List<MovimientoCaja> lista = Collections.emptyList();

    /** Solo en memoria (tests). */
    public MovimientosCajaSesion() {
        this(null);
    }

    public MovimientosCajaSesion(Almacen almacen) {
        this.almacen = almacen;
    }

    /** La de la app, ya con los movimientos del usuario de la sesión actual cargados. */
    public static MovimientosCajaSesion get(Context context) {
        MovimientosCajaSesion s;
        synchronized (MovimientosCajaSesion.class) {
            if (instancia == null) instancia = new MovimientosCajaSesion(new AlmacenMovimientosCaja(context.getApplicationContext()));
            s = instancia;
        }
        s.usarUsuario(new SessionManager(context).getUserId());
        return s;
    }

    /** Carga los movimientos de ese usuario si cambió (null = sin sesión: lista vacía). */
    public void usarUsuario(String id) {
        List<MovimientoCaja> nueva;
        synchronized (this) {
            if (Objects.equals(id, usuarioId)) return;
            usuarioId = id;
            lista = id != null && almacen != null ? deJson(almacen.leer(id)) : Collections.emptyList();
            nueva = lista;
        }
        for (Observador o : observadores) o.onCambio(nueva);
    }

    public void observar(Observador o) {
        observadores.add(o);
        o.onCambio(getLista());
    }

    public void dejarDeObservar(Observador o) {
        observadores.remove(o);
    }

    public synchronized List<MovimientoCaja> getLista() {
        return lista;
    }

    public void registrar(MovimientoCaja m) {
        if (m == null) return;
        List<MovimientoCaja> nueva;
        synchronized (this) {
            for (MovimientoCaja x : lista) if (x.id.equals(m.id)) return;
            List<MovimientoCaja> copia = new ArrayList<>(lista.size() + 1);
            copia.add(m);
            copia.addAll(lista.size() >= MAX_GUARDADOS ? lista.subList(0, MAX_GUARDADOS - 1) : lista);
            lista = nueva = Collections.unmodifiableList(copia);
            if (usuarioId != null && almacen != null) almacen.guardar(usuarioId, aJson(nueva));
        }
        for (Observador o : observadores) o.onCambio(nueva);
    }

    /**
     * Cerrar sesión: se deja de mostrar (la próxima cuenta no ve nada de esta), pero lo guardado
     * queda en el teléfono para cuando ese mismo usuario vuelva a entrar.
     */
    public void cerrarSesion() {
        synchronized (this) {
            usuarioId = null;
            lista = Collections.emptyList();
        }
        for (Observador o : observadores) o.onCambio(Collections.emptyList());
    }

    // ── Serialización (pura, testeable) ─────────────────────────────────────────

    private static final Gson GSON = new Gson();

    static String aJson(List<MovimientoCaja> lista) {
        return GSON.toJson(lista);
    }

    /** Lo guardado ilegible o vacío = sin movimientos (nunca rompe el feed). */
    static List<MovimientoCaja> deJson(String json) {
        if (json == null || json.isEmpty()) return Collections.emptyList();
        try {
            List<MovimientoCaja> leida = GSON.fromJson(json, new TypeToken<List<MovimientoCaja>>() {}.getType());
            if (leida == null) return Collections.emptyList();
            List<MovimientoCaja> validos = new ArrayList<>();
            for (MovimientoCaja m : leida) {
                if (m != null && m.id != null && m.tipo != null && m.monto != null && m.fecha != null) validos.add(m);
            }
            return Collections.unmodifiableList(validos);
        } catch (RuntimeException e) {
            return Collections.emptyList();
        }
    }
}
