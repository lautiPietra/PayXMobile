package com.example.payxmobile.activities;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.payxmobile.R;
import com.example.payxmobile.model.PerfilResponse;
import com.example.payxmobile.model.SinLeerResponse;
import com.example.payxmobile.network.RetrofitClient;
import com.example.payxmobile.utils.SessionManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.text.NumberFormat;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HomeActivity extends AppCompatActivity {

    private SessionManager sessionManager;
    private View badgeNotificaciones;

    private TextView tabPesos, tabDolares, tvSaldoSimbolo, tvSaldoNumero, tvAliasValor, tvCvuValor;
    private ImageButton btnToggleSaldo;

    private final NumberFormat formatoMonto = NumberFormat.getNumberInstance(new Locale("es", "AR"));

    private double saldoPesos = 0;
    private double saldoUsd = 0;
    private String alias;
    private String cvu;
    private String monedaActiva = "pesos";
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
        tvSaldoSimbolo = findViewById(R.id.tvSaldoSimbolo);
        tvSaldoNumero = findViewById(R.id.tvSaldoNumero);
        tvAliasValor = findViewById(R.id.tvAliasValor);
        tvCvuValor = findViewById(R.id.tvCvuValor);
        btnToggleSaldo = findViewById(R.id.btnToggleSaldo);

        configurarSaludo();
        configurarTabsMoneda();
        configurarBottomNav();
        configurarAccionesHome();

        findViewById(R.id.btnNotificaciones).setOnClickListener(v -> {
            startActivity(new Intent(this, NotificacionesActivity.class));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });

        btnToggleSaldo.setOnClickListener(v -> {
            saldoVisible = !saldoVisible;
            btnToggleSaldo.setImageResource(saldoVisible ? R.drawable.ic_eye : R.drawable.ic_eye_off);
            actualizarMontoMostrado();
        });

        findViewById(R.id.filaAlias).setOnClickListener(v -> copiarAlPortapapeles("Alias", alias));
        findViewById(R.id.filaCvu).setOnClickListener(v -> copiarAlPortapapeles("CVU", cvu));

        findViewById(R.id.btnTransferir).setOnClickListener(v -> mostrarProximamente());
        findViewById(R.id.btnVerMovimientos).setOnClickListener(v -> mostrarProximamente());
        findViewById(R.id.btnQuieroInvertir).setOnClickListener(v -> mostrarProximamente());
        findViewById(R.id.tvConsultarTodas).setOnClickListener(v -> mostrarProximamente());
    }

    @Override
    protected void onResume() {
        super.onResume();
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
    }

    // ── Saldo real, alias y CVU ───────────────────────────────────────────────

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
                            actualizarMontoMostrado();
                            tvAliasValor.setText(alias != null ? alias : "-");
                            tvCvuValor.setText(cvu != null ? cvu : "-");
                        }
                    }

                    @Override
                    public void onFailure(Call<PerfilResponse> call, Throwable t) {
                        // se mantiene el ultimo saldo conocido (o 0 la primera vez)
                    }
                });
    }

    private void copiarAlPortapapeles(String etiqueta, String valor) {
        if (valor == null) return;
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText(etiqueta, valor));
        Toast.makeText(this, etiqueta + " copiado", Toast.LENGTH_SHORT).show();
    }

    private void configurarTabsMoneda() {
        tabPesos.setOnClickListener(v -> seleccionarMoneda("pesos"));
        tabDolares.setOnClickListener(v -> seleccionarMoneda("dolares"));
        actualizarMontoMostrado();
    }

    private void seleccionarMoneda(String moneda) {
        monedaActiva = moneda;

        boolean esPesos = moneda.equals("pesos");
        tabPesos.setTextColor(esPesos ? getColor(R.color.payx_orange) : getColor(R.color.text_secondary));
        tabPesos.setTypeface(null, esPesos ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        tabDolares.setTextColor(!esPesos ? getColor(R.color.payx_orange) : getColor(R.color.text_secondary));
        tabDolares.setTypeface(null, !esPesos ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);

        actualizarMontoMostrado();
    }

    private void actualizarMontoMostrado() {
        boolean esPesos = monedaActiva.equals("pesos");
        tvSaldoSimbolo.setText(esPesos ? "$" : "US$");

        double monto = esPesos ? saldoPesos : saldoUsd;
        tvSaldoNumero.setText(saldoVisible ? formatoMonto.format(monto) : "••••••");
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

    // ── Acciones rápidas ("Qué querés hacer") ────────────────────────────────

    private void configurarAccionesHome() {
        configurarAccion(findViewById(R.id.accionTransferir), R.drawable.ic_send, "Transferir");
        configurarAccion(findViewById(R.id.accionTransferenciaDolares), R.drawable.ic_dollar_sign, "Transferencia en dólares");
        configurarAccion(findViewById(R.id.accionTransferenciaCripto), R.drawable.ic_coins, "Transferencia en cripto");
        configurarAccion(findViewById(R.id.accionServicios), R.drawable.ic_zap, "Pagar servicios");
        configurarAccion(findViewById(R.id.accionTarjeta), R.drawable.ic_credit_card, "Tarjeta virtual");
        configurarAccion(findViewById(R.id.accionEstadisticas), R.drawable.ic_bar_chart, "Estadísticas");
    }

    private void configurarAccion(View item, int iconoRes, String label) {
        ((ImageView) item.findViewById(R.id.ivIconoAccion)).setImageResource(iconoRes);
        ((TextView) item.findViewById(R.id.tvLabelAccion)).setText(label);
        item.setOnClickListener(v -> mostrarProximamente());
    }

    private void mostrarProximamente() {
        Toast.makeText(this, "Esta función va a estar disponible próximamente.", Toast.LENGTH_SHORT).show();
    }

    // ── Modal de Inversiones (se abre desde el bottom nav) ───────────────────

    private void mostrarModalInversiones() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View sheet = getLayoutInflater().inflate(R.layout.bottom_sheet_inversiones, null);
        dialog.setContentView(sheet);

        View contenedor = (View) sheet.getParent();
        if (contenedor != null) {
            contenedor.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        }

        configurarAccionModal(dialog, sheet, R.id.accionPlazoFijo, R.drawable.ic_piggy_bank, "Plazos fijos");
        configurarAccionModal(dialog, sheet, R.id.accionMisPlazoFijo, R.drawable.ic_file_text, "Mis plazos fijos");
        configurarAccionModal(dialog, sheet, R.id.accionCajaAhorro, R.drawable.ic_target, "Cajas de ahorro");
        configurarAccionModal(dialog, sheet, R.id.accionComprarDolares, R.drawable.ic_arrow_down_circle, "Comprar dólares");
        configurarAccionModal(dialog, sheet, R.id.accionVenderDolares, R.drawable.ic_arrow_up_circle, "Vender dólares");
        configurarAccionModal(dialog, sheet, R.id.accionComprarCripto, R.drawable.ic_coins, "Comprar criptomonedas");
        configurarAccionModal(dialog, sheet, R.id.accionVenderCripto, R.drawable.ic_coins, "Vender criptomonedas");

        dialog.show();
    }

    private void configurarAccionModal(BottomSheetDialog dialog, View sheet, int idContenedor, int iconoRes, String label) {
        View item = sheet.findViewById(idContenedor);
        ((ImageView) item.findViewById(R.id.ivIconoAccion)).setImageResource(iconoRes);
        ((TextView) item.findViewById(R.id.tvLabelAccion)).setText(label);
        item.setOnClickListener(v -> {
            dialog.dismiss();
            mostrarProximamente();
        });
    }

    // ── Bottom nav ────────────────────────────────────────────────────────────

    private void configurarBottomNav() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setSelectedItemId(R.id.nav_inicio);

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_inicio) {
                return true;
            } else if (id == R.id.nav_actividad) {
                mostrarProximamente();
                return false;
            } else if (id == R.id.nav_inversiones) {
                mostrarModalInversiones();
                return false;
            } else if (id == R.id.nav_perfil) {
                startActivity(new Intent(HomeActivity.this, PerfilActivity.class));
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                return true;
            }
            return false;
        });
    }

    private void irALogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}
