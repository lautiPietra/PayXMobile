package com.example.payxmobile.activities;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.payxmobile.R;
import com.example.payxmobile.cripto.CambiosCriptoRepository;
import com.example.payxmobile.cripto.CriptoViewModel;
import com.example.payxmobile.cripto.OperacionCripto;
import com.example.payxmobile.cripto.ui.AnimadorPrecios;
import com.example.payxmobile.model.OperacionCriptoResponse;
import com.example.payxmobile.network.RetrofitClient;
import com.example.payxmobile.saldos.EstadoSaldos;
import com.example.payxmobile.saldos.SaldosRepository;
import com.example.payxmobile.transferencias.Moneda;
import com.example.payxmobile.transferencias.MontoInput;
import com.example.payxmobile.transferencias.Refrescos;
import com.example.payxmobile.utils.MontoFormatter;
import com.example.payxmobile.utils.SesionUtils;

import java.math.BigDecimal;

/**
 * Comprar / vender cripto (CriptoModal.jsx + paso de confirmación). EXTRA_TIPO: "COMPRAR" (por
 * defecto) o "VENDER". La lógica vive en {@link OperacionCripto}; saldos y cotizaciones salen de
 * {@link SaldosRepository} (las mismas del ticker de Inicio: no se duplica el pedido).
 */
public class OperacionCriptoActivity extends AppCompatActivity {

    public static final String EXTRA_TIPO = "tipo";
    public static final String VENDER = "VENDER";

    private static final String K_MONTO = "c_monto", K_CRIPTO = "c_cripto", K_PASO = "c_paso",
            K_CANT = "c_cant", K_PESOS = "c_pesos", K_COTIZ = "c_cotiz", K_TIPO_RES = "c_tipo_res", K_SIMB = "c_simb";

    private OperacionCripto op;
    private EstadoSaldos estadoSaldos;
    private final SaldosRepository.Observador observadorSaldos = e -> {
        estadoSaldos = e;
        if (e.sesionInvalida) {
            SesionUtils.sesionInvalida(this);
            return;
        }
        op.setCotizaciones(e.cotizaciones, e.sinCotizacionesEnBackend);
        render();
    };

    private View seccionFormulario, seccionConfirmar, seccionResultado;
    private LinearLayout layoutCriptos;
    private EditText etMonto;
    private TextView tvEstadoCotizacion, tvMontoResumen, tvRecibis, tvPrecioSeleccionado, tvSaldoOrigen, tvSaldoLuego,
            tvSaldoDisponible, tvNota, tvError, tvMonedaSeleccionada, tvSimboloResumen, tvSimboloInput, tvLabelPrecio,
            tvLabelSaldoOrigen, tvLabelSaldoDisponible, tvLabelMonto, tvTituloConfirmar, tvLabelMontoConfirmar,
            tvMontoConfirmar, tvRecibisConfirmar, tvLabelPrecioConfirmar, tvCotizacionConfirmar,
            tvSaldoLuegoConfirmar, tvErrorConfirmar, tvTituloResultado, tvTextoResultado, tvDetalleResultado;
    private ImageView ivIconoResultado;
    private Button btnOperar, btnConfirmar, btnModificar;
    private boolean actualizandoCampo;
    private CriptoViewModel vm;
    private AnimadorPrecios animador;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_operacion_cripto);

        OperacionCripto.Tipo tipo = VENDER.equals(getIntent().getStringExtra(EXTRA_TIPO))
                ? OperacionCripto.Tipo.VENTA : OperacionCripto.Tipo.COMPRA;
        vm = new ViewModelProvider(this).get(CriptoViewModel.class);
        animador = new AnimadorPrecios(this);
        op = obtenerOperacion(vm, tipo, savedInstanceState);
        op.setObservador(o -> render());

        vincularVistas();
        armarChips();
        configurarTextosFijos();
        configurarMonto();
        configurarBotones();
        render();
    }

    private OperacionCripto obtenerOperacion(CriptoViewModel vm, OperacionCripto.Tipo tipo, Bundle guardado) {
        if (vm.operacion != null) return vm.operacion;
        Context app = getApplicationContext();
        OperacionCripto nueva = new OperacionCripto(tipo,
                () -> RetrofitClient.getServiceSinReintentos(app),
                () -> Refrescos.trasMoverPlata(app));
        // Precios al instante: los que ya tiene el repositorio de saldos (ticker de Inicio)
        EstadoSaldos e = SaldosRepository.get(app).getEstado();
        nueva.setCotizaciones(e.cotizaciones, e.sinCotizacionesEnBackend);
        if (guardado != null) {
            Moneda cripto = Moneda.desde(guardado.getString(K_CRIPTO));
            if (cripto != null) nueva.setCripto(cripto);
            nueva.setMonto(guardado.getString(K_MONTO, ""));
            String paso = guardado.getString(K_PASO);
            if (paso != null) {
                OperacionCriptoResponse res = null;
                if (guardado.getString(K_CANT) != null) {
                    res = new OperacionCriptoResponse(null, guardado.getString(K_TIPO_RES), guardado.getString(K_SIMB),
                            new BigDecimal(guardado.getString(K_CANT)), new BigDecimal(guardado.getString(K_PESOS)),
                            new BigDecimal(guardado.getString(K_COTIZ)), null);
                }
                nueva.restaurar(cripto, OperacionCripto.Paso.valueOf(paso), res);
            }
        }
        vm.operacion = nueva;
        return nueva;
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putString(K_CRIPTO, op.getCripto().codigo());
        out.putString(K_MONTO, op.getMontoAConfirmar() != null && op.getPaso() != OperacionCripto.Paso.FORMULARIO
                ? MontoInput.aTexto(op.getMontoAConfirmar(), op.getMonedaEntrada()) : op.getMontoTexto());
        // Si el proceso muere con el POST en vuelo no sabemos cómo terminó: al volver, INCIERTO
        OperacionCripto.Paso paso = op.isEnviando() ? OperacionCripto.Paso.INCIERTO : op.getPaso();
        out.putString(K_PASO, paso.name());
        OperacionCriptoResponse r = op.getResultado();
        if (paso == OperacionCripto.Paso.EXITO && r != null && r.getMontoCripto() != null
                && r.getMontoPesos() != null && r.getCotizacion() != null) {
            out.putString(K_TIPO_RES, r.getTipo());
            out.putString(K_SIMB, r.getSimbolo());
            out.putString(K_CANT, r.getMontoCripto().toPlainString());
            out.putString(K_PESOS, r.getMontoPesos().toPlainString());
            out.putString(K_COTIZ, r.getCotizacion().toPlainString());
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        SaldosRepository saldos = SaldosRepository.get(this);
        saldos.observar(observadorSaldos);
        // Precio y saldo YA, y después el mismo auto-refresco que Inicio (saldo 10 s, cotizaciones 20 s)
        saldos.refrescarTodo();
        saldos.iniciarAutoRefresco();
        saldos.iniciarCotizacionesRapidas(); // precios en vivo: cada 3 s mientras se ve esta pantalla
    }

    @Override
    protected void onStop() {
        super.onStop();
        SaldosRepository saldos = SaldosRepository.get(this);
        saldos.detenerCotizacionesRapidas();
        saldos.detenerAutoRefresco();
        saldos.dejarDeObservar(observadorSaldos);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        op.setObservador(null);
        animador.cancelarTodo();
    }

    // ── Vistas ────────────────────────────────────────────────────────────────

    private void vincularVistas() {
        seccionFormulario = findViewById(R.id.seccionFormulario);
        seccionConfirmar = findViewById(R.id.seccionConfirmar);
        seccionResultado = findViewById(R.id.seccionResultado);
        layoutCriptos = findViewById(R.id.layoutCriptos);
        etMonto = findViewById(R.id.etMonto);
        tvEstadoCotizacion = findViewById(R.id.tvEstadoCotizacion);
        tvMontoResumen = findViewById(R.id.tvMontoResumen);
        tvRecibis = findViewById(R.id.tvRecibis);
        tvPrecioSeleccionado = findViewById(R.id.tvPrecioSeleccionado);
        tvSaldoOrigen = findViewById(R.id.tvSaldoOrigen);
        tvSaldoLuego = findViewById(R.id.tvSaldoLuego);
        tvSaldoDisponible = findViewById(R.id.tvSaldoDisponible);
        tvNota = findViewById(R.id.tvNota);
        tvError = findViewById(R.id.tvError);
        tvMonedaSeleccionada = findViewById(R.id.tvMonedaSeleccionada);
        tvSimboloResumen = findViewById(R.id.tvSimboloResumen);
        tvSimboloInput = findViewById(R.id.tvSimboloInput);
        tvLabelPrecio = findViewById(R.id.tvLabelPrecio);
        tvLabelSaldoOrigen = findViewById(R.id.tvLabelSaldoOrigen);
        tvLabelSaldoDisponible = findViewById(R.id.tvLabelSaldoDisponible);
        tvLabelMonto = findViewById(R.id.tvLabelMonto);
        tvTituloConfirmar = findViewById(R.id.tvTituloConfirmar);
        tvLabelMontoConfirmar = findViewById(R.id.tvLabelMontoConfirmar);
        tvMontoConfirmar = findViewById(R.id.tvMontoConfirmar);
        tvRecibisConfirmar = findViewById(R.id.tvRecibisConfirmar);
        tvLabelPrecioConfirmar = findViewById(R.id.tvLabelPrecioConfirmar);
        tvCotizacionConfirmar = findViewById(R.id.tvCotizacionConfirmar);
        tvSaldoLuegoConfirmar = findViewById(R.id.tvSaldoLuegoConfirmar);
        tvErrorConfirmar = findViewById(R.id.tvErrorConfirmar);
        tvTituloResultado = findViewById(R.id.tvTituloResultado);
        tvTextoResultado = findViewById(R.id.tvTextoResultado);
        tvDetalleResultado = findViewById(R.id.tvDetalleResultado);
        ivIconoResultado = findViewById(R.id.ivIconoResultado);
        btnOperar = findViewById(R.id.btnOperar);
        btnConfirmar = findViewById(R.id.btnConfirmar);
        btnModificar = findViewById(R.id.btnModificar);
    }

    /** Chips BTC..XRP, como en saldos y transferencias. Cambiar de cripto borra el monto. */
    private void armarChips() {
        layoutCriptos.removeAllViews();
        for (Moneda m : Moneda.values()) {
            if (!m.esCripto()) continue;
            TextView chip = new TextView(this);
            chip.setText(m.simbolo);
            chip.setTextSize(12.5f);
            chip.setTypeface(null, Typeface.BOLD);
            chip.setBackgroundResource(R.drawable.bg_coin_chip);
            chip.setTextColor(getColorStateList(R.color.coin_chip_text));
            chip.setPadding(dp(16), dp(8), dp(16), dp(8));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(8));
            chip.setLayoutParams(lp);
            chip.setTag(m);
            chip.setOnClickListener(v -> {
                op.setCripto(m);
                ponerTextoMonto(op.getMontoTexto());
                render();
            });
            layoutCriptos.addView(chip);
        }
    }

    private void configurarTextosFijos() {
        boolean compra = op.esCompra();
        ((TextView) findViewById(R.id.tvTitulo)).setText(compra ? "Comprar criptomonedas" : "Vender criptomonedas");
        ((TextView) findViewById(R.id.tvSubtitulo)).setText(compra
                ? "Elegí qué criptomoneda comprar y cuánto querés invertir"
                : "Elegí qué criptomoneda vender y cuánto querés recibir");
        ((TextView) findViewById(R.id.tvLabelVasA)).setText(compra ? "Vas a pagar" : "Vas a vender");
        btnOperar.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_coins, 0, 0, 0);
    }

    private void configurarMonto() {
        ponerTextoMonto(op.getMontoTexto());
        // 2 decimales al pagar en pesos, 8 al vender cripto; 13 enteros como máximo
        etMonto.setFilters(new InputFilter[]{(fuente, inicio, fin, destino, dInicio, dFin) -> {
            String resultado = destino.subSequence(0, dInicio) + fuente.subSequence(inicio, fin).toString()
                    + destino.subSequence(dFin, destino.length());
            return MontoInput.esTipeoValido(resultado, op.getMonedaEntrada()) ? null : "";
        }});
        etMonto.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (actualizandoCampo) return;
                op.setMonto(s.toString());
                render();
            }
        });
    }

    private void ponerTextoMonto(String texto) {
        actualizandoCampo = true;
        etMonto.setText(texto);
        etMonto.setSelection(etMonto.getText().length());
        actualizandoCampo = false;
    }

    private void configurarBotones() {
        findViewById(R.id.btnUsarTodo).setOnClickListener(v -> {
            BigDecimal saldo = saldoDisponible();
            if (saldo == null) {
                Toast.makeText(this, OperacionCripto.MSG_SIN_SALDO, Toast.LENGTH_SHORT).show();
                return;
            }
            op.usarTodo(saldo); // saldo EXACTO de la cripto elegida (8 decimales)
            ponerTextoMonto(op.getMontoTexto());
        });
        // "Comprar/Vender X" solo valida y muestra el resumen: la plata se mueve en "Confirmar"
        btnOperar.setOnClickListener(v -> {
            if (saldoDisponible() == null) SaldosRepository.get(this).refrescar();
            op.continuar(saldoDisponible());
        });
        btnConfirmar.setOnClickListener(v -> op.confirmar());
        btnModificar.setOnClickListener(v -> op.editar());
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
        if (op.isEnviando()) {
            Toast.makeText(this, "Esperá a que termine la operación", Toast.LENGTH_SHORT).show();
            return;
        }
        if (op.getPaso() == OperacionCripto.Paso.CONFIRMAR) {
            op.editar();
            return;
        }
        finish();
    }

    /** Saldo de lo que se entrega (pesos o ESTA cripto), o null si todavía no se cargó. */
    private BigDecimal saldoDisponible() {
        return OperacionCripto.saldoDisponible(op.getTipo(), op.getCripto(),
                estadoSaldos != null ? estadoSaldos.perfil : null);
    }

    /** "$ 1.234,56" o "0,5 BTC" (cripto sin ceros de relleno). */
    private static String formato(Moneda m, BigDecimal monto) {
        return m.esCripto() ? MontoFormatter.cripto(monto) + " " + m.codigo() : "$ " + MontoFormatter.fiat(monto);
    }

    // ── Dibujo ────────────────────────────────────────────────────────────────

    private void render() {
        if (isFinishing() || seccionFormulario == null) return;
        OperacionCripto.Paso paso = op.getPaso();
        boolean formulario = paso == OperacionCripto.Paso.FORMULARIO;
        boolean confirmar = paso == OperacionCripto.Paso.CONFIRMAR;
        seccionFormulario.setVisibility(formulario ? View.VISIBLE : View.GONE);
        seccionConfirmar.setVisibility(confirmar ? View.VISIBLE : View.GONE);
        seccionResultado.setVisibility(formulario || confirmar ? View.GONE : View.VISIBLE);
        if (formulario) renderFormulario();
        else if (confirmar) renderConfirmar();
        else renderResultado();
        // La operación recién hecha aparece YA en Inicio y "Mis movimientos"
        if (paso == OperacionCripto.Paso.EXITO) CambiosCriptoRepository.get(this).agregar(op.getResultado());
    }

    private void renderTicker() {
        int[] ids = {R.id.tvPrecioBTC, R.id.tvPrecioETH, R.id.tvPrecioSOL, R.id.tvPrecioUSDT, R.id.tvPrecioBNB, R.id.tvPrecioXRP};
        Moneda[] criptos = {Moneda.BTC, Moneda.ETH, Moneda.SOL, Moneda.USDT, Moneda.BNB, Moneda.XRP};
        for (int i = 0; i < ids.length; i++) {
            BigDecimal p = estadoSaldos != null ? OperacionCripto.precioDe(criptos[i], estadoSaldos.cotizaciones) : null;
            // Si cambió: cuenta hasta el nuevo precio con verde/rojo que aparece y se desvanece suave
            animador.mostrar(findViewById(ids[i]), criptos[i].codigo(), p, AnimadorPrecios::pesos);
        }
    }

    private void renderFormulario() {
        boolean compra = op.esCompra();
        Moneda cripto = op.getCripto();
        Moneda entrada = op.getMonedaEntrada();
        renderTicker();
        for (int i = 0; i < layoutCriptos.getChildCount(); i++) {
            View chip = layoutCriptos.getChildAt(i);
            chip.setSelected(chip.getTag() == cripto);
        }

        BigDecimal precio = op.getPrecio();
        String precioTexto = precio != null ? "$ " + MontoFormatter.fiat(precio) : MontoFormatter.SIN_DATO;
        boolean cargando = estadoSaldos == null || (estadoSaldos.cotizaciones.isEmpty() && estadoSaldos.cargandoCotizaciones);
        String estado = precio == null
                ? (cargando ? "Buscando la cotización actual..." : "No pudimos obtener la cotización de " + cripto.codigo() + ".")
                : op.isPrecioDesactualizado()
                ? "Precio de " + cripto.codigo() + " sin poder actualizar en este momento: puede que no se pueda operar"
                : null;
        tvEstadoCotizacion.setVisibility(estado != null ? View.VISIBLE : View.GONE);
        tvEstadoCotizacion.setText(estado);
        tvMonedaSeleccionada.setText(cripto.codigo() + " · " + precioTexto);
        tvLabelPrecio.setText("Precio " + cripto.codigo());
        animador.mostrar(tvPrecioSeleccionado, cripto.codigo(), precio, AnimadorPrecios::pesos);
        tvNota.setText(precio != null
                ? "Precio actual de " + cripto.codigo() + ": " + precioTexto + (op.isPrecioDesactualizado() ? " · sin poder actualizar en este momento" : "")
                : cargando ? "Buscando la cotización actual..." : "No pudimos obtener la cotización.");

        String simboloEntrada = compra ? "$" : cripto.codigo();
        tvSimboloResumen.setText(simboloEntrada);
        tvSimboloInput.setText(simboloEntrada);
        tvLabelMonto.setText(compra ? "Monto en pesos a destinar" : "Cantidad de " + cripto.codigo() + " a vender");
        String saldoLabel = compra ? "Saldo disponible en pesos" : "Saldo disponible de " + cripto.codigo();
        tvLabelSaldoOrigen.setText(saldoLabel);
        tvLabelSaldoDisponible.setText(saldoLabel);

        BigDecimal monto = op.getMonto();
        BigDecimal montoMostrado = monto != null && monto.signum() > 0 ? monto : BigDecimal.ZERO;
        tvMontoResumen.setText(compra ? MontoFormatter.fiat(montoMostrado) : MontoFormatter.cripto(montoMostrado));
        BigDecimal recibis = op.getPreview();
        tvRecibis.setVisibility(recibis != null ? View.VISIBLE : View.GONE);
        if (recibis != null) {
            tvRecibis.setText("Recibís ≈ " + (compra ? formato(cripto, recibis) : formato(Moneda.PESOS, recibis)));
        }

        BigDecimal saldo = saldoDisponible();
        String saldoTexto = saldo != null ? formato(entrada, saldo) : MontoFormatter.SIN_DATO;
        tvSaldoOrigen.setText(saldoTexto);
        tvSaldoDisponible.setText(saldoTexto);
        BigDecimal luego = saldo != null ? (monto != null ? saldo.subtract(monto) : saldo) : null;
        tvSaldoLuego.setText(luego != null ? formato(entrada, luego) : MontoFormatter.SIN_DATO);
        tvSaldoLuego.setTextColor(getColor(luego != null && luego.signum() < 0 ? R.color.color_negativo : R.color.text_primary));

        String error = op.getError();
        tvError.setVisibility(error != null ? View.VISIBLE : View.GONE);
        tvError.setText(error);
        btnOperar.setEnabled(precio != null);
        btnOperar.setAlpha(precio != null ? 1f : 0.5f);
        btnOperar.setText((compra ? "Comprar " : "Vender ") + cripto.codigo());
    }

    private void renderConfirmar() {
        boolean compra = op.esCompra();
        Moneda cripto = op.getCripto();
        BigDecimal monto = op.getMontoAConfirmar();
        BigDecimal precio = op.getPrecio();
        BigDecimal recibis = op.getPreviewConfirmar();
        tvTituloConfirmar.setText(compra ? "Confirmá la compra de " + cripto.codigo() : "Confirmá la venta de " + cripto.codigo());
        tvLabelMontoConfirmar.setText(compra ? "Vas a pagar" : "Vas a vender");
        tvMontoConfirmar.setText(formato(op.getMonedaEntrada(), monto));
        tvRecibisConfirmar.setText(recibis != null
                ? (compra ? formato(cripto, recibis) : formato(Moneda.PESOS, recibis)) : MontoFormatter.SIN_DATO);
        tvLabelPrecioConfirmar.setText("Precio " + cripto.codigo());
        animador.mostrar(tvCotizacionConfirmar, cripto.codigo(), precio, AnimadorPrecios::pesos);
        BigDecimal saldo = saldoDisponible();
        tvSaldoLuegoConfirmar.setText(saldo != null && monto != null
                ? formato(op.getMonedaEntrada(), saldo.subtract(monto)) : MontoFormatter.SIN_DATO);

        String error = op.getError();
        tvErrorConfirmar.setVisibility(error != null ? View.VISIBLE : View.GONE);
        tvErrorConfirmar.setText(error);
        boolean enviando = op.isEnviando();
        btnConfirmar.setEnabled(!enviando && precio != null);
        btnConfirmar.setAlpha(btnConfirmar.isEnabled() ? 1f : 0.5f);
        btnConfirmar.setText(enviando ? "Procesando..." : compra ? "Confirmar compra" : "Confirmar venta");
        btnModificar.setEnabled(!enviando);
    }

    private void renderResultado() {
        if (op.getPaso() == OperacionCripto.Paso.EXITO) {
            OperacionCriptoResponse r = op.getResultado();
            boolean compra = r.getTipo() != null ? r.esCompra() : op.esCompra();
            Moneda cripto = r.getSimbolo() != null && Moneda.desde(r.getSimbolo()) != null
                    ? Moneda.desde(r.getSimbolo()) : op.getCripto();
            ivIconoResultado.setImageResource(R.drawable.ic_check);
            ivIconoResultado.setImageTintList(getColorStateList(R.color.color_positivo));
            tvTituloResultado.setText(compra ? "¡Compra realizada!" : "¡Venta realizada!");
            // Montos REALES del backend, no el preview
            String cant = formato(cripto, r.getMontoCripto());
            String pesos = "$ " + MontoFormatter.fiat(r.getMontoPesos());
            tvTextoResultado.setText(compra ? "Compraste " + cant + " pagando " + pesos
                    : "Vendiste " + cant + " y recibiste " + pesos);
            tvDetalleResultado.setVisibility(View.VISIBLE);
            tvDetalleResultado.setText("Precio $ " + MontoFormatter.fiat(r.getCotizacion()) + " por " + cripto.codigo());
        } else {
            ivIconoResultado.setImageResource(R.drawable.ic_clock);
            ivIconoResultado.setImageTintList(getColorStateList(R.color.color_servicio_luz));
            tvTituloResultado.setText("No sabemos si se realizó");
            tvTextoResultado.setText(op.getError());
            tvDetalleResultado.setVisibility(View.GONE);
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
