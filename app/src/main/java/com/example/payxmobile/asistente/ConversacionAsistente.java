package com.example.payxmobile.asistente;

import android.content.Context;

import com.example.payxmobile.model.AsistenteMensajeRequest;
import com.example.payxmobile.model.AsistenteMensajeRequest.Turno;
import com.example.payxmobile.model.AsistenteRespuestaResponse;
import com.example.payxmobile.network.ApiErrores;
import com.example.payxmobile.network.ApiService;
import com.example.payxmobile.network.EnvioSinReintento;
import com.example.payxmobile.network.RetrofitClient;
import com.example.payxmobile.utils.SessionManager;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * La charla con el asistente de IA (AsistenteChat.jsx de la web). Es una sola para toda la app, así
 * sobrevive a la navegación entre pantallas: cada pantalla privada muestra la misma (ver
 * ui/ChatAsistenteView). El backend no guarda nada, así que:
 * - Se guarda en el teléfono (cifrada, con el id del usuario dueño) para sobrevivir a que se cierre
 *   la app mientras la sesión sigue activa.
 * - Se BORRA al cerrar sesión (manual o vencida) y al iniciar sesión: el próximo que use el teléfono
 *   nunca ve la charla de otro. Si lo guardado es de otro usuario, se descarta.
 * - Cada pedido manda el historial completo, recortado (HistorialAsistente).
 */
public final class ConversacionAsistente {

    /** Mensaje inicial fijo: solo visual, nunca se manda al backend. */
    public static final String SALUDO = "¡Hola! Preguntame lo que quieras sobre tu cuenta (saldo, movimientos, gastos) "
            + "o sobre PayX en general. También puedo dejarte lista una transferencia para que la confirmes vos.";
    public static final String MSG_ERROR_GENERICO = "No se pudo consultar al asistente. Probá de nuevo en un momento.";

    /** Dónde se guarda la charla (en la app: SharedPreferences cifradas). */
    public interface Almacen {
        /** null si no hay nada guardado (o no se pudo leer). */
        String leer();

        void guardar(String contenido);

        void borrar();
    }

    public interface Observador {
        void onCambio(ConversacionAsistente c);
    }

    private static ConversacionAsistente instancia;

    private final Supplier<ApiService> api;
    private final Almacen almacen;
    private final Gson gson = new Gson();
    private final List<Observador> observadores = new CopyOnWriteArrayList<>();

    private volatile List<MensajeChat> mensajes = Collections.emptyList();
    private volatile boolean abierto;
    private volatile boolean enviando;
    private volatile long enviandoDesde;
    private volatile String error;
    private String borrador = "";
    private String usuarioId;
    private boolean cargada;
    // Cambia con "Nueva charla" y al cerrar sesión: una respuesta que llega después se descarta
    private volatile int generacion;
    private Call<AsistenteRespuestaResponse> enCurso;

    /** @param almacen null = solo en memoria (tests). */
    public ConversacionAsistente(Supplier<ApiService> api, Almacen almacen) {
        this.api = api;
        this.almacen = almacen;
    }

    /** La de la app, ya con la charla del usuario de la sesión actual (si había una guardada). */
    public static synchronized ConversacionAsistente get(Context context) {
        Context app = context.getApplicationContext();
        if (instancia == null) {
            instancia = new ConversacionAsistente(() -> RetrofitClient.getServiceAsistente(app),
                    new AlmacenConversacion(app));
        }
        instancia.usarUsuario(new SessionManager(app).getUserId());
        return instancia;
    }

    // ── Observadores ──────────────────────────────────────────────────────────

    public void observar(Observador o) {
        observadores.add(o);
    }

    public void dejarDeObservar(Observador o) {
        observadores.remove(o);
    }

    private void notificar() {
        for (Observador o : observadores) o.onCambio(this);
    }

    // ── Sesión ────────────────────────────────────────────────────────────────

    /**
     * La charla es de ESTE usuario: la primera vez carga lo guardado si es suyo (si es de otro, lo
     * borra), y si antes había otro usuario, empieza de cero.
     */
    public void usarUsuario(String id) {
        if (cargada && Objects.equals(id, usuarioId)) return;
        if (cargada) reiniciar(); // cambió el usuario sin pasar por cerrarSesion
        cargada = true;
        usuarioId = id;
        if (almacen == null || id == null) return;
        Guardado guardado = leerGuardado();
        if (guardado == null) return;
        if (!id.equals(guardado.usuarioId)) {
            almacen.borrar();
            return;
        }
        List<MensajeChat> lista = new ArrayList<>();
        if (guardado.mensajes != null) {
            for (MensajeChat m : guardado.mensajes) {
                if (m != null && m.rol != null && m.texto != null) lista.add(m);
            }
        }
        mensajes = Collections.unmodifiableList(lista);
        abierto = guardado.abierto;
    }

    /** Al cerrar sesión (o iniciar una nueva): nada de la charla anterior queda en memoria ni en disco. */
    public void cerrarSesion() {
        reiniciar();
        cargada = false;
        usuarioId = null;
        if (almacen != null) almacen.borrar();
        notificar();
    }

    private void reiniciar() {
        cancelarEnCurso();
        mensajes = Collections.emptyList();
        abierto = false;
        error = null;
        borrador = "";
    }

    // ── Acciones ──────────────────────────────────────────────────────────────

    public void setAbierto(boolean valor) {
        if (abierto == valor) return;
        abierto = valor;
        guardar();
        notificar();
    }

    /** Lo que está escrito en el campo: se mantiene al cambiar de pantalla (solo en memoria). */
    public void setBorrador(String texto) {
        borrador = texto != null ? texto : "";
    }

    /**
     * Manda un mensaje con el historial hasta ahora. false si no se mandó: ya hay uno en camino, o el
     * texto está vacío o supera los 1000 caracteres.
     */
    public boolean enviar(String texto) {
        String mensaje = HistorialAsistente.mensajeValido(texto);
        if (mensaje == null || enviando) return false;
        List<Turno> historial = HistorialAsistente.recortar(mensajes);
        MensajeChat propio = MensajeChat.delUsuario(mensaje);
        agregar(propio);
        error = null;
        enviando = true;
        enviandoDesde = System.currentTimeMillis();
        borrador = "";
        guardar();
        notificar();

        final int gen = generacion;
        Call<AsistenteRespuestaResponse> call = api.get()
                .enviarMensajeAsistente(new AsistenteMensajeRequest(mensaje, historial));
        enCurso = call;
        call.enqueue(new Callback<AsistenteRespuestaResponse>() {
            @Override
            public void onResponse(Call<AsistenteRespuestaResponse> c, Response<AsistenteRespuestaResponse> response) {
                if (gen != generacion) return;
                AsistenteRespuestaResponse body = response.body();
                if (response.isSuccessful() && body != null && body.getTexto() != null
                        && !body.getTexto().trim().isEmpty()) {
                    terminar(null);
                    agregar(MensajeChat.delAsistente(body.getTexto(), body.getAccionSugerida()));
                    guardar();
                    notificar();
                } else if (response.isSuccessful()) {
                    fallo(propio, ApiErrores.MSG_RESPUESTA_INESPERADA);
                } else {
                    // {"error": "..."} del backend (ej. asistente no configurado), el detalle de "campos" de un
                    // 400 de validación, 429, 5xx; sin texto útil, el genérico
                    fallo(propio, EnvioSinReintento.textoRechazo(ApiErrores.mensaje(response), MSG_ERROR_GENERICO));
                }
            }

            @Override
            public void onFailure(Call<AsistenteRespuestaResponse> c, Throwable t) {
                if (gen != generacion) return;
                // Sin conexión, timeout: consultar no mueve plata, así que se puede reintentar tranquilo
                fallo(propio, ApiErrores.mensajeFallo(t));
            }
        });
        return true;
    }

    /** Vuelve a mandar un mensaje que falló (lo saca de donde estaba y lo manda de nuevo al final). */
    public boolean reintentar(MensajeChat fallido) {
        if (enviando || fallido == null || !fallido.fallido) return false;
        List<MensajeChat> lista = new ArrayList<>(mensajes);
        if (!lista.remove(fallido)) return false;
        mensajes = Collections.unmodifiableList(lista);
        return enviar(fallido.texto);
    }

    /** "Nueva charla": vuelve a dejar solo el saludo. Una respuesta pendiente se descarta. */
    public void nuevaCharla() {
        cancelarEnCurso();
        mensajes = Collections.emptyList();
        error = null;
        guardar();
        notificar();
    }

    private void cancelarEnCurso() {
        generacion++;
        enviando = false;
        if (enCurso != null) {
            enCurso.cancel();
            enCurso = null;
        }
    }

    private void terminar(String mensajeError) {
        enviando = false;
        enCurso = null;
        error = mensajeError;
    }

    /** El mensaje del usuario queda en pantalla (marcado para reintentar) y el error se ve abajo. */
    private void fallo(MensajeChat propio, String mensajeError) {
        terminar(mensajeError);
        List<MensajeChat> lista = new ArrayList<>(mensajes);
        int i = lista.indexOf(propio);
        if (i >= 0) lista.set(i, propio.comoFallido());
        mensajes = Collections.unmodifiableList(lista);
        guardar();
        notificar();
    }

    private void agregar(MensajeChat m) {
        List<MensajeChat> lista = new ArrayList<>(mensajes);
        lista.add(m);
        mensajes = Collections.unmodifiableList(lista);
    }

    // ── Persistencia ──────────────────────────────────────────────────────────

    private static final class Guardado {
        String usuarioId;
        boolean abierto;
        List<MensajeChat> mensajes;
    }

    private void guardar() {
        if (almacen == null || usuarioId == null) return;
        Guardado g = new Guardado();
        g.usuarioId = usuarioId;
        g.abierto = abierto;
        g.mensajes = mensajes;
        almacen.guardar(gson.toJson(g));
    }

    private Guardado leerGuardado() {
        String crudo = almacen.leer();
        if (crudo == null) return null;
        try {
            return gson.fromJson(crudo, Guardado.class);
        } catch (RuntimeException e) {
            almacen.borrar(); // formato viejo o dañado: se empieza de cero
            return null;
        }
    }

    // ── Estado ────────────────────────────────────────────────────────────────

    /** Sin el saludo inicial (ese lo agrega la vista). Inmutable. */
    public List<MensajeChat> getMensajes() { return mensajes; }
    public boolean isAbierto() { return abierto; }
    public boolean isEnviando() { return enviando; }
    /** Cuándo salió el pedido en curso (para avisar si tarda). */
    public long getEnviandoDesde() { return enviandoDesde; }
    public String getError() { return error; }
    public String getBorrador() { return borrador; }
}
