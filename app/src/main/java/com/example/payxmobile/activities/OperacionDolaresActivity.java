package com.example.payxmobile.activities;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.payxmobile.R;

import java.text.NumberFormat;
import java.util.Locale;

public class OperacionDolaresActivity extends AppCompatActivity {

    public static final String EXTRA_TIPO = "tipo";

    private static final double COTIZACION_COMPRA = 1485.00;
    private static final double COTIZACION_VENTA = 1535.00;

    private final NumberFormat formatoMonto = NumberFormat.getNumberInstance(new Locale("es", "AR"));
    private final NumberFormat formatoUsd = NumberFormat.getNumberInstance(new Locale("es", "AR"));

    private boolean esCompra = true;
    private double saldoOrigen;

    private TextView tvMontoResumen, tvRecibis, tvSaldoLuego;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_operacion_dolares);

        formatoMonto.setMinimumFractionDigits(2);
        formatoMonto.setMaximumFractionDigits(2);
        formatoUsd.setMinimumFractionDigits(2);
        formatoUsd.setMaximumFractionDigits(2);

        esCompra = !"VENDER".equals(getIntent().getStringExtra(EXTRA_TIPO));
        saldoOrigen = esCompra ? 97567742.00 : 0.0;

        tvMontoResumen = findViewById(R.id.tvMontoResumen);
        tvRecibis = findViewById(R.id.tvRecibis);
        tvSaldoLuego = findViewById(R.id.tvSaldoLuego);

        configurarTextosSegunTipo();

        EditText etMonto = findViewById(R.id.etMonto);
        etMonto.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                actualizarResumen(s.toString());
            }
        });

        findViewById(R.id.btnUsarTodo).setOnClickListener(v ->
                etMonto.setText(esCompra ? formatoMonto.format(saldoOrigen) : formatoUsd.format(saldoOrigen)));

        findViewById(R.id.btnVolver).setOnClickListener(v -> finish());
        findViewById(R.id.btnCerrar).setOnClickListener(v -> finish());
        findViewById(R.id.btnCancelar).setOnClickListener(v -> finish());
        findViewById(R.id.btnOperar).setOnClickListener(v ->
                Toast.makeText(this, "Esta función va a estar disponible próximamente.", Toast.LENGTH_SHORT).show());

        actualizarResumen("0");
    }

    private void configurarTextosSegunTipo() {
        String cotizacionTexto = "$ " + formatoMonto.format(esCompra ? COTIZACION_VENTA : COTIZACION_COMPRA);
        String saldoOrigenTexto = (esCompra ? "$ " : "US$ ") + formatoMonto.format(saldoOrigen);

        ((TextView) findViewById(R.id.tvTitulo)).setText(esCompra ? "Comprar dólares" : "Vender dólares");
        ((TextView) findViewById(R.id.tvSubtitulo)).setText(esCompra
                ? "Comprá dólares al instante con el saldo de tu cuenta en pesos"
                : "Vendé tus dólares y recibí pesos al instante");

        ((TextView) findViewById(R.id.tvLabelVasA)).setText(esCompra ? "Vas a pagar" : "Vas a vender");
        ((TextView) findViewById(R.id.tvSimboloResumen)).setText(esCompra ? "$" : "US$");
        ((TextView) findViewById(R.id.tvCotizacion)).setText(cotizacionTexto);

        ((TextView) findViewById(R.id.tvLabelSaldoOrigen)).setText(
                esCompra ? "Saldo disponible en pesos" : "Saldo disponible en dólares");
        ((TextView) findViewById(R.id.tvSaldoOrigen)).setText(saldoOrigenTexto);

        ((TextView) findViewById(R.id.tvLabelSaldoDisponible)).setText(
                esCompra ? "Saldo disponible en pesos" : "Saldo disponible en dólares");
        ((TextView) findViewById(R.id.tvSaldoDisponible)).setText(saldoOrigenTexto);

        ((TextView) findViewById(R.id.tvLabelMonto)).setText(
                esCompra ? "Monto en pesos a destinar" : "Monto en dólares a vender");
        ((TextView) findViewById(R.id.tvSimboloInput)).setText(esCompra ? "$" : "US$");

        ((TextView) findViewById(R.id.tvNota)).setText(esCompra
                ? "Cotización de venta: $ " + formatoMonto.format(COTIZACION_VENTA) + " por dólar (oficial)"
                : "Cotización de compra: $ " + formatoMonto.format(COTIZACION_COMPRA) + " por dólar (oficial)");

        Button btnOperar = findViewById(R.id.btnOperar);
        btnOperar.setText(esCompra ? "Comprar dólares" : "Vender dólares");
        btnOperar.setCompoundDrawablesWithIntrinsicBounds(
                esCompra ? R.drawable.ic_arrow_down_circle : R.drawable.ic_arrow_up_circle, 0, 0, 0);
    }

    private void actualizarResumen(String textoMonto) {
        double monto = 0;
        try {
            monto = Double.parseDouble(textoMonto.replace(",", "."));
        } catch (NumberFormatException ignored) {}

        tvMontoResumen.setText(formatoMonto.format(monto));

        if (esCompra) {
            double recibis = monto / COTIZACION_VENTA;
            tvRecibis.setText("Recibís US$ " + formatoUsd.format(recibis));
            double restante = Math.max(saldoOrigen - monto, 0);
            tvSaldoLuego.setText("$ " + formatoMonto.format(restante));
        } else {
            double recibis = monto * COTIZACION_COMPRA;
            tvRecibis.setText("Recibís $ " + formatoMonto.format(recibis));
            double restante = Math.max(saldoOrigen - monto, 0);
            tvSaldoLuego.setText("US$ " + formatoUsd.format(restante));
        }
    }
}
