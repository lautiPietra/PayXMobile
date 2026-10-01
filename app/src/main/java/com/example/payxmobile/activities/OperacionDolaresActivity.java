package com.example.payxmobile.activities;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import com.example.payxmobile.dolares.CambioDolares;
import com.example.payxmobile.dolares.CambiosDolaresRepository;
import com.example.payxmobile.dolares.CotizacionDolarRepository;
import com.example.payxmobile.dolares.DolaresViewModel;
import com.example.payxmobile.model.CotizacionDolar;
import com.example.payxmobile.model.OperacionCambioResponse;
import com.example.payxmobile.network.RetrofitClient;
import com.example.payxmobile.saldos.EstadoSaldos;
import com.example.payxmobile.saldos.SaldosRepository;
import com.example.payxmobile.transferencias.Refrescos;
import com.example.payxmobile.transferencias.ui.FiltroMonto;
import com.example.payxmobile.utils.MontoFormatter;
import com.example.payxmobile.utils.SesionUtils;

import java.math.BigDecimal;

/**
 * Comprar / vender dólares al instante (CambioDolaresModal.jsx). EXTRA_TIPO: "COMPRAR" (por
 * defecto) o "VENDER". Toda la lógica vive en {@link CambioDolares}; esta pantalla la dibuja.
 */
public class OperacionDolaresActivity extends AppCompatActivity {

    public static final String EXTRA_TIPO = "tipo";
    public static final String COMPRAR = "COMPRAR";
    public static final String VENDER = "VENDER";

    /** Cotización cada 10 s mientras la pantalla está visible (límite del endpoint: 30/min). */
    private static final long INTERVALO_COTIZACION_MS = 10_000L;

    private static final String K_MONTO = "d_monto", K_PASO = "d_paso", K_USD = "d_usd", K_PESOS = "d_pesos",
            K_COTIZ = "d_cotiz", K_TIPO_RES = "d_tipo_res";

    private CambioDolares cambio;
    private EstadoSaldos estadoSaldos;
    private final SaldosRepository.Observador observadorSaldos = e -> {
        estadoSaldos = e;
        if (e.sesionInvalida) {
            SesionUtils.sesionInvalida(this);
            return;
        }
        render();
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable pollCotizacion = new Runnable() {
        @Override
        public void run() {
            cambio.cargarCotizacion();
            handler.postDelayed(this, INTERVALO_COTIZACION_MS);
        }
    };

    private View seccionFormulario, seccionConfirmar, seccionResultado;
    private EditText etMonto;
    private TextView tvPrecioCompra, tvPrecioVenta, tvEstadoCotizacion, tvMontoResumen, tvRecibis, tvCotizacion,
            tvSaldoOrigen, tvSaldoLuego, tvSaldoDisponible, tvNota, tvError, tvTituloResultado, tvTextoResultado,
            tvDetalleResultado, tvTituloConfirmar, tvLabelMontoConfirmar, tvMontoConfirmar, tvRecibisConfirmar,
            tvCotizacionConfirmar, tvSaldoLuegoConfirmar, tvErrorConfirmar;
    private ImageView ivIconoResultado;
    private Button btnOperar, btnConfirmar, btnModificar;
    private boolean actualizandoCampo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_operacion_dolares);

        CambioDolares.Tipo tipo = VENDER.equals(getIntent().getStringExtra(EXTRA_TIPO))
                ? CambioDolares.Tipo.VENTA : CambioDolares.Tipo.COMPRA;
        cambio = obtenerCambio(new ViewModelProvider(this).get(DolaresViewModel.class), tipo, savedInstanceState);
        cambio.setObservador(c -> render());

        vincularVistas();
        configurarTextosFijos();
        configurarMonto();
        configurarBotones();
        render();
    }

    /** Vive en el ViewModel (rotación, con request en vuelo incluida). Si el proceso murió, se rearma. */
    private CambioDolares obtenerCambio(DolaresViewModel vm, CambioDolares.Tipo tipo, Bundle guardado) {
        if (vm.cambio != null) return vm.cambio;
        Context app = getApplicationContext();
        CambioDolares nuevo = new CambioDolares(tipo,
                () -> RetrofitClient.getService(app),
                () -> RetrofitClient.getServiceSinReintentos(app),
                () -> Refrescos.trasMoverPlata(app));
        if (guardado != null) {
            nuevo.setMonto(guardado.getString(K_MONTO, ""));
            String paso = guardado.getString(K_PASO);
            if (paso != null) {
                OperacionCambioResponse res = null;
                if (guardado.getString(K_USD) != null) {
                    res = new OperacionCambioResponse(null, guardado.getString(K_TIPO_RES),
                            new BigDecimal(guardado.getString(K_USD)), new BigDecimal(guardado.getString(K_PESOS)),
                            new BigDecimal(guardado.getString(K_COTIZ)), null);
                }
                nuevo.restaurar(CambioDolares.Paso.valueOf(paso), res);
            }
        }
        // Precio al instante con la última cotización precargada (Inicio / Inversiones); onStart la refresca
        nuevo.usarCotizacionConocida(CotizacionDolarRepository.get(app).getVigente());
        vm.cambio = nuevo;
        return nuevo;
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putString(K_MONTO, cambio.getMontoTexto());
        // Si el proceso muere con el POST en vuelo no sabemos cómo terminó: al volver, INCIERTO
        CambioDolares.Paso paso = cambio.isEnviando() ? CambioDolares.Paso.INCIERTO : cambio.getPaso();
        out.putString(K_PASO, paso.name());
        OperacionCambioResponse r = cambio.getResultado();
        if (paso == CambioDolares.Paso.EXITO && r != null && r.getMontoUsd() != null
                && r.getMontoPesos() != null && r.getCotizacion() != null) {
            out.putString(K_TIPO_RES, r.getTipo());
            out.putString(K_USD, r.getMontoUsd().toPlainString());
            out.putString(K_PESOS, r.getMontoPesos().toPlainString());
            out.putString(K_COTIZ, r.getCotizacion().toPlainString());
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        SaldosRepository saldos = SaldosRepository.get(this);
        saldos.observar(observadorSaldos);
        saldos.refrescar();
        handler.post(pollCotizacion); // YA y cada 10 s
    }

    @Override
    protected void onStop() {
        super.onStop();
        handler.removeCallbacks(pollCotizacion);
        SaldosRepository.get(this).dejarDeObservar(observadorSaldos);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // La Activity nueva (rotación) se registra en su onCreate; esta ya no dibuja nada
        cambio.setObservador(null);
    }

    // ── Vistas ────────────────────────────────────────────────────────────────

    private void vincularVistas() {
        seccionFormulario = findViewById(R.id.seccionFormulario);
        seccionResultado = findViewById(R.id.seccionResultado);
        seccionConfirmar = findViewById(R.id.seccionConfirmar);
        tvTituloConfirmar = findViewById(R.id.tvTituloConfirmar);
        tvLabelMontoConfirmar = findViewById(R.id.tvLabelMontoConfirmar);
        tvMontoConfirmar = findViewById(R.id.tvMontoConfirmar);
        tvRecibisConfirmar = findViewById(R.id.tvRecibisConfirmar);
        tvCotizacionConfirmar = findViewById(R.id.tvCotizacionConfirmar);
        tvSaldoLuegoConfirmar = findViewById(R.id.tvSaldoLuegoConfirmar);
        tvErrorConfirmar = findViewById(R.id.tvErrorConfirmar);
        btnConfirmar = findViewById(R.id.btnConfirmar);
        btnModificar = findViewById(R.id.btnModificar);
        etMonto = findViewById(R.id.etMonto);
        tvPrecioCompra = findViewById(R.id.tvPrecioCompra);
        tvPrecioVenta = findViewById(R.id.tvPrecioVenta);
        tvEstadoCotizacion = findViewById(R.id.tvEstadoCotizacion);
        tvMontoResumen = findViewById(R.id.tvMontoResumen);
        tvRecibis = findViewById(R.id.tvRecibis);
        tvCotizacion = findViewById(R.id.tvCotizacion);
        tvSaldoOrigen = findViewById(R.id.tvSaldoOrigen);
        tvSaldoLuego = findViewById(R.id.tvSaldoLuego);
        tvSaldoDisponible = findViewById(R.id.tvSaldoDisponible);
        tvNota = findViewById(R.id.tvNota);
        tvError = findViewById(R.id.tvError);
        tvTituloResultado = findViewById(R.id.tvTituloResultado);
        tvTextoResultado = findViewById(R.id.tvTextoResultado);
        tvDetalleResultado = findViewById(R.id.tvDetalleResultado);
        ivIconoResultado = findViewById(R.id.ivIconoResultado);
        btnOperar = findViewById(R.id.btnOperar);
    }

    private void configurarTextosFijos() {
        boolean compra = cambio.esCompra();
        String saldoLabel = compra ? "Saldo disponible en pesos" : "Saldo disponible en dólares";
        String simbolo = cambio.getMonedaEntrada().simbolo;
        ((TextView) findViewById(R.id.tvTitulo)).setText(compra ? "Comprar dólares" : "Vender dólares");
        ((TextView) findViewById(R.id.tvSubtitulo)).setText(compra
                ? "Comprá dólares al instante con el saldo de tu cuenta en pesos"
                : "Vendé tus dólares y recibí el dinero al instante en tu cuenta en pesos");
        ((TextView) findViewById(R.id.tvLabelVasA)).setText(compra ? "Vas a pagar" : "Vas a vender");
        ((TextView) findViewById(R.id.tvSimboloResumen)).setText(simbolo);
        ((TextView) findViewById(R.id.tvLabelSaldoOrigen)).setText(saldoLabel);
        ((TextView) findViewById(R.id.tvLabelSaldoDisponible)).setText(saldoLabel);
        ((TextView) findViewById(R.id.tvLabelMonto)).setText(compra ? "Monto en pesos a destinar" : "Monto de dólares a vender");
        ((TextView) findViewById(R.id.tvSimboloInput)).setText(simbolo);
        btnOperar.setCompoundDrawablesWithIntrinsicBounds(
                compra ? R.drawable.ic_arrow_down_circle : R.drawable.ic_arrow_up_circle, 0, 0, 0);
    }

    private void configurarMonto() {
        actualizandoCampo = true;
        etMonto.setText(cambio.getMontoTexto());
        actualizandoCampo = false;
        // Bloquea el tipeo de más de 2 decimales o más de 13 enteros, y avisa
        FiltroMonto.instalar(etMonto, cambio::getMonedaEntrada, cambio::avisarDecimales);
        etMonto.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (actualizandoCampo) return;
                cambio.setMonto(s.toString());
                render();
            }
        });
    }

    private void configurarBotones() {
        findViewById(R.id.btnUsarTodo).setOnClickListener(v -> {
            BigDecimal saldo = saldoDisponible();
            if (saldo == null) {
                Toast.makeText(this, CambioDolares.MSG_SIN_SALDO, Toast.LENGTH_SHORT).show();
                return;
            }
            cambio.usarTodo(saldo);
            actualizandoCampo = true;
            etMonto.setText(cambio.getMontoTexto());
            etMonto.setSelection(etMonto.getText().length());
            actualizandoCampo = false;
        });
        // "Comprar/Vender dólares" solo valida y muestra el resumen: la plata se mueve en "Confirmar"
        btnOperar.setOnClickListener(v -> {
            if (saldoDisponible() == null) SaldosRepository.get(this).refrescar();
            cambio.continuar(saldoDisponible());
        });
        btnConfirmar.setOnClickListener(v -> cambio.confirmar());
        btnModificar.setOnClickListener(v -> cambio.editar());
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

    /** Mientras se opera no se deja salir: el resultado se perdería de vista. */
    private void intentarCerrar() {
        if (cambio.isEnviando()) {
            Toast.makeText(this, "Esperá a que termine la operación", Toast.LENGTH_SHORT).show();
            return;
        }
        // Desde el resumen, "atrás" vuelve al formulario (como "Modificar")
        if (cambio.getPaso() == CambioDolares.Paso.CONFIRMAR) {
            cambio.editar();
            return;
        }
        finish();
    }

    /** Saldo de la moneda que se entrega, o null si todavía no se cargó (nunca un 0 inventado). */
    private BigDecimal saldoDisponible() {
        if (estadoSaldos == null || estadoSaldos.perfil == null) return null;
        return cambio.getMonedaEntrada().saldoEn(estadoSaldos.perfil);
    }

    // ── Dibujo ────────────────────────────────────────────────────────────────

    private void render() {
        if (isFinishing() || seccionFormulario == null) return;
        CambioDolares.Paso paso = cambio.getPaso();
        boolean formulario = paso == CambioDolares.Paso.FORMULARIO;
        boolean confirmar = paso == CambioDolares.Paso.CONFIRMAR;
        seccionFormulario.setVisibility(formulario ? View.VISIBLE : View.GONE);
        seccionConfirmar.setVisibility(confirmar ? View.VISIBLE : View.GONE);
        seccionResultado.setVisibility(formulario || confirmar ? View.GONE : View.VISIBLE);
        // La cotización buena queda en memoria para la próxima vez que se abra la pantalla
        CotizacionDolarRepository.get(this).guardar(cambio.getCotizacion());
        if (formulario) renderFormulario();
        else if (confirmar) renderConfirmar();
        else renderResultado();
        // La operación recién hecha aparece YA en Inicio y "Mis movimientos" (sin esperar al GET)
        if (paso == CambioDolares.Paso.EXITO) CambiosDolaresRepository.get(this).agregar(cambio.getResultado());
    }

    private void renderConfirmar() {
        boolean compra = cambio.esCompra();
        String simbolo = cambio.getMonedaEntrada().simbolo;
        BigDecimal monto = cambio.getMontoAConfirmar();
        BigDecimal precio = cambio.getPrecioAplicado();
        BigDecimal recibis = CambioDolares.preview(cambio.getTipo(), monto, precio);
        tvTituloConfirmar.setText(compra ? "Confirmá la compra" : "Confirmá la venta");
        tvLabelMontoConfirmar.setText(compra ? "Vas a pagar" : "Vas a vender");
        tvMontoConfirmar.setText(simbolo + " " + MontoFormatter.fiat(monto));
        tvRecibisConfirmar.setText(recibis != null
                ? (compra ? "US$ " : "$ ") + MontoFormatter.fiat(recibis) : MontoFormatter.SIN_DATO);
        tvCotizacionConfirmar.setText(precio != null
                ? "$ " + MontoFormatter.fiat(precio) + " por dólar" : MontoFormatter.SIN_DATO);
        BigDecimal saldo = saldoDisponible();
        tvSaldoLuegoConfirmar.setText(saldo != null && monto != null
                ? simbolo + " " + MontoFormatter.fiat(saldo.subtract(monto)) : MontoFormatter.SIN_DATO);

        String error = cambio.getError();
        tvErrorConfirmar.setVisibility(error != null ? View.VISIBLE : View.GONE);
        tvErrorConfirmar.setText(error);
        boolean enviando = cambio.isEnviando();
        btnConfirmar.setEnabled(!enviando && precio != null);
        btnConfirmar.setAlpha(btnConfirmar.isEnabled() ? 1f : 0.5f);
        btnConfirmar.setText(enviando ? "Procesando..." : compra ? "Confirmar compra" : "Confirmar venta");
        btnModificar.setEnabled(!enviando);
    }

    private void renderFormulario() {
        boolean compra = cambio.esCompra();
        String simbolo = cambio.getMonedaEntrada().simbolo;

        // Ticker: los dos precios oficiales (sin cotización: "—", nunca 0)
        CotizacionDolar c = cambio.getCotizacion();
        BigDecimal precioCompra = CambioDolares.precioPara(CambioDolares.Tipo.VENTA, c);
        BigDecimal precioVenta = CambioDolares.precioPara(CambioDolares.Tipo.COMPRA, c);
        tvPrecioCompra.setText(precioCompra != null ? "$ " + MontoFormatter.fiat(precioCompra) : MontoFormatter.SIN_DATO);
        tvPrecioVenta.setText(precioVenta != null ? "$ " + MontoFormatter.fiat(precioVenta) : MontoFormatter.SIN_DATO);
        String estadoCotizacion = cambio.isCargandoCotizacion() ? "Buscando la cotización actual..."
                : c == null ? cambio.getErrorCotizacion()
                : c.isDesactualizada() ? "Sin poder actualizar la cotización en este momento" : null;
        tvEstadoCotizacion.setVisibility(estadoCotizacion != null ? View.VISIBLE : View.GONE);
        tvEstadoCotizacion.setText(estadoCotizacion);

        // Resumen
        BigDecimal precio = cambio.getPrecioAplicado();
        BigDecimal monto = cambio.getMonto();
        tvMontoResumen.setText(MontoFormatter.fiat(monto != null && monto.signum() > 0 ? monto : BigDecimal.ZERO));
        BigDecimal recibis = cambio.getPreview();
        tvRecibis.setVisibility(recibis != null ? View.VISIBLE : View.GONE);
        if (recibis != null) {
            tvRecibis.setText("Recibís " + (compra ? "US$ " : "$ ") + MontoFormatter.fiat(recibis));
        }
        tvCotizacion.setText(precio != null ? "$ " + MontoFormatter.fiat(precio) : MontoFormatter.SIN_DATO);
        tvNota.setText(precio != null
                ? "Cotización de " + (compra ? "venta" : "compra") + ": $ " + MontoFormatter.fiat(precio)
                + " por dólar (oficial)" + (c != null && c.isDesactualizada() ? " · sin poder actualizar en este momento" : "")
                : cambio.isCargandoCotizacion() ? "Buscando la cotización actual..." : "No pudimos obtener la cotización.");

        BigDecimal saldo = saldoDisponible();
        String saldoTexto = saldo != null ? simbolo + " " + MontoFormatter.fiat(saldo) : MontoFormatter.SIN_DATO;
        tvSaldoOrigen.setText(saldoTexto);
        tvSaldoDisponible.setText(saldoTexto);
        BigDecimal luego = cambio.saldoLuego(saldo);
        tvSaldoLuego.setText(luego != null ? simbolo + " " + MontoFormatter.fiat(luego) : MontoFormatter.SIN_DATO);
        tvSaldoLuego.setTextColor(getColor(luego != null && luego.signum() < 0 ? R.color.color_negativo : R.color.text_primary));

        // Error y botón
        String error = cambio.getError();
        tvError.setVisibility(error != null ? View.VISIBLE : View.GONE);
        tvError.setText(error);
        boolean enviando = cambio.isEnviando();
        btnOperar.setEnabled(!enviando && precio != null);
        btnOperar.setAlpha(btnOperar.isEnabled() ? 1f : 0.5f);
        btnOperar.setText(enviando ? "Procesando..." : compra ? "Comprar dólares" : "Vender dólares");
        etMonto.setEnabled(!enviando);
    }

    private void renderResultado() {
        if (cambio.getPaso() == CambioDolares.Paso.EXITO) {
            OperacionCambioResponse r = cambio.getResultado();
            boolean compra = r.getTipo() != null ? r.esCompra() : cambio.esCompra();
            ivIconoResultado.setImageResource(R.drawable.ic_check);
            ivIconoResultado.setImageTintList(getColorStateList(R.color.color_positivo));
            tvTituloResultado.setText(compra ? "¡Compra realizada!" : "¡Venta realizada!");
            // Montos REALES devueltos por el backend, no el preview
            String usd = "US$ " + MontoFormatter.fiat(r.getMontoUsd());
            String pesos = "$ " + MontoFormatter.fiat(r.getMontoPesos());
            tvTextoResultado.setText(compra
                    ? "Compraste " + usd + " pagando " + pesos
                    : "Vendiste " + usd + " y recibiste " + pesos);
            tvDetalleResultado.setVisibility(View.VISIBLE);
            tvDetalleResultado.setText("Cotización $ " + MontoFormatter.fiat(r.getCotizacion()) + " por dólar");
        } else {
            ivIconoResultado.setImageResource(R.drawable.ic_clock);
            ivIconoResultado.setImageTintList(getColorStateList(R.color.color_servicio_luz));
            tvTituloResultado.setText("No sabemos si se realizó");
            tvTextoResultado.setText(cambio.getError());
            tvDetalleResultado.setVisibility(View.GONE);
        }
    }
}
