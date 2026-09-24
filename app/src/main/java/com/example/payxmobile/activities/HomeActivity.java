package com.example.payxmobile.activities;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.Typeface;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.viewpager2.widget.ViewPager2;

import com.example.payxmobile.R;
import com.example.payxmobile.actividad.VistaMovimientos;
import com.example.payxmobile.model.PerfilResponse;
import com.example.payxmobile.model.SinLeerResponse;
import com.example.payxmobile.model.TransferenciaResponse;
import com.example.payxmobile.network.RetrofitClient;
import com.example.payxmobile.saldos.EstadoSaldos;
import com.example.payxmobile.saldos.SaldosRepository;
import com.example.payxmobile.saldos.VistaSaldo;
import com.example.payxmobile.transferencias.TransferenciasRepository;
import com.example.payxmobile.transferencias.ui.DetalleTransferenciaSheet;
import com.example.payxmobile.transferencias.ui.FilaTransferenciaVista;
import com.example.payxmobile.utils.Avatar;
import com.example.payxmobile.utils.NavegacionInferior;
import com.example.payxmobile.utils.Saludo;
import com.example.payxmobile.utils.SesionUtils;
import com.example.payxmobile.utils.SessionManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.math.BigDecimal;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HomeActivity extends AppCompatActivity {

    private static final int POS_PESOS = 0;
    private static final int POS_DOLARES = 1;
    private static final int POS_CRIPTO = 2;

    private SessionManager sessionManager;
    private View badgeNotificaciones;

    private TextView tabPesos, tabDolares, tabCripto;
    private View dot0, dot1, dot2;
    private ViewPager2 vpSaldo;
    private SaldoPagerAdapter pagerAdapter;

    private View overlayMenu, panelMenu, scrimMenu;
    private OnBackPressedCallback cerrarMenuAlVolver;
    private BottomNavigationView bottomNav;

    private static final String ESTADO_SALDO_VISIBLE = "saldo_visible";
    private static final String ESTADO_CRIPTO = "cripto_seleccionada";

    // Saldos y cotizaciones: siempre del repositorio compartido (nunca un 0 inventado)
    private SaldosRepository saldosRepository;
    private EstadoSaldos estadoSaldos;
    private final SaldosRepository.Observador observadorSaldos = this::onEstadoSaldos;
    private SwipeRefreshLayout swipeRefresh;
    private boolean pullEnCurso = false;
    private boolean saldoVisible = true;
    private String criptoSeleccionada = PerfilResponse.CRIPTOS[0];
    private String fotoMostrada;

    // Últimas actividades: mismo repositorio (y mismo polling) que "Mis movimientos"
    private TransferenciasRepository transferenciasRepo;
    private final TransferenciasRepository.Observador observadorActividades = this::onActividades;
    private DetalleTransferenciaSheet detalleSheet;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        sessionManager = new SessionManager(this);

        if (!sessionManager.isLoggedIn()) {
            irALogin();
            return;
        }
        if (!sessionManager.tieneSesionVigente()) {
            SesionUtils.sesionVencida(this);
            return;
        }

        // El ojito y la cripto elegida sobreviven a rotar / plegar el teléfono
        if (savedInstanceState != null) {
            saldoVisible = savedInstanceState.getBoolean(ESTADO_SALDO_VISIBLE, true);
            criptoSeleccionada = savedInstanceState.getString(ESTADO_CRIPTO, PerfilResponse.CRIPTOS[0]);
        }
        saldosRepository = SaldosRepository.get(this);
        transferenciasRepo = TransferenciasRepository.get(this);
        detalleSheet = new DetalleTransferenciaSheet(this);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        swipeRefresh.setColorSchemeResources(R.color.payx_orange);
        swipeRefresh.setOnRefreshListener(() -> {
            pullEnCurso = true;
            saldosRepository.refrescarTodo();
            transferenciasRepo.refrescar();
            onEstadoSaldos(saldosRepository.getEstado());
        });

        badgeNotificaciones = findViewById(R.id.badgeNotificaciones);
        tabPesos = findViewById(R.id.tabPesos);
        tabDolares = findViewById(R.id.tabDolares);
        tabCripto = findViewById(R.id.tabCripto);
        dot0 = findViewById(R.id.dot0);
        dot1 = findViewById(R.id.dot1);
        dot2 = findViewById(R.id.dot2);
        vpSaldo = findViewById(R.id.vpSaldo);
        overlayMenu = findViewById(R.id.overlayMenu);
        panelMenu = findViewById(R.id.panelMenu);
        scrimMenu = findViewById(R.id.scrimMenu);

        configurarSaludo();
        configurarCardSaldo();
        configurarBottomNav();
        configurarAccionesHome();
        configurarMenuLateral();

        findViewById(R.id.btnNotificaciones).setOnClickListener(v -> {
            startActivity(new Intent(this, NotificacionesActivity.class));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });

        findViewById(R.id.btnMenu).setOnClickListener(v -> abrirMenu());

        findViewById(R.id.btnQuieroInvertir).setOnClickListener(v -> {
            startActivity(new Intent(this, PlazoFijoActivity.class));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });
        findViewById(R.id.tvConsultarTodas).setOnClickListener(v -> abrirMovimientos());
        findViewById(R.id.btnReintentarActividades).setOnClickListener(v -> transferenciasRepo.refrescar());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (bottomNav == null) return;
        // La app pudo quedar en segundo plano más de lo que dura el token: se chequea el "exp" sin llamar al backend
        if (!sessionManager.tieneSesionVigente()) {
            SesionUtils.sesionVencida(this);
            return;
        }
        bottomNav.setSelectedItemId(R.id.nav_inicio);
        mostrarAvatarMenu();
        actualizarBadge();
    }

    // Auto-refresco solo en primer plano: arranca (y refresca YA) en onStart, se corta en onStop
    @Override
    protected void onStart() {
        super.onStart();
        if (saldosRepository == null || !sessionManager.tieneSesionVigente()) return;
        saldosRepository.observar(observadorSaldos);
        saldosRepository.iniciarAutoRefresco();
        transferenciasRepo.observar(observadorActividades);
        transferenciasRepo.iniciarAutoRefresco();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (saldosRepository == null) return;
        saldosRepository.detenerAutoRefresco();
        saldosRepository.dejarDeObservar(observadorSaldos);
        transferenciasRepo.detenerAutoRefresco();
        transferenciasRepo.dejarDeObservar(observadorActividades);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(ESTADO_SALDO_VISIBLE, saldoVisible);
        outState.putString(ESTADO_CRIPTO, criptoSeleccionada);
    }

    // ── Saludo ────────────────────────────────────────────────────────────────

    private void configurarSaludo() {
        String saludo = "Hola, " + Saludo.primerNombre(sessionManager.getNombreCompleto());

        TextView tvBienvenida = findViewById(R.id.tvBienvenida);
        tvBienvenida.setText(saludo);

        TextView tvMenuSaludo = findViewById(R.id.tvMenuSaludo);
        tvMenuSaludo.setText(saludo);
    }

    // ── Saldos (repositorio compartido) ─────────────────────────────────────────

    private void onEstadoSaldos(EstadoSaldos estado) {
        if (isFinishing()) return;
        if (estado.sesionInvalida) {
            SesionUtils.sesionInvalida(this);
            return;
        }
        estadoSaldos = estado;
        if (estado.perfil != null) {
            String foto = estado.perfil.getFotoPerfilUrl();
            if (foto != null ? !foto.equals(fotoMostrada) : fotoMostrada != null) {
                fotoMostrada = foto;
                sessionManager.actualizarFotoPerfilUrl(foto);
                mostrarAvatarMenu();
            }
        }
        if (pullEnCurso && !estado.cargandoSaldo && !estado.cargandoCotizaciones) {
            pullEnCurso = false;
            swipeRefresh.setRefreshing(false);
        }
        if (pagerAdapter != null) pagerAdapter.actualizarSaldos();
    }

    // ── Card de saldo: tabs + carrusel (Pesos / Dólares / Cripto) ───────────────

    private void configurarCardSaldo() {
        pagerAdapter = new SaldoPagerAdapter();
        vpSaldo.setAdapter(pagerAdapter);
        vpSaldo.setOffscreenPageLimit(3);
        vpSaldo.setPageTransformer((page, position) -> {
            float scale = 1f - (Math.abs(position) * 0.08f);
            page.setScaleY(Math.max(scale, 0.9f));
            page.setAlpha(1f - Math.min(Math.abs(position), 0.6f));
        });

        tabPesos.setOnClickListener(v -> vpSaldo.setCurrentItem(POS_PESOS));
        tabDolares.setOnClickListener(v -> vpSaldo.setCurrentItem(POS_DOLARES));
        tabCripto.setOnClickListener(v -> vpSaldo.setCurrentItem(POS_CRIPTO));

        vpSaldo.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                actualizarTabs(position);
                pagerAdapter.ajustarAlturaDe(position);
                vpSaldo.requestLayout();
            }
        });

        actualizarTabs(POS_PESOS);
    }

    private void actualizarTabs(int position) {
        marcarTab(tabPesos, position == POS_PESOS, R.color.color_pesos);
        marcarTab(tabDolares, position == POS_DOLARES, R.color.color_dolares);
        marcarTab(tabCripto, position == POS_CRIPTO, R.color.color_cripto);

        marcarDot(dot0, position == POS_PESOS);
        marcarDot(dot1, position == POS_DOLARES);
        marcarDot(dot2, position == POS_CRIPTO);
    }

    private void marcarTab(TextView tab, boolean activo, int colorActivo) {
        tab.setTextColor(getColor(activo ? colorActivo : R.color.text_secondary));
        tab.setTypeface(null, activo ? Typeface.BOLD : Typeface.NORMAL);
    }

    private void marcarDot(View dot, boolean activo) {
        ViewGroup.LayoutParams params = dot.getLayoutParams();
        params.width = dpA(activo ? 18 : 6);
        dot.setLayoutParams(params);
        dot.setBackgroundResource(activo ? R.drawable.bg_dot_activo : R.drawable.bg_dot_inactivo);
    }

    private int dpA(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    // ── Notificaciones sin leer ──────────────────────────────────────────────

    private void actualizarBadge() {
        RetrofitClient.getService(this).contarSinLeer()
                .enqueue(new Callback<SinLeerResponse>() {
                    @Override
                    public void onResponse(Call<SinLeerResponse> call, Response<SinLeerResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            long cantidad = response.body().getCantidad();
                            badgeNotificaciones.setVisibility(cantidad > 0 ? View.VISIBLE : View.GONE);
                        }
                    }

                    @Override
                    public void onFailure(Call<SinLeerResponse> call, Throwable t) {}
                });
    }

    // ── Menú lateral (hamburguesa) ────────────────────────────────────────────

    private void configurarMenuLateral() {
        int paddingInicialInferior = panelMenu.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(panelMenu, (v, insets) -> {
            int abajo = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(),
                    paddingInicialInferior + abajo);
            return insets;
        });

        scrimMenu.setOnClickListener(v -> cerrarMenu());
        findViewById(R.id.btnCerrarMenu).setOnClickListener(v -> cerrarMenu());

        findViewById(R.id.itemPerfil).setOnClickListener(v -> {
            cerrarMenu();
            startActivity(new Intent(HomeActivity.this, PerfilActivity.class));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });
        vincularItemMenuLateral(R.id.itemEstadisticas);

        findViewById(R.id.itemMovimientos).setOnClickListener(v -> {
            cerrarMenu();
            startActivity(new Intent(HomeActivity.this, MovimientosActivity.class));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });

        findViewById(R.id.itemPlazoFijo).setOnClickListener(v -> {
            cerrarMenu();
            startActivity(new Intent(HomeActivity.this, PlazoFijoActivity.class));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });

        findViewById(R.id.itemCajaAhorro).setOnClickListener(v -> {
            cerrarMenu();
            startActivity(new Intent(HomeActivity.this, CajasAhorroActivity.class));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });

        findViewById(R.id.itemTarjeta).setOnClickListener(v -> {
            cerrarMenu();
            abrirTarjetaVirtual();
        });

        findViewById(R.id.itemServicios).setOnClickListener(v -> {
            cerrarMenu();
            abrirServicios();
        });

        findViewById(R.id.itemCerrarSesion).setOnClickListener(v -> {
            cerrarMenu();
            confirmarCerrarSesion();
        });

        cerrarMenuAlVolver = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                cerrarMenu();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, cerrarMenuAlVolver);
    }

    private void vincularItemMenuLateral(int id) {
        findViewById(id).setOnClickListener(v -> {
            cerrarMenu();
            mostrarProximamente();
        });
    }

    private void abrirMenu() {
        overlayMenu.setVisibility(View.VISIBLE);
        cerrarMenuAlVolver.setEnabled(true);

        scrimMenu.setAlpha(0f);
        scrimMenu.animate().alpha(1f).setDuration(220).start();

        panelMenu.setTranslationX(dpA(300));
        panelMenu.animate()
                .translationX(0f)
                .setDuration(280)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private void cerrarMenu() {
        cerrarMenuAlVolver.setEnabled(false);

        panelMenu.animate()
                .translationX(dpA(300))
                .setDuration(240)
                .setInterpolator(new AccelerateInterpolator())
                .start();

        scrimMenu.animate()
                .alpha(0f)
                .setDuration(240)
                .withEndAction(() -> overlayMenu.setVisibility(View.GONE))
                .start();
    }

    // ── Acciones rápidas ("Qué querés hacer") ────────────────────────────────

    private void configurarAccionesHome() {
        View accionTransferir = findViewById(R.id.accionTransferir);
        configurarAccion(accionTransferir, R.drawable.ic_send, "Transferir");
        accionTransferir.setOnClickListener(v -> abrirTransferencia("ARS"));

        View accionTransferenciaDolares = findViewById(R.id.accionTransferenciaDolares);
        configurarAccion(accionTransferenciaDolares, R.drawable.ic_dollar_sign, "Transferencia en dólares");
        accionTransferenciaDolares.setOnClickListener(v -> abrirTransferencia("USD"));

        View accionTransferenciaCripto = findViewById(R.id.accionTransferenciaCripto);
        configurarAccion(accionTransferenciaCripto, R.drawable.ic_coins, "Transferencia en cripto");
        accionTransferenciaCripto.setOnClickListener(v -> abrirTransferenciaCripto());
        View accionServicios = findViewById(R.id.accionServicios);
        configurarAccion(accionServicios, R.drawable.ic_zap, "Pagar servicios");
        accionServicios.setOnClickListener(v -> abrirServicios());

        View accionTarjeta = findViewById(R.id.accionTarjeta);
        configurarAccion(accionTarjeta, R.drawable.ic_credit_card, "Tarjeta virtual");
        accionTarjeta.setOnClickListener(v -> abrirTarjetaVirtual());
        configurarAccion(findViewById(R.id.accionEstadisticas), R.drawable.ic_bar_chart, "Estadísticas");
    }

    private void abrirTransferencia(String moneda) {
        Intent intent = new Intent(this, TransferenciaActivity.class);
        intent.putExtra(TransferenciaActivity.EXTRA_MONEDA, moneda);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void abrirTransferenciaCripto() {
        // Misma pantalla de envío, con selector de cripto (BTC por defecto, como la web)
        Intent intent = new Intent(this, TransferenciaActivity.class);
        intent.putExtra(TransferenciaActivity.EXTRA_MONEDA, "BTC");
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void abrirServicios() {
        startActivity(new Intent(this, ServiciosActivity.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void abrirTarjetaVirtual() {
        startActivity(new Intent(this, TarjetaVirtualActivity.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void configurarAccion(View item, int iconoRes, String label) {
        ((ImageView) item.findViewById(R.id.ivIconoAccion)).setImageResource(iconoRes);
        ((TextView) item.findViewById(R.id.tvLabelAccion)).setText(label);
        item.setOnClickListener(v -> mostrarProximamente());
    }

    private void mostrarProximamente() {
        Toast.makeText(this, "Esta función va a estar disponible próximamente.", Toast.LENGTH_SHORT).show();
    }

    // ── Bottom nav ────────────────────────────────────────────────────────────

    private void configurarBottomNav() {
        bottomNav = findViewById(R.id.bottomNav);
        NavegacionInferior.configurar(this, bottomNav, R.id.nav_inicio);
    }

    private void irALogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private void confirmarCerrarSesion() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Cerrar sesión")
                .setMessage("¿Seguro que querés cerrar sesión?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Cerrar sesión", (dialog, which) -> cerrarSesion())
                .show();
    }

    private void cerrarSesion() {
        SesionUtils.cerrarSesion(this);
    }

    // ── Últimas actividades (las 4 más recientes) ───────────────────────────────

    private void onActividades(TransferenciasRepository.Estado estado) {
        if (isFinishing()) return;
        VistaMovimientos v = VistaMovimientos.inicio(estado);
        LinearLayout lista = findViewById(R.id.layoutActividades);
        boolean hayFilas = v.modo == VistaMovimientos.Modo.LISTA;
        findViewById(R.id.layoutEstadoActividades).setVisibility(hayFilas ? View.GONE : View.VISIBLE);
        lista.setVisibility(hayFilas ? View.VISIBLE : View.GONE);
        findViewById(R.id.progressActividades).setVisibility(
                v.modo == VistaMovimientos.Modo.CARGANDO ? View.VISIBLE : View.GONE);
        findViewById(R.id.btnReintentarActividades).setVisibility(
                v.modo == VistaMovimientos.Modo.ERROR ? View.VISIBLE : View.GONE);
        // Nunca "Todavía no tenés movimientos" si en realidad no se pudieron cargar
        ((TextView) findViewById(R.id.tvEstadoActividades)).setText(
                v.modo == VistaMovimientos.Modo.CARGANDO ? "Cargando movimientos..."
                        : v.modo == VistaMovimientos.Modo.ERROR ? v.error
                        : VistaMovimientos.MSG_VACIO);
        if (!hayFilas) return;

        // Se reusan las filas existentes: el refresco cada 10 s no parpadea
        LayoutInflater inflater = LayoutInflater.from(this);
        while (lista.getChildCount() > v.visibles.size()) lista.removeViewAt(lista.getChildCount() - 1);
        while (lista.getChildCount() < v.visibles.size()) {
            lista.addView(inflater.inflate(R.layout.item_transferencia, lista, false));
        }
        for (int i = 0; i < v.visibles.size(); i++) {
            TransferenciaResponse t = v.visibles.get(i).transferencia;
            View fila = lista.getChildAt(i);
            FilaTransferenciaVista.bind(fila, t);
            fila.setOnClickListener(x -> detalleSheet.abrir(t));
        }
    }

    private void abrirMovimientos() {
        startActivity(new Intent(this, MovimientosActivity.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (detalleSheet != null) detalleSheet.cerrar();
    }

    private void mostrarAvatarMenu() {
        Avatar.mostrar(findViewById(R.id.ivMenuFoto), findViewById(R.id.tvMenuInicial),
                sessionManager.getNombreCompleto(), sessionManager.getFotoPerfilUrl());
    }

    // ── Adapter del carrusel de saldo ─────────────────────────────────────────

    private class SaldoPagerAdapter extends RecyclerView.Adapter<SaldoPagerAdapter.SaldoViewHolder> {

        private static final long DURACION_ANIMACION_MS = 700;

        private final SparseArray<SaldoViewHolder> holders = new SparseArray<>();

        @NonNull
        @Override
        public SaldoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_card_moneda, parent, false);
            return new SaldoViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull SaldoViewHolder holder, int position) {
            holders.put(position, holder);
            holder.valorMostrado = null;
            bindEstilo(holder, position);
            if (position == POS_CRIPTO) {
                bindChips(holder);
            }
            animarEntrada(holder);
            render(holder, position, true);
            holder.itemView.post(() -> {
                if (holder.getBindingAdapterPosition() == vpSaldo.getCurrentItem()) {
                    ajustarAltura(holder);
                }
            });
        }

        /** Llamado con cada estado nuevo del repositorio: actualiza en el lugar, sin re-crear las páginas. */
        void actualizarSaldos() {
            for (int i = 0; i < holders.size(); i++) {
                render(holders.valueAt(i), holders.keyAt(i), true);
            }
            ajustarAlturaDe(vpSaldo.getCurrentItem());
        }

        void ajustarAlturaDe(int position) {
            SaldoViewHolder holder = holders.get(position);
            if (holder != null) ajustarAltura(holder);
        }

        private void ajustarAltura(SaldoViewHolder holder) {
            int ancho = vpSaldo.getWidth();
            if (ancho == 0) return;
            holder.itemView.measure(
                    View.MeasureSpec.makeMeasureSpec(ancho, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            int alto = holder.itemView.getMeasuredHeight();

            ViewGroup.LayoutParams params = vpSaldo.getLayoutParams();
            if (params.height != alto) {
                params.height = alto;
                vpSaldo.setLayoutParams(params);
            }
        }

        @Override
        public void onViewRecycled(@NonNull SaldoViewHolder holder) {
            super.onViewRecycled(holder);
            cancelarAnimacion(holder);
            int index = holders.indexOfValue(holder);
            if (index >= 0) holders.removeAt(index);
        }

        @Override
        public int getItemCount() {
            return 3;
        }

        private void bindEstilo(SaldoViewHolder holder, int position) {
            int badgeBg, icono, botonBg, botonTintBg, textoBoton;
            switch (position) {
                case POS_DOLARES:
                    badgeBg = R.drawable.bg_icon_badge_dolares;
                    icono = R.drawable.ic_dollar_sign;
                    botonBg = R.drawable.bg_button_dolares;
                    botonTintBg = R.drawable.bg_button_dolares_tint;
                    textoBoton = R.color.color_dolares;
                    break;
                case POS_CRIPTO:
                    badgeBg = R.drawable.bg_icon_badge_cripto;
                    icono = R.drawable.ic_coins;
                    botonBg = R.drawable.bg_button_cripto;
                    botonTintBg = R.drawable.bg_button_cripto_tint;
                    textoBoton = R.color.color_cripto;
                    break;
                default:
                    badgeBg = R.drawable.bg_icon_badge;
                    icono = R.drawable.ic_money;
                    botonBg = R.drawable.bg_button_orange;
                    botonTintBg = R.drawable.bg_button_orange_tint;
                    textoBoton = R.color.payx_orange;
                    break;
            }

            holder.ivIcono.setBackgroundResource(badgeBg);
            holder.ivIcono.setImageResource(icono);

            holder.btnTransferir.setBackgroundResource(botonBg);
            holder.btnVerMovimientos.setBackgroundResource(botonTintBg);
            holder.btnVerMovimientos.setTextColor(getColor(textoBoton));

            holder.btnTransferir.setOnClickListener(v -> {
                if (position == POS_PESOS) abrirTransferencia("ARS");
                else if (position == POS_DOLARES) abrirTransferencia("USD");
                else abrirTransferenciaCripto();
            });
            holder.btnVerMovimientos.setOnClickListener(v -> abrirMovimientos());
            holder.btnOjo.setOnClickListener(v -> {
                saldoVisible = !saldoVisible;
                // Ocultar/mostrar no anima: cambia en el momento en todas las pestañas
                for (int i = 0; i < holders.size(); i++) {
                    render(holders.valueAt(i), holders.keyAt(i), false);
                }
            });
            holder.btnReintentarSaldo.setOnClickListener(v -> saldosRepository.refrescarTodo());
        }

        /** Dibuja la pestaña según {@link VistaSaldo}: skeleton, error con "Reintentar" o saldo. */
        private void render(SaldoViewHolder holder, int position, boolean animar) {
            VistaSaldo vista = VistaSaldo.de(estadoSaldos, position, criptoSeleccionada, saldoVisible);
            boolean esCripto = position == POS_CRIPTO;
            boolean hayError = vista.tipo == VistaSaldo.Tipo.ERROR;

            holder.scrollChips.setVisibility(esCripto ? View.VISIBLE : View.GONE);
            holder.tvSimbolo.setText(vista.prefijo);
            holder.tvSimbolo.setVisibility(!esCripto && !hayError ? View.VISIBLE : View.GONE);
            holder.btnOjo.setImageResource(saldoVisible ? R.drawable.ic_eye : R.drawable.ic_eye_off);
            holder.btnOjo.setVisibility(vista.tipo == VistaSaldo.Tipo.SALDO ? View.VISIBLE : View.INVISIBLE);
            holder.layoutErrorSaldo.setVisibility(hayError ? View.VISIBLE : View.GONE);
            holder.tvErrorSaldo.setText(vista.error);
            holder.tvMonto.setVisibility(hayError ? View.GONE : View.VISIBLE);
            holder.tvEstadoSaldo.setVisibility(vista.desactualizado ? View.VISIBLE : View.GONE);

            if (vista.tipo == VistaSaldo.Tipo.CARGANDO) {
                // Skeleton: nunca un "0,00" que parezca un saldo real
                cancelarAnimacion(holder);
                holder.tvMonto.setText("");
                holder.tvMonto.setBackgroundResource(R.drawable.bg_skeleton);
                holder.valorMostrado = null;
            } else {
                holder.tvMonto.setBackground(null);
                if (!hayError) mostrarMonto(holder, position, vista, animar);
            }

            holder.tvEquivalente.setVisibility(vista.equivalente != null ? View.VISIBLE : View.GONE);
            holder.tvEquivalente.setText(vista.equivalente);
            holder.tvAvisoCotizacion.setVisibility(vista.cotizacionDesactualizada ? View.VISIBLE : View.GONE);
        }

        /** Anima de lo que se veía al valor nuevo (700 ms, como la web). Todo en BigDecimal, nunca float. */
        private void mostrarMonto(SaldoViewHolder holder, int position, VistaSaldo vista, boolean animar) {
            BigDecimal hasta = vista.valor;
            String clave = position == POS_CRIPTO ? criptoSeleccionada : "";
            boolean mismoValor = hasta != null && holder.valorMostrado != null
                    && hasta.compareTo(holder.valorMostrado) == 0 && clave.equals(holder.claveMostrada);

            if (hasta == null || !animar || mismoValor) {
                // Oculto, sin animación pedida, o refresco sin cambios: texto directo (no parpadea).
                // Si hay una animación en curso hacia este mismo valor, se la deja terminar.
                if (!(mismoValor && holder.animador != null)) {
                    cancelarAnimacion(holder);
                    holder.tvMonto.setText(vista.monto);
                }
                holder.valorMostrado = hasta;
                holder.claveMostrada = clave;
                return;
            }

            cancelarAnimacion(holder);
            BigDecimal desde = holder.valorMostrado != null && clave.equals(holder.claveMostrada)
                    ? holder.valorMostrado : BigDecimal.ZERO;
            BigDecimal delta = hasta.subtract(desde);
            String cripto = criptoSeleccionada;
            String textoFinal = vista.monto;
            ValueAnimator animador = ValueAnimator.ofFloat(0f, 1f);
            animador.setDuration(DURACION_ANIMACION_MS);
            animador.setInterpolator(new DecelerateInterpolator());
            animador.addUpdateListener(a -> {
                BigDecimal fraccion = BigDecimal.valueOf(a.getAnimatedFraction());
                holder.tvMonto.setText(VistaSaldo.formatear(position, cripto, desde.add(delta.multiply(fraccion))));
            });
            animador.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    // El último cuadro es SIEMPRE el texto exacto del saldo
                    holder.tvMonto.setText(textoFinal);
                    if (holder.animador == animation) holder.animador = null;
                }
            });
            holder.valorMostrado = hasta;
            holder.claveMostrada = clave;
            holder.animador = animador;
            animador.start();
        }

        private void cancelarAnimacion(SaldoViewHolder holder) {
            if (holder.animador != null) {
                ValueAnimator a = holder.animador;
                holder.animador = null;
                a.removeAllUpdateListeners();
                a.removeAllListeners();
                a.cancel();
            }
        }

        private void bindChips(SaldoViewHolder holder) {
            holder.layoutChips.removeAllViews();
            for (String moneda : PerfilResponse.CRIPTOS) {
                TextView chip = new TextView(HomeActivity.this);
                chip.setText(moneda);
                chip.setTextSize(12.5f);
                chip.setTypeface(null, Typeface.BOLD);
                chip.setBackgroundResource(R.drawable.bg_coin_chip);
                chip.setTextColor(getColorStateList(R.color.coin_chip_text));
                chip.setSelected(moneda.equals(criptoSeleccionada));
                chip.setPadding(dpA(16), dpA(8), dpA(16), dpA(8));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                params.setMarginEnd(dpA(8));
                chip.setLayoutParams(params);

                chip.setOnClickListener(v -> {
                    criptoSeleccionada = moneda;
                    for (int i = 0; i < holder.layoutChips.getChildCount(); i++) {
                        holder.layoutChips.getChildAt(i).setSelected(false);
                    }
                    chip.setSelected(true);
                    render(holder, POS_CRIPTO, true);
                    ajustarAltura(holder);
                });

                holder.layoutChips.addView(chip);
            }
        }

        private void animarEntrada(SaldoViewHolder holder) {
            holder.tvMonto.setAlpha(0f);
            holder.tvMonto.setTranslationY(dpA(16));
            holder.tvMonto.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(450)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();

            holder.tvEtiqueta.setAlpha(0f);
            holder.tvEtiqueta.animate().alpha(1f).setStartDelay(80).setDuration(350).start();
        }

        class SaldoViewHolder extends RecyclerView.ViewHolder {
            ImageView ivIcono;
            TextView tvEtiqueta, tvSimbolo, tvMonto, tvEquivalente;
            TextView tvEstadoSaldo, tvAvisoCotizacion, tvErrorSaldo;
            View layoutErrorSaldo, btnReintentarSaldo;
            ImageButton btnOjo;
            android.widget.HorizontalScrollView scrollChips;
            LinearLayout layoutChips;
            android.widget.Button btnTransferir, btnVerMovimientos;
            // Lo que muestra hoy el número grande (para animar solo cuando cambia)
            BigDecimal valorMostrado;
            String claveMostrada;
            ValueAnimator animador;

            SaldoViewHolder(@NonNull View itemView) {
                super(itemView);
                ivIcono = itemView.findViewById(R.id.ivIconoMoneda);
                tvEtiqueta = itemView.findViewById(R.id.tvEtiqueta);
                tvSimbolo = itemView.findViewById(R.id.tvSimbolo);
                tvMonto = itemView.findViewById(R.id.tvMonto);
                tvEquivalente = itemView.findViewById(R.id.tvEquivalente);
                tvEstadoSaldo = itemView.findViewById(R.id.tvEstadoSaldo);
                tvAvisoCotizacion = itemView.findViewById(R.id.tvAvisoCotizacion);
                layoutErrorSaldo = itemView.findViewById(R.id.layoutErrorSaldo);
                tvErrorSaldo = itemView.findViewById(R.id.tvErrorSaldo);
                btnReintentarSaldo = itemView.findViewById(R.id.btnReintentarSaldo);
                btnOjo = itemView.findViewById(R.id.btnOjo);
                scrollChips = itemView.findViewById(R.id.scrollChips);
                layoutChips = itemView.findViewById(R.id.layoutChips);
                btnTransferir = itemView.findViewById(R.id.btnTransferir);
                btnVerMovimientos = itemView.findViewById(R.id.btnVerMovimientos);
            }
        }
    }
}
