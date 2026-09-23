package com.example.payxmobile.activities;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.payxmobile.R;

import java.text.NumberFormat;
import java.util.Locale;

public class OperacionCriptoActivity extends AppCompatActivity {

    public static final String EXTRA_TIPO = "tipo";

    private static final String[] CODIGOS = {"BTC", "ETH", "SOL", "USDT", "BNB", "XRP"};
    private static final String[] NOMBRES = {
            "Bitcoin (BTC)", "Ethereum (ETH)", "Solana (SOL)", "Tether (USDT)", "BNB (BNB)", "XRP (XRP)"
    };
    private static final double[] PRECIOS = {128127714.0, 4057107.0, 174485.0, 1515.86, 1164353.0, 2322.37};

    private static final double SALDO_PESOS = 97567742.00;

    private final NumberFormat formatoMonto = NumberFormat.getNumberInstance(new Locale("es", "AR"));
    private final NumberFormat formatoCripto = NumberFormat.getNumberInstance(new Locale("es", "AR"));

    private boolean esCompra = true;
    private int indiceMoneda = 0;

    private TextView tvMontoResumen, tvRecibis, tvSaldoLuego, tvPrecioSeleccionado, tvMonedaSeleccionada;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_operacion_cripto);

        formatoMonto.setMinimumFractionDigits(2);
        formatoMonto.setMaximumFractionDigits(2);
        formatoCripto.setMinimumFractionDigits(8);
        formatoCripto.setMaximumFractionDigits(8);

        esCompra = !"VENDER".equals(getIntent().getStringExtra(EXTRA_TIPO));

        tvMontoResumen = findViewById(R.id.tvMontoResumen);
        tvRecibis = findViewById(R.id.tvRecibis);
        tvSaldoLuego = findViewById(R.id.tvSaldoLuego);
        tvPrecioSeleccionado = findViewById(R.id.tvPrecioSeleccionado);
        tvMonedaSeleccionada = findViewById(R.id.tvMonedaSeleccionada);

        pintarPreciosVivos();
        configurarTextosFijos();
        actualizarMoneda();

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
                etMonto.setText(esCompra ? formatoMonto.format(SALDO_PESOS) : formatoCripto.format(0)));

        findViewById(R.id.filaMoneda).setOnClickListener(this::mostrarMonedas);

        findViewById(R.id.btnVolver).setOnClickListener(v -> finish());
        findViewById(R.id.btnCerrar).setOnClickListener(v -> finish());
        findViewById(R.id.btnCancelar).setOnClickListener(v -> finish());
        findViewById(R.id.btnOperar).setOnClickListener(v ->
                Toast.makeText(this, "Esta función va a estar disponible próximamente.", Toast.LENGTH_SHORT).show());
    }

    private void pintarPreciosVivos() {
        ((TextView) findViewById(R.id.tvPrecioBTC)).setText("$ " + formatoMonto.format(PRECIOS[0]));
        ((TextView) findViewById(R.id.tvPrecioETH)).setText("$ " + formatoMonto.format(PRECIOS[1]));
        ((TextView) findViewById(R.id.tvPrecioSOL)).setText("$ " + formatoMonto.format(PRECIOS[2]));
        ((TextView) findViewById(R.id.tvPrecioUSDT)).setText("$ " + formatoMonto.format(PRECIOS[3]));
        ((TextView) findViewById(R.id.tvPrecioBNB)).setText("$ " + formatoMonto.format(PRECIOS[4]));
        ((TextView) findViewById(R.id.tvPrecioXRP)).setText("$ " + formatoMonto.format(PRECIOS[5]));
    }

    private void configurarTextosFijos() {
        ((TextView) findViewById(R.id.tvTitulo)).setText(esCompra ? "Comprar criptomonedas" : "Vender criptomonedas");
        ((TextView) findViewById(R.id.tvSubtitulo)).setText(esCompra
                ? "Elegí qué criptomoneda comprar y cuánto querés invertir"
                : "Elegí qué criptomoneda vender y cuánto querés recibir");
        ((TextView) findViewById(R.id.tvLabelVasA)).setText(esCompra ? "Vas a pagar" : "Vas a vender");
        ((TextView) findViewById(R.id.tvLabelMonto)).setText(esCompra
                ? "Monto en pesos a destinar" : "Monto en cripto a vender");
    }

    private void mostrarMonedas(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        for (int i = 0; i < NOMBRES.length; i++) {
            popup.getMenu().add(0, i, i, NOMBRES[i] + " · $ " + formatoMonto.format(PRECIOS[i]));
        }
        popup.setOnMenuItemClickListener(item -> {
            indiceMoneda = item.getItemId();
            actualizarMoneda();
            return true;
        });
        popup.show();
    }

    private void actualizarMoneda() {
        String codigo = CODIGOS[indiceMoneda];
        double precio = PRECIOS[indiceMoneda];
        String precioTexto = "$ " + formatoMonto.format(precio);

        tvMonedaSeleccionada.setText(NOMBRES[indiceMoneda] + " · " + precioTexto);
        tvPrecioSeleccionado.setText(precioTexto);
        ((TextView) findViewById(R.id.tvLabelPrecio)).setText("Precio " + codigo);
        ((TextView) findViewById(R.id.tvSimboloResumen)).setText(esCompra ? "$" : codigo);
        ((TextView) findViewById(R.id.tvSimboloInput)).setText(esCompra ? "$" : codigo);

        ((TextView) findViewById(R.id.tvLabelSaldoOrigen)).setText(esCompra ? "Saldo en pesos" : "Saldo en " + codigo);
        ((TextView) findViewById(R.id.tvSaldoOrigen)).setText(esCompra
                ? "$ " + formatoMonto.format(SALDO_PESOS)
                : formatoCripto.format(0) + " " + codigo);

        ((TextView) findViewById(R.id.tvLabelSaldoDisponible)).setText(
                esCompra ? "Saldo disponible en pesos" : "Saldo disponible en " + codigo);
        ((TextView) findViewById(R.id.tvSaldoDisponible)).setText(esCompra
                ? "$ " + formatoMonto.format(SALDO_PESOS)
                : formatoCripto.format(0) + " " + codigo);

        ((TextView) findViewById(R.id.tvNota)).setText(
                "Precio actual de " + NOMBRES[indiceMoneda].split(" ")[0] + ": " + precioTexto);

        Button btnOperar = findViewById(R.id.btnOperar);
        btnOperar.setText((esCompra ? "Comprar " : "Vender ") + codigo);

        actualizarResumen(((EditText) findViewById(R.id.etMonto)).getText().toString());
    }

    private void actualizarResumen(String textoMonto) {
        double monto = 0;
        try {
            monto = Double.parseDouble(textoMonto.replace(",", "."));
        } catch (NumberFormatException ignored) {}

        double precio = PRECIOS[indiceMoneda];
        String codigo = CODIGOS[indiceMoneda];

        if (esCompra) {
            tvMontoResumen.setText(formatoMonto.format(monto));
            double recibis = precio > 0 ? monto / precio : 0;
            tvRecibis.setText("Recibís " + codigo + " " + formatoCripto.format(recibis));
            double restante = Math.max(SALDO_PESOS - monto, 0);
            tvSaldoLuego.setText("$ " + formatoMonto.format(restante));
        } else {
            tvMontoResumen.setText(formatoCripto.format(monto));
            double recibis = monto * precio;
            tvRecibis.setText("Recibís $ " + formatoMonto.format(recibis));
            double restante = Math.max(0 - monto, 0);
            tvSaldoLuego.setText(formatoCripto.format(restante) + " " + codigo);
        }
    }
}
