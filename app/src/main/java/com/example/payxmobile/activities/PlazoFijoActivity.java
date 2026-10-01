package com.example.payxmobile.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.payxmobile.R;
import com.example.payxmobile.actividad.ListaRemota;
import com.example.payxmobile.model.PlazoFijoResponse;
import com.example.payxmobile.model.TasaPlazoFijo;
import com.example.payxmobile.model.TasasPlazoFijoResponse;
import com.example.payxmobile.network.RetrofitClient;
import com.example.payxmobile.plazofijo.CalculoPlazoFijo;
import com.example.payxmobile.plazofijo.ConstitucionPlazoFijo;
import com.example.payxmobile.plazofijo.FormatoPlazoFijo;
import com.example.payxmobile.plazofijo.PlazoFijoViewModel;
import com.example.payxmobile.plazofijo.PlazosFijosRepository;
import com.example.payxmobile.saldos.EstadoSaldos;
import com.example.payxmobile.saldos.SaldosRepository;
import com.example.payxmobile.transferencias.Moneda;
import com.example.payxmobile.transferencias.Refrescos;
import com.example.payxmobile.transferencias.ui.FiltroMonto;
import com.example.payxmobile.utils.MontoFormatter;
import com.example.payxmobile.utils.SesionUtils;
import com.google.gson.Gson;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Constituir un plazo fijo (PlazoFijoModal.jsx). Toda la lógica vive en
 * {@link ConstitucionPlazoFijo}; esta pantalla la dibuja. Sin la respuesta de /tasas no se
 * muestra el formulario (plazos, TNA, mínimo y máximo los define el admin).
 */
public class PlazoFijoActivity extends AppCompatActivity {

    private static final String K_MONTO = "pf_monto", K_DIAS = "pf_dias", K_PASO = "pf_paso", K_RESULTADO = "pf_resultado";

    private ConstitucionPlazoFijo constitucion;
    private EstadoSaldos estadoSaldos;
    private final SaldosRepository.Observador observadorSaldos = e -> {
        estadoSaldos = e;
        if (e.sesionInvalida) {
            SesionUtils.sesionInvalida(this);
            return;
        }
        render();
    };
    // La lista de plazos fijos dice cuántos ACTIVOS tiene (para el tope, antes de intentar)
    private ListaRemota<PlazoFijoResponse> plazosRepo;
    private final ListaRemota.Observador<PlazoFijoResponse> observadorPlazos =
            e -> constitucion.setActivos(PlazosFijosRepository.activos(e.lista));

    private View seccionTasas, seccionFormulario, seccionConfirmar, seccionResultado, encabezado;
    private EditText etMonto;
    private TextView tvEstadoTasas, tvMontoResumen, tvPlazoBadge, tvInteres, tvTotal, tvFechaVencimiento,
            tvSaldoDisponible, tvMontoMinimo, tvPlazoSeleccionado, tvNota, tvAvisoLimite, tvError,
            tvMontoConfirmar, tvPlazoConfirmar, tvInteresConfirmar, tvTotalConfirmar, tvVencimientoConfirmar,
            tvSaldoLuegoConfirmar, tvErrorConfirmar, tvTituloResultado, tvTextoResultado, tvDetalleResultado;
    private View progressTasas, btnReintentarTasas, filaPlazo;
    private ImageView ivIconoResultado;
    private Button btnConstituir, btnConfirmar, btnModificar;
    private boolean actualizandoCampo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_plazo_fijo);

        plazosRepo = PlazosFijosRepository.get(this);
        constitucion = obtenerConstitucion(new ViewModelProvider(this).get(PlazoFijoViewModel.class), savedInstanceState);
        constitucion.setObservador(c -> render());

        vincularVistas();
        configurarMonto();
        configurarBotones();
        render();
    }

    /** Vive en el ViewModel (rotación, con request en vuelo incluida). Si el proceso murió, se rearma. */
    private ConstitucionPlazoFijo obtenerConstitucion(PlazoFijoViewModel vm, Bundle guardado) {
        if (vm.constitucion != null) return vm.constitucion;
        Context app = getApplicationContext();
        ConstitucionPlazoFijo nueva = new ConstitucionPlazoFijo(
                () -> RetrofitClient.getService(app),
                () -> RetrofitClient.getServiceSinReintentos(app),
                () -> Refrescos.trasMoverPlata(app),
                () -> PlazosFijosRepository.get(app).refrescar());
        if (guardado != null) {
            nueva.setMonto(guardado.getString(K_MONTO, ""));
            String paso = guardado.getString(K_PASO);
            if (paso != null) {
                String json = guardado.getString(K_RESULTADO);
                PlazoFijoResponse res = json != null ? new Gson().fromJson(json, PlazoFijoResponse.class) : null;
                Integer dias = guardado.containsKey(K_DIAS) ? guardado.getInt(K_DIAS) : null;
                nueva.restaurar(dias, ConstitucionPlazoFijo.Paso.valueOf(paso), res);
            }
        }
        vm.constitucion = nueva;
        return nueva;
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putString(K_MONTO, constitucion.getMontoTexto());
        // Si el proceso muere con el POST en vuelo no sabemos cómo terminó: al volver, INCIERTO
        ConstitucionPlazoFijo.Paso paso = constitucion.isEnviando()
                ? ConstitucionPlazoFijo.Paso.INCIERTO : constitucion.getPaso();
        out.putString(K_PASO, paso.name());
        Integer dias = paso == ConstitucionPlazoFijo.Paso.CONFIRMAR
                ? Integer.valueOf(constitucion.getDiasAConfirmar()) : constitucion.getPlazoDias();
        if (dias != null) out.putInt(K_DIAS, dias);
        if (paso == ConstitucionPlazoFijo.Paso.EXITO && constitucion.getResultado() != null) {
            out.putString(K_RESULTADO, new Gson().toJson(constitucion.getResultado()));
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        SaldosRepository saldos = SaldosRepository.get(this);
        saldos.observar(observadorSaldos);
        saldos.refrescar();
        plazosRepo.observar(observadorPlazos);
        plazosRepo.iniciarAutoRefresco();
        // Cada vez que se abre (o se vuelve): el admin pudo cambiar tasas, mínimo o máximo
        constitucion.cargarTasas();
    }

    @Override
    protected void onStop() {
        super.onStop();
        SaldosRepository.get(this).dejarDeObservar(observadorSaldos);
        plazosRepo.detenerAutoRefresco();
        plazosRepo.dejarDeObservar(observadorPlazos);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // La Activity nueva (rotación) se registra en su onCreate; esta ya no dibuja nada
        constitucion.setObservador(null);
    }

    // ── Vistas ────────────────────────────────────────────────────────────────

    private void vincularVistas() {
        encabezado = findViewById(R.id.btnVerMisPlazos);
        seccionTasas = findViewById(R.id.seccionTasas);
        seccionFormulario = findViewById(R.id.seccionFormulario);
        seccionConfirmar = findViewById(R.id.seccionConfirmar);
        seccionResultado = findViewById(R.id.seccionResultado);
        progressTasas = findViewById(R.id.progressTasas);
        tvEstadoTasas = findViewById(R.id.tvEstadoTasas);
        btnReintentarTasas = findViewById(R.id.btnReintentarTasas);
        etMonto = findViewById(R.id.etMonto);
        tvMontoResumen = findViewById(R.id.tvMontoResumen);
        tvPlazoBadge = findViewById(R.id.tvPlazoBadge);
        tvInteres = findViewById(R.id.tvInteres);
        tvTotal = findViewById(R.id.tvTotal);
        tvFechaVencimiento = findViewById(R.id.tvFechaVencimiento);
        tvSaldoDisponible = findViewById(R.id.tvSaldoDisponible);
        tvMontoMinimo = findViewById(R.id.tvMontoMinimo);
        filaPlazo = findViewById(R.id.filaPlazo);
        tvPlazoSeleccionado = findViewById(R.id.tvPlazoSeleccionado);
        tvNota = findViewById(R.id.tvNota);
        tvAvisoLimite = findViewById(R.id.tvAvisoLimite);
        tvError = findViewById(R.id.tvError);
        btnConstituir = findViewById(R.id.btnConstituir);
        tvMontoConfirmar = findViewById(R.id.tvMontoConfirmar);
        tvPlazoConfirmar = findViewById(R.id.tvPlazoConfirmar);
        tvInteresConfirmar = findViewById(R.id.tvInteresConfirmar);
        tvTotalConfirmar = findViewById(R.id.tvTotalConfirmar);
        tvVencimientoConfirmar = findViewById(R.id.tvVencimientoConfirmar);
        tvSaldoLuegoConfirmar = findViewById(R.id.tvSaldoLuegoConfirmar);
        tvErrorConfirmar = findViewById(R.id.tvErrorConfirmar);
        btnConfirmar = findViewById(R.id.btnConfirmar);
        btnModificar = findViewById(R.id.btnModificar);
        ivIconoResultado = findViewById(R.id.ivIconoResultado);
        tvTituloResultado = findViewById(R.id.tvTituloResultado);
        tvTextoResultado = findViewById(R.id.tvTextoResultado);
        tvDetalleResultado = findViewById(R.id.tvDetalleResultado);
    }

    private void configurarMonto() {
        actualizandoCampo = true;
        etMonto.setText(constitucion.getMontoTexto());
        actualizandoCampo = false;
        // Bloquea el tipeo de más de 2 decimales o más de 13 enteros, y avisa
        FiltroMonto.instalar(etMonto, () -> Moneda.PESOS, constitucion::avisarDecimales);
        etMonto.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (actualizandoCampo) return;
                constitucion.setMonto(s.toString());
                render();
            }
        });
    }

    private void configurarBotones() {
        findViewById(R.id.btnUsarTodo).setOnClickListener(v -> {
            BigDecimal saldo = saldoPesos();
            if (saldo == null) {
                Toast.makeText(this, ConstitucionPlazoFijo.MSG_SIN_SALDO, Toast.LENGTH_SHORT).show();
                return;
            }
            constitucion.usarTodo(saldo);
            actualizandoCampo = true;
            etMonto.setText(constitucion.getMontoTexto());
            etMonto.setSelection(etMonto.getText().length());
            actualizandoCampo = false;
        });
        filaPlazo.setOnClickListener(this::mostrarPlazos);
        btnReintentarTasas.setOnClickListener(v -> constitucion.cargarTasas());
        // "Constituir" solo valida y muestra el resumen: la plata se mueve en "Confirmar"
        btnConstituir.setOnClickListener(v -> {
            if (saldoPesos() == null) SaldosRepository.get(this).refrescar();
            constitucion.continuar(saldoPesos());
        });
        btnConfirmar.setOnClickListener(v -> constitucion.confirmar());
        btnModificar.setOnClickListener(v -> constitucion.editar());
        findViewById(R.id.btnVerMisPlazos).setOnClickListener(v -> abrirMisPlazos());
        findViewById(R.id.btnVerMisPlazosResultado).setOnClickListener(v -> {
            abrirMisPlazos();
            finish();
        });
        findViewById(R.id.btnListo).setOnClickListener(v -> finish());
        findViewById(R.id.btnCancelar).setOnClickListener(v -> intentarCerrar());
        findViewById(R.id.btnVolver).setOnClickListener(v -> intentarCerrar());
        findViewById(R.id.btnCerrar).setOnClickListener(v -> intentarCerrar());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                intentarCerrar();
            }
        });
    }

    private void abrirMisPlazos() {
        startActivity(new Intent(this, MisPlazosFijosActivity.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    /** Solo los plazos que llegaron en /tasas, con su TNA. */
    private void mostrarPlazos(View anchor) {
        List<TasaPlazoFijo> opciones = constitucion.getOpciones();
        if (opciones.isEmpty() || constitucion.isEnviando()) return;
        PopupMenu popup = new PopupMenu(this, anchor);
        for (int i = 0; i < opciones.size(); i++) {
            TasaPlazoFijo t = opciones.get(i);
            popup.getMenu().add(0, t.getDias(), i, FormatoPlazoFijo.plazoYTna(t.getDias(), t.getTna()));
        }
        popup.setOnMenuItemClickListener(item -> constitucion.elegirPlazo(item.getItemId()));
        popup.show();
    }

    /** Mientras se constituye no se deja salir: el resultado se perdería de vista. */
    private void intentarCerrar() {
        if (constitucion.isEnviando()) {
            Toast.makeText(this, "Esperá a que termine la operación", Toast.LENGTH_SHORT).show();
            return;
        }
        // Desde el resumen, "atrás" vuelve al formulario (como "Modificar")
        if (constitucion.getPaso() == ConstitucionPlazoFijo.Paso.CONFIRMAR) {
            constitucion.editar();
            return;
        }
        finish();
    }

    /** Saldo en pesos, o null si todavía no se cargó (nunca un 0 inventado). */
    private BigDecimal saldoPesos() {
        if (estadoSaldos == null || estadoSaldos.perfil == null) return null;
        return Moneda.PESOS.saldoEn(estadoSaldos.perfil);
    }

    // ── Dibujo ────────────────────────────────────────────────────────────────

    private void render() {
        if (isFinishing() || seccionFormulario == null) return;
        ConstitucionPlazoFijo.Paso paso = constitucion.getPaso();
        boolean formulario = paso == ConstitucionPlazoFijo.Paso.FORMULARIO;
        boolean confirmar = paso == ConstitucionPlazoFijo.Paso.CONFIRMAR;
        boolean hayTasas = constitucion.getTasas() != null;
        encabezado.setVisibility(formulario ? View.VISIBLE : View.GONE);
        findViewById(R.id.tvTitulo).setVisibility(formulario ? View.VISIBLE : View.GONE);
        findViewById(R.id.tvSubtitulo).setVisibility(formulario ? View.VISIBLE : View.GONE);
        seccionTasas.setVisibility(formulario && !hayTasas ? View.VISIBLE : View.GONE);
        seccionFormulario.setVisibility(formulario && hayTasas ? View.VISIBLE : View.GONE);
        seccionConfirmar.setVisibility(confirmar ? View.VISIBLE : View.GONE);
        seccionResultado.setVisibility(formulario || confirmar ? View.GONE : View.VISIBLE);
        if (formulario && !hayTasas) renderTasas();
        else if (formulario) renderFormulario();
        else if (confirmar) renderConfirmar();
        else renderResultado();
        // El plazo fijo recién constituido aparece YA en "Mis plazos fijos" y en el feed
        if (paso == ConstitucionPlazoFijo.Paso.EXITO) plazosRepo.agregar(constitucion.getResultado());
    }

    private void renderTasas() {
        String error = constitucion.getErrorTasas();
        boolean cargando = error == null;
        progressTasas.setVisibility(cargando ? View.VISIBLE : View.GONE);
        tvEstadoTasas.setText(cargando ? "Buscando las tasas disponibles..." : error);
        btnReintentarTasas.setVisibility(cargando ? View.GONE : View.VISIBLE);
    }

    private void renderFormulario() {
        TasasPlazoFijoResponse tasas = constitucion.getTasas();
        TasaPlazoFijo tasa = constitucion.getTasaElegida();
        BigDecimal monto = constitucion.getMonto();
        BigDecimal montoMostrado = monto != null && monto.signum() > 0 ? monto : BigDecimal.ZERO;
        BigDecimal interes = constitucion.getInteresPreview();

        tvMontoResumen.setText(MontoFormatter.fiat(montoMostrado));
        String plazoTexto = tasa != null ? FormatoPlazoFijo.plazoYTna(tasa.getDias(), tasa.getTna()) : MontoFormatter.SIN_DATO;
        tvPlazoBadge.setText(plazoTexto);
        tvPlazoSeleccionado.setText(plazoTexto);
        tvInteres.setText("+$ " + MontoFormatter.fiat(interes != null ? interes : BigDecimal.ZERO));
        tvTotal.setText("$ " + MontoFormatter.fiat(interes != null ? montoMostrado.add(interes) : montoMostrado));
        String vence = tasa != null
                ? FormatoPlazoFijo.larga(CalculoPlazoFijo.vencimientoEstimado(LocalDate.now(), tasa.getDias())) : "";
        tvFechaVencimiento.setText(vence);
        tvNota.setText("El dinero queda inmovilizado hasta el " + vence
                + ". Ese día se acredita solo, junto con el interés.");
        tvMontoMinimo.setText("Monto mínimo: $ " + MontoFormatter.fiat(tasas.getMontoMinimo()));

        BigDecimal saldo = saldoPesos();
        tvSaldoDisponible.setText(saldo != null ? "$ " + MontoFormatter.fiat(saldo) : MontoFormatter.SIN_DATO);

        boolean limite = constitucion.isLimiteAlcanzado();
        tvAvisoLimite.setVisibility(limite ? View.VISIBLE : View.GONE);
        if (limite) {
            tvAvisoLimite.setText(ConstitucionPlazoFijo.msgLimite(tasas.getMaxActivos())
                    + ". Vas a poder constituir otro cuando venza alguno.");
        }
        String error = constitucion.getError();
        // El aviso del tope ya lo dice: no se repite como error
        boolean mostrarError = error != null && !(limite && error.startsWith("Ya tenés el máximo"));
        tvError.setVisibility(mostrarError ? View.VISIBLE : View.GONE);
        tvError.setText(error);

        boolean enviando = constitucion.isEnviando();
        btnConstituir.setEnabled(!enviando && tasa != null && !limite);
        btnConstituir.setAlpha(btnConstituir.isEnabled() ? 1f : 0.5f);
        btnConstituir.setText(enviando ? "Procesando..." : "Constituir plazo fijo");
        etMonto.setEnabled(!enviando);
    }

    private void renderConfirmar() {
        BigDecimal monto = constitucion.getMontoAConfirmar();
        TasaPlazoFijo tasa = constitucion.getTasaAConfirmar();
        int dias = constitucion.getDiasAConfirmar();
        BigDecimal interes = tasa != null ? CalculoPlazoFijo.interes(monto, tasa.getTna(), dias) : null;
        tvMontoConfirmar.setText("$ " + MontoFormatter.fiat(monto));
        tvPlazoConfirmar.setText(tasa != null ? FormatoPlazoFijo.plazoYTna(dias, tasa.getTna()) : MontoFormatter.SIN_DATO);
        tvInteresConfirmar.setText(interes != null ? "+$ " + MontoFormatter.fiat(interes) : MontoFormatter.SIN_DATO);
        tvTotalConfirmar.setText(interes != null ? "$ " + MontoFormatter.fiat(monto.add(interes)) : MontoFormatter.SIN_DATO);
        tvVencimientoConfirmar.setText(FormatoPlazoFijo.larga(CalculoPlazoFijo.vencimientoEstimado(LocalDate.now(), dias)));
        BigDecimal saldo = saldoPesos();
        tvSaldoLuegoConfirmar.setText(saldo != null && monto != null
                ? "$ " + MontoFormatter.fiat(saldo.subtract(monto)) : MontoFormatter.SIN_DATO);

        String error = constitucion.getError();
        tvErrorConfirmar.setVisibility(error != null ? View.VISIBLE : View.GONE);
        tvErrorConfirmar.setText(error);
        boolean enviando = constitucion.isEnviando();
        btnConfirmar.setEnabled(!enviando && tasa != null && !constitucion.isLimiteAlcanzado());
        btnConfirmar.setAlpha(btnConfirmar.isEnabled() ? 1f : 0.5f);
        btnConfirmar.setText(enviando ? "Procesando..." : "Confirmar plazo fijo");
        btnModificar.setEnabled(!enviando);
    }

    private void renderResultado() {
        if (constitucion.getPaso() == ConstitucionPlazoFijo.Paso.EXITO) {
            PlazoFijoResponse r = constitucion.getResultado();
            ivIconoResultado.setImageResource(R.drawable.ic_check);
            ivIconoResultado.setImageTintList(getColorStateList(R.color.color_positivo));
            tvTituloResultado.setText("¡Plazo fijo constituido!");
            // Datos REALES devueltos por el backend, no el preview
            tvTextoResultado.setText("Invertiste $ " + MontoFormatter.fiat(r.getMonto()) + " a " + r.getPlazoDias()
                    + " días con una TNA del " + FormatoPlazoFijo.tna(r.getTna()) + "%");
            tvDetalleResultado.setVisibility(View.VISIBLE);
            tvDetalleResultado.setText("Cobrás $ " + MontoFormatter.fiat(r.getMontoTotal()) + " el "
                    + FormatoPlazoFijo.larga(FormatoPlazoFijo.dia(r.getFechaVencimiento()))
                    + ", sin que tengas que hacer nada");
        } else {
            ivIconoResultado.setImageResource(R.drawable.ic_clock);
            ivIconoResultado.setImageTintList(getColorStateList(R.color.color_servicio_luz));
            tvTituloResultado.setText("No sabemos si se constituyó");
            tvTextoResultado.setText(constitucion.getError());
            tvDetalleResultado.setVisibility(View.GONE);
        }
    }
}
