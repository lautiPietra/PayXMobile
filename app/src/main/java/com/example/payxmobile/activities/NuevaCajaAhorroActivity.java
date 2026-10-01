package com.example.payxmobile.activities;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.payxmobile.R;
import com.example.payxmobile.actividad.ListaRemota;
import com.example.payxmobile.cajas.CajasAhorroRepository;
import com.example.payxmobile.cajas.CajasViewModel;
import com.example.payxmobile.cajas.FormularioCaja;
import com.example.payxmobile.cajas.TemasCaja;
import com.example.payxmobile.cajas.ui.EstiloCaja;
import com.example.payxmobile.model.CajaAhorroResponse;
import com.example.payxmobile.network.RetrofitClient;
import com.example.payxmobile.transferencias.Moneda;
import com.example.payxmobile.transferencias.ui.FiltroMonto;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;

/**
 * Crear una caja o, con EXTRA_CAJA, editarla (CajaAhorroModal.jsx). Colores e íconos se eligen
 * SOLO de las whitelists (se arman desde {@link TemasCaja}): no hay texto libre ni color picker.
 * Toda la lógica vive en {@link FormularioCaja}.
 */
public class NuevaCajaAhorroActivity extends AppCompatActivity {

    private static final String K_NOMBRE = "c_nombre", K_COLOR = "c_color", K_ICONO = "c_icono", K_META = "c_meta",
            K_INCIERTO = "c_incierto";

    private FormularioCaja formulario;
    private ListaRemota<CajaAhorroResponse> repo;
    private final ListaRemota.Observador<CajaAhorroResponse> observadorCajas =
            e -> formulario.setCantidadCajas(e.lista != null ? e.lista.size() : null);

    private final List<View> swatches = new ArrayList<>();
    private final List<View> opcionesIcono = new ArrayList<>();
    private EditText etNombre, etMeta;
    private TextView tvError, tvAvisoLimite, tvPreviewNombre, tvContador;
    private Button btnGuardar;
    private boolean actualizandoCampo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nueva_caja_ahorro);
        repo = CajasAhorroRepository.get(this);
        formulario = obtenerFormulario(new ViewModelProvider(this).get(CajasViewModel.class), savedInstanceState);
        formulario.setObservador(f -> render());

        etNombre = findViewById(R.id.etNombreCaja);
        etMeta = findViewById(R.id.etMetaCaja);
        tvError = findViewById(R.id.tvError);
        tvAvisoLimite = findViewById(R.id.tvAvisoLimite);
        tvPreviewNombre = findViewById(R.id.tvPreviewNombre);
        tvContador = findViewById(R.id.tvContadorNombre);
        btnGuardar = findViewById(R.id.btnCrearCaja);
        ((TextView) findViewById(R.id.tvTitulo)).setText(formulario.esEdicion() ? "Editar caja de ahorro" : "Nueva caja de ahorro");

        armarSelectores();
        configurarCampos();
        btnGuardar.setOnClickListener(v -> {
            if (formulario.getPaso() == FormularioCaja.Paso.INCIERTO) finish();
            else formulario.guardar();
        });
        findViewById(R.id.btnVolver).setOnClickListener(v -> intentarCerrar());
        findViewById(R.id.btnCerrar).setOnClickListener(v -> intentarCerrar());
        findViewById(R.id.btnCancelar).setOnClickListener(v -> intentarCerrar());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                intentarCerrar();
            }
        });
        render();
    }

    private FormularioCaja obtenerFormulario(CajasViewModel vm, Bundle guardado) {
        if (vm.formulario != null) return vm.formulario;
        Context app = getApplicationContext();
        String json = getIntent().getStringExtra(CajasAhorroActivity.EXTRA_CAJA);
        CajaAhorroResponse existente = json != null ? new Gson().fromJson(json, CajaAhorroResponse.class) : null;
        FormularioCaja f = new FormularioCaja(existente,
                () -> RetrofitClient.getService(app),
                () -> RetrofitClient.getServiceSinReintentos(app),
                caja -> CajasAhorroRepository.get(app).actualizar(caja),
                () -> CajasAhorroRepository.get(app).refrescar());
        if (guardado != null) {
            f.restaurar(guardado.getString(K_NOMBRE, ""), guardado.getString(K_COLOR), guardado.getString(K_ICONO),
                    guardado.getString(K_META, ""), guardado.getBoolean(K_INCIERTO));
        }
        vm.formulario = f;
        return f;
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putString(K_NOMBRE, formulario.getNombre());
        out.putString(K_COLOR, formulario.getColor());
        out.putString(K_ICONO, formulario.getIcono());
        out.putString(K_META, formulario.getMetaTexto());
        // Si el proceso muere con el pedido en vuelo no sabemos cómo terminó
        out.putBoolean(K_INCIERTO, formulario.isEnviando() || formulario.getPaso() == FormularioCaja.Paso.INCIERTO);
    }

    @Override
    protected void onStart() {
        super.onStart();
        repo.observar(observadorCajas);
        repo.refrescar();
        // Crear: el máximo se pide SIEMPRE antes de decidir (el admin pudo cambiarlo)
        if (!formulario.esEdicion()) formulario.cargarLimite();
    }

    @Override
    protected void onStop() {
        super.onStop();
        repo.dejarDeObservar(observadorCajas);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        formulario.setObservador(null);
    }

    // ── Selectores: SOLO los valores de las whitelists ────────────────────────

    private void armarSelectores() {
        LinearLayout filaColores = findViewById(R.id.filaColores);
        for (String color : TemasCaja.COLORES) {
            FrameLayout swatch = new FrameLayout(this);
            swatch.setLayoutParams(new LinearLayout.LayoutParams(0, dp(40), 1f));
            View circulo = new View(this);
            circulo.setBackgroundResource(R.drawable.bg_icon_circle_solid);
            circulo.setBackgroundTintList(EstiloCaja.tinte(color));
            circulo.setLayoutParams(new FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER));
            swatch.addView(circulo);
            View anillo = new View(this);
            anillo.setBackgroundResource(R.drawable.bg_ring_selector);
            anillo.setLayoutParams(new FrameLayout.LayoutParams(dp(38), dp(38), Gravity.CENTER));
            swatch.addView(anillo);
            ImageView check = new ImageView(this);
            check.setImageResource(R.drawable.ic_check);
            check.setLayoutParams(new FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER));
            swatch.addView(check);
            swatch.setContentDescription("Color " + color);
            swatch.setTag(color);
            swatch.setOnClickListener(v -> formulario.elegirColor(color));
            filaColores.addView(swatch);
            swatches.add(swatch);
        }
        LinearLayout fila1 = findViewById(R.id.filaIconos1), fila2 = findViewById(R.id.filaIconos2);
        for (int i = 0; i < TemasCaja.ICONOS.size(); i++) {
            TemasCaja.Icono icono = TemasCaja.ICONOS.get(i);
            FrameLayout opcion = new FrameLayout(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), 1f);
            if (i % 6 != 5) lp.setMarginEnd(dp(8));
            opcion.setLayoutParams(lp);
            opcion.setBackgroundResource(R.drawable.bg_icono_caja);
            ImageView iv = new ImageView(this);
            iv.setImageResource(EstiloCaja.icono(icono.clave));
            iv.setLayoutParams(new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
            opcion.addView(iv);
            opcion.setContentDescription(icono.etiqueta);
            opcion.setTag(icono.clave);
            opcion.setOnClickListener(v -> formulario.elegirIcono(icono.clave));
            (i < 6 ? fila1 : fila2).addView(opcion);
            opcionesIcono.add(opcion);
        }
    }

    private void configurarCampos() {
        actualizandoCampo = true;
        etNombre.setText(formulario.getNombre());
        etMeta.setText(formulario.getMetaTexto());
        actualizandoCampo = false;
        etNombre.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (actualizandoCampo) return;
                formulario.setNombre(s.toString());
                render();
            }
        });
        // Bloquea el tipeo de más de 2 decimales o más de 13 enteros, y avisa
        FiltroMonto.instalar(etMeta, () -> Moneda.PESOS, formulario::avisarDecimales);
        etMeta.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (actualizandoCampo) return;
                formulario.setMeta(s.toString());
                render();
            }
        });
    }

    private void intentarCerrar() {
        if (formulario.isEnviando()) {
            Toast.makeText(this, "Esperá a que termine la operación", Toast.LENGTH_SHORT).show();
            return;
        }
        finish();
    }

    // ── Dibujo ────────────────────────────────────────────────────────────────

    private void render() {
        if (isFinishing() || btnGuardar == null) return;
        if (formulario.getPaso() == FormularioCaja.Paso.EXITO) {
            Toast.makeText(this, formulario.esEdicion() ? "Cambios guardados" : "Caja creada", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        String nombre = formulario.getNombre().trim();
        tvPreviewNombre.setText(nombre.isEmpty() ? "Nombre de la caja" : nombre);
        tvContador.setText(formulario.getNombre().length() + "/" + FormularioCaja.MAX_NOMBRE);
        findViewById(R.id.previewCaja).setBackgroundTintList(EstiloCaja.tinte(formulario.getColor()));
        ((ImageView) findViewById(R.id.ivPreviewIcono)).setImageResource(EstiloCaja.icono(formulario.getIcono()));

        for (View s : swatches) {
            boolean elegido = s.getTag().equals(formulario.getColor());
            FrameLayout f = (FrameLayout) s;
            f.getChildAt(1).setVisibility(elegido ? View.VISIBLE : View.GONE);
            f.getChildAt(2).setVisibility(elegido ? View.VISIBLE : View.GONE);
        }
        for (View o : opcionesIcono) {
            boolean elegido = o.getTag().equals(formulario.getIcono());
            o.setSelected(elegido);
            ((ImageView) ((FrameLayout) o).getChildAt(0)).setImageTintList(
                    getColorStateList(elegido ? R.color.payx_orange : R.color.text_secondary));
        }

        boolean limite = !formulario.esEdicion() && formulario.isLimiteAlcanzado();
        tvAvisoLimite.setVisibility(limite ? View.VISIBLE : View.GONE);
        if (limite) tvAvisoLimite.setText(FormularioCaja.msgLimite(formulario.getMaxCajas()));
        String error = formulario.getError();
        boolean mostrarError = error != null && !(limite && error.startsWith("Ya tenés el máximo"));
        tvError.setVisibility(mostrarError ? View.VISIBLE : View.GONE);
        tvError.setText(error);

        boolean incierto = formulario.getPaso() == FormularioCaja.Paso.INCIERTO;
        boolean enviando = formulario.isEnviando();
        btnGuardar.setEnabled(incierto || (!enviando && !limite));
        btnGuardar.setAlpha(btnGuardar.isEnabled() ? 1f : 0.5f);
        btnGuardar.setText(incierto ? "Volver a mis cajas"
                : enviando ? "Guardando..." : formulario.esEdicion() ? "Guardar cambios" : "Crear caja");
        etNombre.setEnabled(!enviando && !incierto);
        etMeta.setEnabled(!enviando && !incierto);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
