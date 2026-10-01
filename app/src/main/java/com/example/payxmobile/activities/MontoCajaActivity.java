package com.example.payxmobile.activities;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
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
import com.example.payxmobile.cajas.MovimientoCaja;
import com.example.payxmobile.cajas.MovimientosCajaSesion;
import com.example.payxmobile.cajas.OperacionMontoCaja;
import com.example.payxmobile.cajas.ui.EstiloCaja;
import com.example.payxmobile.model.CajaAhorroResponse;
import com.example.payxmobile.network.RetrofitClient;
import com.example.payxmobile.saldos.EstadoSaldos;
import com.example.payxmobile.saldos.SaldosRepository;
import com.example.payxmobile.transferencias.Moneda;
import com.example.payxmobile.transferencias.Refrescos;
import com.example.payxmobile.transferencias.ui.FiltroMonto;
import com.example.payxmobile.utils.MontoFormatter;
import com.example.payxmobile.utils.SesionUtils;
import com.google.gson.Gson;

import java.math.BigDecimal;
import java.time.Clock;

/**
 * Agregar dinero a una caja o retirarlo (MontoCajaModal.jsx). EXTRA_CAJA (JSON) y EXTRA_TIPO
 * ("DEPOSITO" o "RETIRO"). Toda la lógica vive en {@link OperacionMontoCaja}.
 */
public class MontoCajaActivity extends AppCompatActivity {

    public static final String EXTRA_TIPO = "tipo";
    private static final String K_MONTO = "m_monto", K_INCIERTO = "m_incierto";

    private OperacionMontoCaja operacion;
    private EstadoSaldos estadoSaldos;
    private final SaldosRepository.Observador observadorSaldos = e -> {
        estadoSaldos = e;
        if (e.sesionInvalida) {
            SesionUtils.sesionInvalida(this);
            return;
        }
        render();
    };
    private ListaRemota<CajaAhorroResponse> repo;
    // La caja puede cambiar mientras está abierta (p. ej. desde la web): se usa la más nueva
    private final ListaRemota.Observador<CajaAhorroResponse> observadorCajas =
            e -> operacion.setCaja(CajasAhorroRepository.buscar(e.lista, operacion.getCaja().getId()));

    private View seccionFormulario, seccionResultado;
    private EditText etMonto;
    private TextView tvDisponible, tvLuego, tvError, tvTituloResultado, tvTextoResultado, tvDetalleResultado;
    private ImageView ivIconoResultado;
    private Button btnConfirmar;
    private boolean actualizandoCampo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_monto_caja);
        repo = CajasAhorroRepository.get(this);
        operacion = obtenerOperacion(new ViewModelProvider(this).get(CajasViewModel.class), savedInstanceState);
        if (operacion == null) {
            finish();
            return;
        }
        operacion.setObservador(o -> render());

        seccionFormulario = findViewById(R.id.seccionFormulario);
        seccionResultado = findViewById(R.id.seccionResultado);
        etMonto = findViewById(R.id.etMonto);
        tvDisponible = findViewById(R.id.tvDisponible);
        tvLuego = findViewById(R.id.tvLuego);
        tvError = findViewById(R.id.tvError);
        tvTituloResultado = findViewById(R.id.tvTituloResultado);
        tvTextoResultado = findViewById(R.id.tvTextoResultado);
        tvDetalleResultado = findViewById(R.id.tvDetalleResultado);
        ivIconoResultado = findViewById(R.id.ivIconoResultado);
        btnConfirmar = findViewById(R.id.btnConfirmar);

        boolean deposito = operacion.esDeposito();
        ((TextView) findViewById(R.id.tvTitulo)).setText(deposito ? "Agregar dinero" : "Retirar dinero");
        ((TextView) findViewById(R.id.tvLabelDisponible)).setText(deposito ? "Saldo disponible en tu cuenta" : "Saldo en la caja");
        configurarMonto();
        configurarBotones();
        render();
    }

    private OperacionMontoCaja obtenerOperacion(CajasViewModel vm, Bundle guardado) {
        if (vm.operacion != null) return vm.operacion;
        String json = getIntent().getStringExtra(CajasAhorroActivity.EXTRA_CAJA);
        if (json == null) return null;
        CajaAhorroResponse caja = new Gson().fromJson(json, CajaAhorroResponse.class);
        MovimientoCaja.Tipo tipo = MovimientoCaja.Tipo.RETIRO.name().equals(getIntent().getStringExtra(EXTRA_TIPO))
                ? MovimientoCaja.Tipo.RETIRO : MovimientoCaja.Tipo.DEPOSITO;
        Context app = getApplicationContext();
        OperacionMontoCaja op = new OperacionMontoCaja(tipo, caja,
                () -> RetrofitClient.getServiceSinReintentos(app), MovimientosCajaSesion.get(app), Clock.systemDefaultZone(),
                nueva -> CajasAhorroRepository.get(app).actualizar(nueva),
                () -> Refrescos.trasMoverPlata(app));
        if (guardado != null) {
            op.setMonto(guardado.getString(K_MONTO, ""));
            if (guardado.getBoolean(K_INCIERTO)) op.restaurarIncierto();
        }
        vm.operacion = op;
        return op;
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        if (operacion == null) return;
        out.putString(K_MONTO, operacion.getMontoTexto());
        // Si el proceso muere con el pedido en vuelo no sabemos cómo terminó
        out.putBoolean(K_INCIERTO, operacion.isEnviando() || operacion.getPaso() == OperacionMontoCaja.Paso.INCIERTO);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (operacion == null) return;
        SaldosRepository saldos = SaldosRepository.get(this);
        saldos.observar(observadorSaldos);
        saldos.refrescar();
        repo.observar(observadorCajas);
        repo.refrescar();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (operacion == null) return;
        SaldosRepository.get(this).dejarDeObservar(observadorSaldos);
        repo.dejarDeObservar(observadorCajas);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (operacion != null) operacion.setObservador(null);
    }

    private void configurarMonto() {
        actualizandoCampo = true;
        etMonto.setText(operacion.getMontoTexto());
        actualizandoCampo = false;
        // Bloquea el tipeo de más de 2 decimales o más de 13 enteros, y avisa
        FiltroMonto.instalar(etMonto, () -> Moneda.PESOS, operacion::avisarDecimales);
        etMonto.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (actualizandoCampo) return;
                operacion.setMonto(s.toString());
                render();
            }
        });
    }

    private void configurarBotones() {
        findViewById(R.id.btnUsarTodo).setOnClickListener(v -> {
            BigDecimal disponible = operacion.disponible(saldoPrincipal());
            if (disponible == null) {
                Toast.makeText(this, OperacionMontoCaja.MSG_SIN_SALDO, Toast.LENGTH_SHORT).show();
                return;
            }
            operacion.usarTodo(disponible);
            actualizandoCampo = true;
            etMonto.setText(operacion.getMontoTexto());
            etMonto.setSelection(etMonto.getText().length());
            actualizandoCampo = false;
        });
        btnConfirmar.setOnClickListener(v -> {
            if (operacion.esDeposito() && saldoPrincipal() == null) SaldosRepository.get(this).refrescar();
            operacion.confirmar(saldoPrincipal());
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

    private void intentarCerrar() {
        if (operacion.isEnviando()) {
            Toast.makeText(this, "Esperá a que termine la operación", Toast.LENGTH_SHORT).show();
            return;
        }
        finish();
    }

    /** Saldo en pesos de la cuenta, o null si todavía no se cargó (nunca un 0 inventado). */
    private BigDecimal saldoPrincipal() {
        return estadoSaldos != null && estadoSaldos.perfil != null ? estadoSaldos.perfil.getSaldoPesos() : null;
    }

    // ── Dibujo ────────────────────────────────────────────────────────────────

    private void render() {
        if (isFinishing() || seccionFormulario == null) return;
        CajaAhorroResponse caja = operacion.getCaja();
        boolean formulario = operacion.getPaso() == OperacionMontoCaja.Paso.FORMULARIO;
        seccionFormulario.setVisibility(formulario ? View.VISIBLE : View.GONE);
        seccionResultado.setVisibility(formulario ? View.GONE : View.VISIBLE);
        findViewById(R.id.fondoIcono).setBackgroundTintList(EstiloCaja.tinte(caja.getColor()));
        ((ImageView) findViewById(R.id.ivIcono)).setImageResource(EstiloCaja.icono(caja.getIcono()));
        ((TextView) findViewById(R.id.tvSubtitulo)).setText(caja.getNombre());
        if (formulario) renderFormulario(caja);
        else renderResultado();
    }

    private void renderFormulario(CajaAhorroResponse caja) {
        boolean deposito = operacion.esDeposito();
        BigDecimal disponible = operacion.disponible(saldoPrincipal());
        tvDisponible.setText(disponible != null ? "$ " + MontoFormatter.fiat(disponible) : MontoFormatter.SIN_DATO);
        BigDecimal monto = operacion.getMonto();
        BigDecimal saldoCaja = caja.getSaldo() != null ? caja.getSaldo() : BigDecimal.ZERO;
        if (monto != null && monto.signum() > 0) {
            BigDecimal luego = deposito ? saldoCaja.add(monto) : saldoCaja.subtract(monto);
            tvLuego.setText("La caja va a quedar con $ " + MontoFormatter.fiat(luego));
        } else {
            tvLuego.setText("Saldo actual de la caja: $ " + MontoFormatter.fiat(saldoCaja));
        }
        String error = operacion.getError();
        tvError.setVisibility(error != null ? View.VISIBLE : View.GONE);
        tvError.setText(error);
        boolean enviando = operacion.isEnviando();
        btnConfirmar.setEnabled(!enviando);
        btnConfirmar.setAlpha(enviando ? 0.5f : 1f);
        btnConfirmar.setText(enviando ? "Procesando..." : deposito ? "Agregar" : "Retirar");
        etMonto.setEnabled(!enviando);
    }

    private void renderResultado() {
        if (operacion.getPaso() == OperacionMontoCaja.Paso.EXITO) {
            CajaAhorroResponse r = operacion.getResultado();
            boolean deposito = operacion.esDeposito();
            ivIconoResultado.setImageResource(R.drawable.ic_check);
            ivIconoResultado.setImageTintList(getColorStateList(R.color.color_positivo));
            tvTituloResultado.setText(operacion.isMetaAlcanzada() ? "¡Llegaste a tu meta!" : "¡Listo!");
            String monto = "$ " + MontoFormatter.fiat(operacion.getMontoEnviado());
            tvTextoResultado.setText(deposito ? "Agregaste " + monto + " a " + r.getNombre()
                    : "Retiraste " + monto + " de " + r.getNombre() + ". Ya está en tu cuenta.");
            // Saldo REAL devuelto por el backend
            String detalle = "Saldo de la caja: $ " + MontoFormatter.fiat(r.getSaldo());
            if (r.getMontoObjetivo() != null) detalle += " de $ " + MontoFormatter.fiat(r.getMontoObjetivo());
            tvDetalleResultado.setVisibility(View.VISIBLE);
            tvDetalleResultado.setText(detalle);
        } else {
            ivIconoResultado.setImageResource(R.drawable.ic_clock);
            ivIconoResultado.setImageTintList(getColorStateList(R.color.color_servicio_luz));
            tvTituloResultado.setText("No sabemos si se realizó");
            tvTextoResultado.setText(operacion.getError());
            tvDetalleResultado.setVisibility(View.GONE);
        }
    }
}
