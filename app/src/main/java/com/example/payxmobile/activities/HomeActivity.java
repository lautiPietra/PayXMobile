package com.example.payxmobile.activities;

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
import androidx.viewpager2.widget.ViewPager2;

import com.example.payxmobile.R;
import com.example.payxmobile.model.PerfilResponse;
import com.example.payxmobile.model.SinLeerResponse;
import com.example.payxmobile.network.RetrofitClient;
import com.example.payxmobile.utils.SessionManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.text.NumberFormat;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HomeActivity extends AppCompatActivity {

    private static final int POS_PESOS = 0;
    private static final int POS_DOLARES = 1;
    private static final int POS_CRIPTO = 2;
    private static final String[] MONEDAS_CRIPTO = {"BTC", "ETH", "SOL", "USDT", "BNB", "XRP"};

    private SessionManager sessionManager;
    private View badgeNotificaciones;

    private TextView tabPesos, tabDolares, tabCripto;
    private View dot0, dot1, dot2;
    private ViewPager2 vpSaldo;
    private SaldoPagerAdapter pagerAdapter;

    private View overlayMenu, panelMenu, scrimMenu;
    private OnBackPressedCallback cerrarMenuAlVolver;
    private BottomNavigationView bottomNav;

    private final NumberFormat formatoMonto = NumberFormat.getNumberInstance(new Locale("es", "AR"));

    private double saldoPesos = 0;
    private double saldoUsd = 0;
    private String alias;
    private String cvu;
    private boolean saldoVisible = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        sessionManager = new SessionManager(this);

        if (!sessionManager.isLoggedIn()) {
            irALogin();
            return;
        }

        formatoMonto.setMinimumFractionDigits(2);
        formatoMonto.setMaximumFractionDigits(2);

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
        findViewById(R.id.tvConsultarTodas).setOnClickListener(v -> mostrarProximamente());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (bottomNav == null) return;
        bottomNav.setSelectedItemId(R.id.nav_inicio);
        actualizarBadge();
        cargarPerfil();
    }

    // ── Saludo ────────────────────────────────────────────────────────────────

    private void configurarSaludo() {
        String nombreCompleto = sessionManager.getNombreCompleto();
        String primerNombre = nombreCompleto.contains(" ")
                ? nombreCompleto.split(" ")[0]
                : nombreCompleto;

        TextView tvBienvenida = findViewById(R.id.tvBienvenida);
        tvBienvenida.setText("Hola, " + primerNombre);

        TextView tvMenuSaludo = findViewById(R.id.tvMenuSaludo);
        tvMenuSaludo.setText("Hola, " + primerNombre);
    }

    // ── Saldo real (alias y CVU se muestran en Perfil / Tarjeta virtual) ────────

    private void cargarPerfil() {
        RetrofitClient.getService(this).obtenerPerfil()
                .enqueue(new Callback<PerfilResponse>() {
                    @Override
                    public void onResponse(Call<PerfilResponse> call, Response<PerfilResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            PerfilResponse perfil = response.body();
                            saldoPesos = perfil.getSaldoPesos();
                            saldoUsd = perfil.getSaldoUsd();
                            alias = perfil.getAlias();
                            cvu = perfil.getCvu();
                            pagerAdapter.notifyDataSetChanged();
                        }
                    }

                    @Override
                    public void onFailure(Call<PerfilResponse> call, Throwable t) {
                        // se mantiene el ultimo saldo conocido (o 0 la primera vez)
                    }
                });
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
                pagerAdapter.animarPagina(position);
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
        startActivity(new Intent(this, TransferenciaCriptoActivity.class));
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
        bottomNav.setSelectedItemId(R.id.nav_inicio);

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_inicio) {
                return true;
            } else if (id == R.id.nav_actividad) {
                startActivity(new Intent(HomeActivity.this, MovimientosActivity.class));
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                return false;
            } else if (id == R.id.nav_inversiones) {
                startActivity(new Intent(HomeActivity.this, InversionesActivity.class));
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                return false;
            } else if (id == R.id.nav_perfil) {
                startActivity(new Intent(HomeActivity.this, PerfilActivity.class));
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                return false;
            }
            return false;
        });
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
        sessionManager.clearSession();
        irALogin();
    }

    // ── Adapter del carrusel de saldo ─────────────────────────────────────────

    private class SaldoPagerAdapter extends RecyclerView.Adapter<SaldoPagerAdapter.SaldoViewHolder> {

        private final SparseArray<SaldoViewHolder> holders = new SparseArray<>();
        private String monedaCriptoSeleccionada = MONEDAS_CRIPTO[0];

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
            bindContenido(holder, position);
            bindEstilo(holder, position);
            if (position == POS_CRIPTO) {
                bindChips(holder);
            }
            animarEntrada(holder, position);
            holder.itemView.post(() -> {
                if (holder.getBindingAdapterPosition() == vpSaldo.getCurrentItem()) {
                    ajustarAltura(holder);
                }
            });
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

            holder.scrollChips.setVisibility(position == POS_CRIPTO ? View.VISIBLE : View.GONE);
            holder.tvEquivalente.setVisibility(position == POS_CRIPTO ? View.VISIBLE : View.GONE);
            holder.tvSimbolo.setVisibility(position == POS_CRIPTO ? View.GONE : View.VISIBLE);

            holder.btnTransferir.setOnClickListener(v -> {
                if (position == POS_PESOS) abrirTransferencia("ARS");
                else if (position == POS_DOLARES) abrirTransferencia("USD");
                else abrirTransferenciaCripto();
            });
            holder.btnVerMovimientos.setOnClickListener(v -> mostrarProximamente());
            holder.btnOjo.setOnClickListener(v -> {
                saldoVisible = !saldoVisible;
                refrescarVisibilidad();
            });
        }

        private void bindContenido(SaldoViewHolder holder, int position) {
            switch (position) {
                case POS_DOLARES:
                    holder.tvSimbolo.setText("US$");
                    holder.tvMonto.setText(formatearMonto(saldoUsd, null));
                    break;
                case POS_CRIPTO:
                    holder.tvMonto.setText(formatearMonto(0, monedaCriptoSeleccionada));
                    holder.tvEquivalente.setText("≈ $ 0,00");
                    break;
                default:
                    holder.tvSimbolo.setText("$");
                    holder.tvMonto.setText(formatearMonto(saldoPesos, null));
                    break;
            }
            holder.btnOjo.setImageResource(saldoVisible ? R.drawable.ic_eye : R.drawable.ic_eye_off);
        }

        private void bindChips(SaldoViewHolder holder) {
            holder.layoutChips.removeAllViews();
            for (String moneda : MONEDAS_CRIPTO) {
                TextView chip = new TextView(HomeActivity.this);
                chip.setText(moneda);
                chip.setTextSize(12.5f);
                chip.setTypeface(null, Typeface.BOLD);
                chip.setBackgroundResource(R.drawable.bg_coin_chip);
                chip.setTextColor(getColorStateList(R.color.coin_chip_text));
                chip.setSelected(moneda.equals(monedaCriptoSeleccionada));
                chip.setPadding(dpA(16), dpA(8), dpA(16), dpA(8));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                params.setMarginEnd(dpA(8));
                chip.setLayoutParams(params);

                chip.setOnClickListener(v -> {
                    monedaCriptoSeleccionada = moneda;
                    for (int i = 0; i < holder.layoutChips.getChildCount(); i++) {
                        holder.layoutChips.getChildAt(i).setSelected(false);
                    }
                    chip.setSelected(true);
                    holder.tvMonto.setText(formatearMonto(0, monedaCriptoSeleccionada));
                });

                holder.layoutChips.addView(chip);
            }
        }

        private String formatearMonto(double monto, String sufijoMoneda) {
            if (!saldoVisible) return "••••••";
            String numero = formatoMonto.format(monto);
            return sufijoMoneda != null ? numero + " " + sufijoMoneda : numero;
        }

        private void refrescarVisibilidad() {
            for (int i = 0; i < holders.size(); i++) {
                int position = holders.keyAt(i);
                SaldoViewHolder holder = holders.valueAt(i);
                bindContenido(holder, position);
            }
        }

        private void animarEntrada(SaldoViewHolder holder, int position) {
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

            animarConteo(holder, position);
        }

        void animarPagina(int position) {
            SaldoViewHolder holder = holders.get(position);
            if (holder != null) {
                animarConteo(holder, position);
                ajustarAltura(holder);
            }
        }

        private void animarConteo(SaldoViewHolder holder, int position) {
            if (!saldoVisible) return;
            double destino = position == POS_DOLARES ? saldoUsd : position == POS_CRIPTO ? 0 : saldoPesos;
            String sufijo = position == POS_CRIPTO ? monedaCriptoSeleccionada : null;

            ValueAnimator animador = ValueAnimator.ofFloat(0f, (float) destino);
            animador.setDuration(700);
            animador.setInterpolator(new DecelerateInterpolator());
            animador.addUpdateListener(animation ->
                    holder.tvMonto.setText(formatearMonto((float) animation.getAnimatedValue(), sufijo)));
            animador.start();
        }

        class SaldoViewHolder extends RecyclerView.ViewHolder {
            ImageView ivIcono;
            TextView tvEtiqueta, tvSimbolo, tvMonto, tvEquivalente;
            ImageButton btnOjo;
            android.widget.HorizontalScrollView scrollChips;
            LinearLayout layoutChips;
            android.widget.Button btnTransferir, btnVerMovimientos;

            SaldoViewHolder(@NonNull View itemView) {
                super(itemView);
                ivIcono = itemView.findViewById(R.id.ivIconoMoneda);
                tvEtiqueta = itemView.findViewById(R.id.tvEtiqueta);
                tvSimbolo = itemView.findViewById(R.id.tvSimbolo);
                tvMonto = itemView.findViewById(R.id.tvMonto);
                tvEquivalente = itemView.findViewById(R.id.tvEquivalente);
                btnOjo = itemView.findViewById(R.id.btnOjo);
                scrollChips = itemView.findViewById(R.id.scrollChips);
                layoutChips = itemView.findViewById(R.id.layoutChips);
                btnTransferir = itemView.findViewById(R.id.btnTransferir);
                btnVerMovimientos = itemView.findViewById(R.id.btnVerMovimientos);
            }
        }
    }
}
