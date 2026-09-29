package com.example.payxmobile.asistente.ui;

import android.app.Activity;
import android.content.Context;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.payxmobile.R;
import com.example.payxmobile.activities.HomeActivity;
import com.example.payxmobile.activities.TransferenciaActivity;
import com.example.payxmobile.asistente.ConversacionAsistente;
import com.example.payxmobile.asistente.HistorialAsistente;
import com.example.payxmobile.asistente.MensajeChat;
import com.example.payxmobile.asistente.TextoAsistente;
import com.example.payxmobile.asistente.TransferenciaSugerida;
import com.example.payxmobile.model.AsistenteRespuestaResponse.AccionSugerida;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.List;

/**
 * Burbuja flotante + panel del chat, encima del contenido de UNA pantalla privada (la instala
 * InstaladorChatAsistente). El estado (abierto/cerrado, mensajes, lo escrito) vive en
 * ConversacionAsistente, así que al cambiar de pantalla la nueva muestra exactamente lo mismo.
 */
final class ChatAsistenteView implements ConversacionAsistente.Observador {

    private static final long AVISO_DEMORA_MS = 8000;
    private static final int CONTADOR_DESDE = 900;

    private final Activity activity;
    private final ConversacionAsistente conversacion;
    private final View raiz, panel;
    private final ImageButton btnBurbuja, btnEnviar;
    private final ScrollView scroll;
    private final LinearLayout lista;
    private final TextView tvError, tvContador;
    private final EditText etMensaje;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final OnBackPressedCallback alVolver;

    // Dónde dejó el usuario la burbuja: de qué lado y cuántos dp más arriba de su lugar de siempre
    private static final String PREFS_UI = "payx_asistente_ui";
    private static final String KEY_BURBUJA_IZQUIERDA = "burbuja_izquierda";
    private static final String KEY_BURBUJA_ALTURA_DP = "burbuja_altura_dp";
    private boolean burbujaIzquierda;
    private int burbujaAlturaDp;
    private final int toqueMinimo;
    private float toqueX, toqueY;
    private boolean arrastrando;
    // Límites que calculó ubicar(): la burbuja no se arrastra debajo del menú ni encima de la barra de estado
    private int limiteArriba, limiteAbajo;

    private BottomNavigationView navInferior;
    private WindowInsetsCompat insets;
    private int modoTecladoOriginal = -1;
    private boolean activa;
    // Qué se dibujó la última vez: la lista se rearma solo si cambió
    private List<MensajeChat> mensajesDibujados;
    private boolean escribiendoDibujado;
    private boolean abiertoDibujado;
    private boolean pegadoAlFinal = true;
    private TextView tvEscribiendo;
    private int puntos;

    private ChatAsistenteView(Activity activity, ViewGroup contenedor) {
        this.activity = activity;
        this.conversacion = ConversacionAsistente.get(activity);
        raiz = LayoutInflater.from(activity).inflate(R.layout.overlay_asistente, contenedor, false);
        panel = raiz.findViewById(R.id.panelChat);
        btnBurbuja = raiz.findViewById(R.id.btnBurbujaChat);
        btnEnviar = raiz.findViewById(R.id.btnEnviarChat);
        scroll = raiz.findViewById(R.id.scrollChat);
        lista = raiz.findViewById(R.id.listaChat);
        tvError = raiz.findViewById(R.id.tvErrorChat);
        tvContador = raiz.findViewById(R.id.tvContadorChat);
        etMensaje = raiz.findViewById(R.id.etMensajeChat);
        contenedor.addView(raiz);
        navInferior = buscarNavInferior(contenedor);
        toqueMinimo = ViewConfiguration.get(activity).getScaledTouchSlop();

        alVolver = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                conversacion.setAbierto(false);
            }
        };
        if (activity instanceof ComponentActivity) {
            ((ComponentActivity) activity).getOnBackPressedDispatcher().addCallback((ComponentActivity) activity, alVolver);
        }
        configurar();
    }

    /** Agrega el chat encima del contenido de la pantalla (una sola vez por pantalla). */
    static ChatAsistenteView instalar(Activity activity) {
        FrameLayout contenido = activity.findViewById(android.R.id.content);
        if (contenido == null) return null;
        Object previo = contenido.getTag(R.id.chatAsistenteRaiz);
        if (previo instanceof ChatAsistenteView) return (ChatAsistenteView) previo;
        ChatAsistenteView chat = new ChatAsistenteView(activity, contenido);
        contenido.setTag(R.id.chatAsistenteRaiz, chat);
        return chat;
    }

    // ── Ciclo de vida (lo llama InstaladorChatAsistente) ──────────────────────

    void alIniciar() {
        activa = true;
        // Pudo haberse movido en otra pantalla mientras esta no se veía
        SharedPreferences prefs = activity.getSharedPreferences(PREFS_UI, Context.MODE_PRIVATE);
        burbujaIzquierda = prefs.getBoolean(KEY_BURBUJA_IZQUIERDA, false);
        burbujaAlturaDp = prefs.getInt(KEY_BURBUJA_ALTURA_DP, 0);
        conversacion.observar(this);
        String borrador = conversacion.getBorrador();
        if (!borrador.contentEquals(etMensaje.getText())) {
            etMensaje.setText(borrador);
            etMensaje.setSelection(etMensaje.length());
        }
        mensajesDibujados = null; // puede haber cambiado mientras la pantalla no se veía
        onCambio(conversacion);
    }

    void alDetener() {
        activa = false;
        conversacion.dejarDeObservar(this);
        handler.removeCallbacksAndMessages(null);
        restaurarModoTeclado();
    }

    // ── Configuración ─────────────────────────────────────────────────────────

    private void configurar() {
        btnBurbuja.setOnClickListener(v -> conversacion.setAbierto(!conversacion.isAbierto()));
        btnBurbuja.setOnTouchListener(this::alTocarBurbuja);
        raiz.findViewById(R.id.btnCerrarChat).setOnClickListener(v -> conversacion.setAbierto(false));
        raiz.findViewById(R.id.btnNuevaCharla).setOnClickListener(v -> conversacion.nuevaCharla());
        btnEnviar.setOnClickListener(v -> enviar());

        // "Enter envía": el campo es multilínea (el texto largo se ve en varias líneas) pero el teclado
        // lo ve como una línea, así muestra la tecla "Enviar". Con teclado físico, Shift+Enter hace un
        // salto de línea.
        etMensaje.setRawInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        etMensaje.setImeOptions(EditorInfo.IME_ACTION_SEND);
        etMensaje.setOnEditorActionListener((v, actionId, evento) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                enviar();
                return true;
            }
            if (evento != null && evento.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                if (evento.isShiftPressed()) return false; // salto de línea normal
                if (evento.getAction() == KeyEvent.ACTION_DOWN) enviar();
                return true;
            }
            return false;
        });
        etMensaje.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void afterTextChanged(Editable s) {
                conversacion.setBorrador(s.toString());
                renderEntrada();
            }
        });

        ViewCompat.setOnApplyWindowInsetsListener(raiz, (v, nuevos) -> {
            insets = nuevos;
            ubicar();
            return nuevos;
        });
        // Después de CADA pasada de layout de la pantalla (menú inferior que aparece o cambia de alto,
        // panel que se abre, rotación...). ubicar() solo toca los márgenes si cambiaron, así que no se
        // vuelve a disparar sola.
        raiz.getViewTreeObserver().addOnGlobalLayoutListener(this::ubicar);
        // Como cualquier chat: mientras se está viendo el final, cualquier cambio de tamaño (mensaje nuevo,
        // panel que se abre, teclado) lo mantiene a la vista. Si el usuario subió a leer, no se lo mueve.
        View.OnLayoutChangeListener seguirAlFinal = (v, l, t, r, b, ol, ot, or, ob) -> {
            if (pegadoAlFinal && b > t) scroll.post(() -> scroll.scrollTo(0, lista.getHeight()));
        };
        lista.addOnLayoutChangeListener(seguirAlFinal);
        scroll.addOnLayoutChangeListener(seguirAlFinal);
        scroll.setOnScrollChangeListener((v, x, y, ox, oy) -> {
            if (y == oy) return;
            pegadoAlFinal = y + scroll.getHeight() - scroll.getPaddingTop() - scroll.getPaddingBottom()
                    >= lista.getHeight() - dp(24);
        });
    }

    // ── Burbuja arrastrable ───────────────────────────────────────────────────

    /**
     * Con el chat cerrado la burbuja se arrastra a cualquier lado; al soltarla se pega al borde más
     * cercano y queda a esa altura (en todas las pantallas). Un toque sin arrastrar la abre como siempre.
     */
    @SuppressLint("ClickableViewAccessibility") // el toque sin arrastre llama a performClick()
    private boolean alTocarBurbuja(View v, MotionEvent e) {
        if (conversacion.isAbierto()) return false; // abierta es la X: solo cierra
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                toqueX = e.getRawX();
                toqueY = e.getRawY();
                arrastrando = false;
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dx = e.getRawX() - toqueX, dy = e.getRawY() - toqueY;
                if (!arrastrando && Math.hypot(dx, dy) < toqueMinimo) return true;
                arrastrando = true;
                v.setTranslationX(limitar(dx, -v.getLeft(), raiz.getWidth() - v.getRight()));
                v.setTranslationY(limitar(dy, limiteArriba - v.getTop(), limiteAbajo - v.getBottom()));
                return true;
            }
            case MotionEvent.ACTION_UP:
                if (arrastrando) soltarBurbuja(v);
                else v.performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (arrastrando) soltarBurbuja(v);
                return true;
            default:
                return true;
        }
    }

    private void soltarBurbuja(View v) {
        arrastrando = false;
        float x = v.getLeft() + v.getTranslationX();
        float y = v.getTop() + v.getTranslationY();
        burbujaIzquierda = x + v.getWidth() / 2f < raiz.getWidth() / 2f;
        // Cuánto más arriba de su lugar de siempre quedó (el lugar de siempre es limiteAbajo)
        float alturaPx = Math.max(0, limiteAbajo - (y + v.getHeight()));
        burbujaAlturaDp = Math.round(alturaPx / activity.getResources().getDisplayMetrics().density);
        activity.getSharedPreferences(PREFS_UI, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_BURBUJA_IZQUIERDA, burbujaIzquierda)
                .putInt(KEY_BURBUJA_ALTURA_DP, burbujaAlturaDp)
                .apply();

        // Se mueve al lugar nuevo y la traslación lo deja visualmente donde se soltó; después se anima
        // hasta el borde
        ubicar();
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) v.getLayoutParams();
        float nuevoX = burbujaIzquierda ? lp.getMarginStart() : raiz.getWidth() - lp.getMarginEnd() - v.getWidth();
        float nuevoY = raiz.getHeight() - lp.bottomMargin - v.getHeight();
        v.setTranslationX(x - nuevoX);
        v.setTranslationY(y - nuevoY);
        v.animate().translationX(0).translationY(0).setDuration(200).start();
    }

    private static float limitar(float valor, float minimo, float maximo) {
        return Math.max(minimo, Math.min(maximo, valor));
    }

    private void enviar() {
        if (conversacion.enviar(etMensaje.getText().toString())) {
            etMensaje.setText("");
        }
    }

    private void completarTransferencia(AccionSugerida accion) {
        TransferenciaSugerida t = TransferenciaSugerida.de(accion);
        if (t == null) return;
        conversacion.setAbierto(false);
        ocultarTeclado();
        // Se vuelve al Inicio (cerrando lo que haya encima) y arriba se abre el formulario de siempre,
        // ya completado: el usuario lo revisa y confirma con el flujo normal (Continuar -> Confirmar).
        Intent inicio = new Intent(activity, HomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        Intent formulario = new Intent(activity, TransferenciaActivity.class)
                .putExtra(TransferenciaActivity.EXTRA_MONEDA, t.moneda)
                .putExtra(TransferenciaActivity.EXTRA_DESTINATARIO, t.destinatario)
                .putExtra(TransferenciaActivity.EXTRA_MONTO, t.monto)
                .putExtra(TransferenciaActivity.EXTRA_MOTIVO, t.motivo)
                .putExtra(TransferenciaActivity.EXTRA_TIPO, t.tipo);
        activity.startActivities(new Intent[]{inicio, formulario});
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void onCambio(ConversacionAsistente c) {
        if (!activa) return;
        boolean abierto = c.isAbierto();
        if (abierto && !abiertoDibujado) irAlFinal();
        abiertoDibujado = abierto;
        panel.setVisibility(abierto ? View.VISIBLE : View.GONE);
        btnBurbuja.setImageResource(abierto ? R.drawable.ic_close : R.drawable.ic_sparkles);
        btnBurbuja.setBackgroundResource(abierto ? R.drawable.bg_chat_fab_activo : R.drawable.bg_chat_fab);
        btnBurbuja.setContentDescription(abierto ? "Cerrar asistente" : "Abrir asistente");
        alVolver.setEnabled(abierto);
        if (abierto) usarModoTecladoResize();
        else {
            ocultarTeclado();
            restaurarModoTeclado();
        }

        if (c.getMensajes() != mensajesDibujados || c.isEnviando() != escribiendoDibujado) {
            dibujarMensajes(c.getMensajes(), c.isEnviando());
        }
        String error = c.getError();
        tvError.setVisibility(error != null ? View.VISIBLE : View.GONE);
        tvError.setText(error);
        renderEntrada();
        ubicar();
    }

    private void renderEntrada() {
        int largo = etMensaje.length();
        btnEnviar.setEnabled(!conversacion.isEnviando()
                && HistorialAsistente.mensajeValido(etMensaje.getText().toString()) != null);
        tvContador.setVisibility(largo >= CONTADOR_DESDE ? View.VISIBLE : View.GONE);
        tvContador.setText(largo + "/" + HistorialAsistente.MAX_CARACTERES_MENSAJE);
    }

    private void dibujarMensajes(List<MensajeChat> mensajes, boolean enviando) {
        mensajesDibujados = mensajes;
        escribiendoDibujado = enviando;
        lista.removeAllViews();
        lista.addView(fila(MensajeChat.delAsistente(ConversacionAsistente.SALUDO, null)));
        for (MensajeChat m : mensajes) lista.addView(fila(m));
        handler.removeCallbacks(animarEscribiendo);
        tvEscribiendo = null;
        if (enviando) {
            View fila = fila(MensajeChat.delAsistente("Escribiendo", null));
            tvEscribiendo = fila.findViewById(R.id.tvTextoChat);
            tvEscribiendo.setTextIsSelectable(false);
            tvEscribiendo.setTextColor(ContextCompat.getColor(activity, R.color.text_secondary));
            lista.addView(fila);
            puntos = 0;
            animarEscribiendo.run();
        }
        irAlFinal();
    }

    /** Muestra el último mensaje en cuanto la lista tenga su tamaño real (con el panel oculto mide 0). */
    private void irAlFinal() {
        pegadoAlFinal = true;
        scroll.post(() -> scroll.scrollTo(0, lista.getHeight()));
    }

    /** "Escribiendo..." con puntos animados; si tarda, aclara que puede demorar unos segundos. */
    private final Runnable animarEscribiendo = new Runnable() {
        @Override
        public void run() {
            if (tvEscribiendo == null || !activa) return;
            puntos = (puntos + 1) % 4;
            StringBuilder texto = new StringBuilder("Escribiendo");
            for (int i = 0; i < 3; i++) texto.append(i < puntos ? '.' : ' ');
            if (System.currentTimeMillis() - conversacion.getEnviandoDesde() > AVISO_DEMORA_MS) {
                texto.append("\nEstoy revisando tus datos, puede tardar unos segundos");
            }
            tvEscribiendo.setText(texto);
            handler.postDelayed(this, 400);
        }
    };

    private View fila(MensajeChat m) {
        View fila = LayoutInflater.from(activity).inflate(R.layout.item_chat_mensaje, lista, false);
        LinearLayout contenedor = (LinearLayout) fila;
        contenedor.setGravity(m.esDelUsuario() ? Gravity.END : Gravity.START);
        TextView tvTexto = fila.findViewById(R.id.tvTextoChat);
        tvTexto.setText(m.esDelUsuario() ? m.texto : conNegritas(m.texto));
        tvTexto.setBackgroundResource(!m.esDelUsuario() ? R.drawable.bg_chat_burbuja_asistente
                : m.fallido ? R.drawable.bg_chat_burbuja_fallida : R.drawable.bg_chat_burbuja_usuario);

        if (m.fallido) {
            TextView tvFallido = fila.findViewById(R.id.tvFallidoChat);
            tvFallido.setVisibility(View.VISIBLE);
            tvFallido.setOnClickListener(v -> conversacion.reintentar(m));
        }

        TransferenciaSugerida transferencia = m.tieneTransferencia() ? TransferenciaSugerida.de(m.accion) : null;
        if (m.tieneTransferencia()) {
            View tarjeta = fila.findViewById(R.id.tarjetaAccionChat);
            tarjeta.setVisibility(View.VISIBLE);
            ((TextView) fila.findViewById(R.id.tvMontoAccion)).setText(TransferenciaSugerida.montoFormateado(m.accion));
            String nombre = m.accion.getNombreResuelto() != null ? m.accion.getNombreResuelto() : m.accion.getDestinatario();
            ((TextView) fila.findViewById(R.id.tvDestinoAccion)).setText("a " + nombre);
            TextView tvMotivo = fila.findViewById(R.id.tvMotivoAccion);
            if (m.accion.getMotivo() != null && !m.accion.getMotivo().trim().isEmpty()) {
                tvMotivo.setVisibility(View.VISIBLE);
                tvMotivo.setText(m.accion.getMotivo());
            }
            TextView btn = fila.findViewById(R.id.btnCompletarTransferencia);
            Drawable icono = ContextCompat.getDrawable(activity, R.drawable.ic_send);
            if (icono != null) {
                icono = icono.mutate();
                icono.setTint(ContextCompat.getColor(activity, R.color.white));
                icono.setBounds(0, 0, dp(16), dp(16));
                btn.setCompoundDrawablesRelative(icono, null, null, null);
                btn.setCompoundDrawablePadding(dp(8));
            }
            // Sin moneda conocida o destinatario no hay formulario que abrir: solo se muestra la tarjeta
            btn.setVisibility(transferencia != null ? View.VISIBLE : View.GONE);
            btn.setOnClickListener(v -> completarTransferencia(m.accion));
        }
        return fila;
    }

    private static CharSequence conNegritas(String crudo) {
        TextoAsistente t = TextoAsistente.de(crudo);
        if (t.negritas.isEmpty()) return t.texto;
        SpannableString s = new SpannableString(t.texto);
        for (int[] r : t.negritas) {
            s.setSpan(new StyleSpan(Typeface.BOLD), r[0], r[1], Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return s;
    }

    // ── Ubicación: barras del sistema, menú inferior y teclado ───────────────

    private void ubicar() {
        int arriba = 0, abajoSistema = 0, izquierda = 0, derecha = 0, teclado = 0;
        boolean tecladoVisible = false;
        if (insets != null) {
            Insets sistema = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            arriba = sistema.top;
            abajoSistema = sistema.bottom;
            izquierda = sistema.left;
            derecha = sistema.right;
            tecladoVisible = insets.isVisible(WindowInsetsCompat.Type.ime());
            teclado = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
        }
        int base = tecladoVisible ? teclado : Math.max(abajoSistema, alturaNavInferior());

        // Con el teclado arriba la burbuja se esconde: no tapa el formulario de la pantalla y, con el
        // panel abierto, le deja todo el lugar (se cierra con la X o con "atrás")
        btnBurbuja.setVisibility(tecladoVisible ? View.GONE : View.VISIBLE);
        int margenBase = base + dp(16);
        limiteAbajo = raiz.getHeight() - margenBase;
        limiteArriba = arriba + dp(16);
        // Abierta (la X) va a su lugar de siempre, justo debajo del panel; cerrada, donde la dejó el
        // usuario, sin pasarse de la barra de estado
        int margenBurbuja = margenBase;
        if (!conversacion.isAbierto() && burbujaAlturaDp > 0) {
            int maximo = raiz.getHeight() - limiteArriba - dp(56);
            margenBurbuja = Math.max(margenBase, Math.min(margenBase + dp(burbujaAlturaDp), maximo));
        }
        int gravedad = Gravity.BOTTOM | (burbujaIzquierda ? Gravity.START : Gravity.END);
        FrameLayout.LayoutParams lpBurbuja = (FrameLayout.LayoutParams) btnBurbuja.getLayoutParams();
        if (lpBurbuja.bottomMargin != margenBurbuja || lpBurbuja.gravity != gravedad
                || lpBurbuja.getMarginStart() != dp(16) + izquierda || lpBurbuja.getMarginEnd() != dp(16) + derecha) {
            lpBurbuja.bottomMargin = margenBurbuja;
            lpBurbuja.gravity = gravedad;
            lpBurbuja.setMarginStart(dp(16) + izquierda);
            lpBurbuja.setMarginEnd(dp(16) + derecha);
            btnBurbuja.setLayoutParams(lpBurbuja);
        }

        FrameLayout.LayoutParams lpPanel = (FrameLayout.LayoutParams) panel.getLayoutParams();
        int arribaPanel = arriba + dp(12);
        int abajoPanel = tecladoVisible ? base + dp(8) : margenBase + dp(56 + 12);
        if (lpPanel.topMargin != arribaPanel || lpPanel.bottomMargin != abajoPanel) {
            lpPanel.topMargin = arribaPanel;
            lpPanel.bottomMargin = abajoPanel;
            panel.setLayoutParams(lpPanel);
        }
    }

    /** Cuánto ocupa el menú inferior (Inicio/Actividad/...) desde abajo, para que la burbuja no lo tape. */
    private int alturaNavInferior() {
        // La referencia puede haber quedado de una vista ya descartada: se vuelve a buscar
        if (navInferior != null && !navInferior.isAttachedToWindow() && raiz.getParent() instanceof ViewGroup) {
            navInferior = buscarNavInferior((ViewGroup) raiz.getParent());
        }
        if (navInferior == null || !navInferior.isShown() || navInferior.getHeight() == 0) return 0;
        int[] nav = new int[2];
        int[] propia = new int[2];
        navInferior.getLocationInWindow(nav);
        raiz.getLocationInWindow(propia);
        return Math.max(0, propia[1] + raiz.getHeight() - nav[1]);
    }

    private static BottomNavigationView buscarNavInferior(ViewGroup grupo) {
        for (int i = 0; i < grupo.getChildCount(); i++) {
            View hijo = grupo.getChildAt(i);
            if (hijo instanceof BottomNavigationView) return (BottomNavigationView) hijo;
            if (hijo instanceof ViewGroup) {
                BottomNavigationView encontrado = buscarNavInferior((ViewGroup) hijo);
                if (encontrado != null) return encontrado;
            }
        }
        return null;
    }

    // ── Teclado ───────────────────────────────────────────────────────────────

    /** Con el panel abierto, el teclado achica el espacio en vez de desplazar toda la pantalla. */
    private void usarModoTecladoResize() {
        if (modoTecladoOriginal != -1) return;
        WindowManager.LayoutParams lp = activity.getWindow().getAttributes();
        modoTecladoOriginal = lp.softInputMode;
        activity.getWindow().setSoftInputMode((lp.softInputMode & ~WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST)
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    private void restaurarModoTeclado() {
        if (modoTecladoOriginal == -1) return;
        activity.getWindow().setSoftInputMode(modoTecladoOriginal);
        modoTecladoOriginal = -1;
    }

    private void ocultarTeclado() {
        if (!etMensaje.hasFocus()) return;
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(etMensaje.getWindowToken(), 0);
        etMensaje.clearFocus();
    }

    private int dp(int v) {
        return Math.round(v * activity.getResources().getDisplayMetrics().density);
    }
}
