package com.example.payxmobile.activities;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.payxmobile.R;
import com.example.payxmobile.estadisticas.CargaEstadisticas;
import com.example.payxmobile.estadisticas.ComparacionPeriodo;
import com.example.payxmobile.estadisticas.EstadisticasViewModel;
import com.example.payxmobile.estadisticas.PeriodoEstadistica;
import com.example.payxmobile.estadisticas.VistaEstadisticas;
import com.example.payxmobile.estadisticas.ui.BarrasView;
import com.example.payxmobile.estadisticas.ui.DonutView;
import com.example.payxmobile.estadisticas.ui.EstiloCategoria;
import com.example.payxmobile.model.EstadisticaGastosResponse;
import com.example.payxmobile.network.RetrofitClient;
import com.example.payxmobile.utils.SesionUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * "Estadísticas de gastos" (Estadisticas.jsx). Toda la lógica vive en {@link CargaEstadisticas} y
 * {@link VistaEstadisticas}. Sin polling: se pide al entrar (y al volver), al cambiar de atajo y
 * deslizando para refrescar.
 */
public class EstadisticasActivity extends AppCompatActivity {

    private static final String K_PERIODO = "est_periodo";

    private CargaEstadisticas carga;
    /** Recreada por rotación con los datos en el ViewModel: no se vuelve a pedir. */
    private boolean saltearPrimerRefresco;
    private SwipeRefreshLayout swipeRefresh;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_estadisticas);

        EstadisticasViewModel vm = new ViewModelProvider(this).get(EstadisticasViewModel.class);
        if (vm.carga == null) {
            Context app = getApplicationContext();
            vm.carga = new CargaEstadisticas(() -> RetrofitClient.getService(app), LocalDate::now);
            if (savedInstanceState != null) {
                String p = savedInstanceState.getString(K_PERIODO);
                if (p != null) vm.carga.restaurar(PeriodoEstadistica.valueOf(p));
            }
        } else {
            saltearPrimerRefresco = true;
        }
        carga = vm.carga;
        carga.setObservador(c -> {
            if (c.isSesionInvalida()) {
                SesionUtils.sesionInvalida(this); // sin bucle: se ignora si ya no hay token
                return;
            }
            render();
        });

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnReintentar).setOnClickListener(v -> carga.refrescar());
        swipeRefresh = findViewById(R.id.swipeRefresh);
        swipeRefresh.setColorSchemeResources(R.color.payx_orange);
        swipeRefresh.setOnRefreshListener(() -> carga.refrescar());
        armarPeriodos();
        render();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putString(K_PERIODO, carga.getPeriodo().name());
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Al entrar y al volver a la pantalla (p. ej. después de operar en otra), no en segundo plano
        if (saltearPrimerRefresco) saltearPrimerRefresco = false;
        else carga.refrescar();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        carga.setObservador(null);
    }

    // ── Atajos ────────────────────────────────────────────────────────────────

    private void armarPeriodos() {
        LinearLayout fila = findViewById(R.id.filaPeriodos);
        for (PeriodoEstadistica p : PeriodoEstadistica.values()) {
            TextView chip = new TextView(this);
            chip.setText(p.etiqueta);
            chip.setTag(p);
            chip.setTextSize(12.5f);
            chip.setTypeface(null, Typeface.BOLD);
            chip.setBackgroundResource(R.drawable.bg_chip_naranja);
            chip.setTextColor(getColorStateList(R.color.coin_chip_text));
            chip.setPadding(dp(16), dp(9), dp(16), dp(9));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(8));
            chip.setLayoutParams(lp);
            chip.setOnClickListener(v -> carga.seleccionar(p));
            fila.addView(chip);
        }
    }

    // ── Dibujo ────────────────────────────────────────────────────────────────

    private void render() {
        if (isFinishing()) return;
        LinearLayout fila = findViewById(R.id.filaPeriodos);
        for (int i = 0; i < fila.getChildCount(); i++) {
            View chip = fila.getChildAt(i);
            chip.setSelected(chip.getTag() == carga.getPeriodo());
        }

        CargaEstadisticas.Estado estado = carga.getEstado();
        EstadisticaGastosResponse datos = carga.getDatos(); // solo si son del atajo elegido
        if (estado != CargaEstadisticas.Estado.CARGANDO) swipeRefresh.setRefreshing(false);

        boolean hayDatos = datos != null;
        findViewById(R.id.contenido).setVisibility(hayDatos ? View.VISIBLE : View.GONE);
        // Refrescando el mismo período: los datos quedan, atenuados
        findViewById(R.id.contenido).setAlpha(hayDatos && estado == CargaEstadisticas.Estado.CARGANDO ? 0.5f : 1f);
        boolean mostrarEstado = !hayDatos || estado == CargaEstadisticas.Estado.ERROR;
        findViewById(R.id.layoutEstado).setVisibility(mostrarEstado ? View.VISIBLE : View.GONE);
        findViewById(R.id.progress).setVisibility(estado == CargaEstadisticas.Estado.CARGANDO ? View.VISIBLE : View.GONE);
        findViewById(R.id.btnReintentar).setVisibility(estado == CargaEstadisticas.Estado.ERROR ? View.VISIBLE : View.GONE);
        ((TextView) findViewById(R.id.tvEstado)).setText(
                estado == CargaEstadisticas.Estado.ERROR ? carga.getError() : "Cargando tus estadísticas...");
        if (hayDatos) renderDatos(VistaEstadisticas.de(datos));
    }

    private void renderDatos(VistaEstadisticas v) {
        ((TextView) findViewById(R.id.tvRango)).setText(v.rango);
        ((TextView) findViewById(R.id.tvTotal)).setText(v.total);
        ((TextView) findViewById(R.id.tvOperaciones)).setText(v.operaciones);

        TextView tvComparacion = findViewById(R.id.tvComparacion);
        ComparacionPeriodo comp = v.comparacion;
        tvComparacion.setVisibility(comp != null ? View.VISIBLE : View.GONE);
        if (comp != null) {
            tvComparacion.setText(comp.texto);
            // Gastar más es "malo" (rojo); gastar menos, "bueno" (verde)
            tvComparacion.setTextColor(getColor(comp.tipo == ComparacionPeriodo.Tipo.SUBE ? R.color.color_negativo
                    : comp.tipo == ComparacionPeriodo.Tipo.BAJA ? R.color.color_positivo : R.color.text_secondary));
        }

        findViewById(R.id.kpiPromedio).setVisibility(v.promedioDiario != null ? View.VISIBLE : View.GONE);
        ((TextView) findViewById(R.id.tvPromedio)).setText(v.promedioDiario);
        findViewById(R.id.kpiPico).setVisibility(v.diaPico != null ? View.VISIBLE : View.GONE);
        ((TextView) findViewById(R.id.tvPico)).setText(v.diaPico);
        findViewById(R.id.filaKpis).setVisibility(v.promedioDiario != null || v.diaPico != null ? View.VISIBLE : View.GONE);

        // Donut: proporción monto/total; sin gastos solo la pista y el mensaje
        List<DonutView.Porcion> porciones = new ArrayList<>();
        if (!v.sinGastos) {
            for (VistaEstadisticas.Categoria c : v.categorias) {
                if (c.sinGasto) continue;
                porciones.add(new DonutView.Porcion(EstiloCategoria.color(c.codigo),
                        c.montoValor.divide(v.totalValor, 6, java.math.RoundingMode.HALF_UP).floatValue()));
            }
        }
        ((DonutView) findViewById(R.id.donut)).setPorciones(porciones);
        ((TextView) findViewById(R.id.tvCentroValor)).setText(v.total);
        findViewById(R.id.tvSinGastos).setVisibility(v.sinGastos ? View.VISIBLE : View.GONE);

        LinearLayout lista = findViewById(R.id.listaCategorias);
        lista.setVisibility(v.sinGastos ? View.GONE : View.VISIBLE);
        LayoutInflater inflater = LayoutInflater.from(this);
        while (lista.getChildCount() > v.categorias.size()) lista.removeViewAt(lista.getChildCount() - 1);
        while (lista.getChildCount() < v.categorias.size()) lista.addView(inflater.inflate(R.layout.item_categoria_gasto, lista, false));
        for (int i = 0; i < v.categorias.size(); i++) bindCategoria(lista.getChildAt(i), v.categorias.get(i));

        // Serie: oculta si viene vacía (> 365 días, o sin gastos): no es un error
        boolean haySerie = !v.serie.isEmpty();
        findViewById(R.id.seccionSerie).setVisibility(haySerie ? View.VISIBLE : View.GONE);
        if (haySerie) {
            List<BigDecimal> montos = new ArrayList<>();
            for (VistaEstadisticas.Punto p : v.serie) montos.add(p.monto);
            BarrasView barras = findViewById(R.id.barras);
            barras.setColores(getColor(R.color.payx_orange), getColor(R.color.color_servicio_luz));
            barras.setMontos(montos);
            ((TextView) findViewById(R.id.tvSerieDesde)).setText(VistaEstadisticas.diaCorto(v.serie.get(0).fecha));
            ((TextView) findViewById(R.id.tvSerieHasta)).setText(VistaEstadisticas.diaCorto(v.serie.get(v.serie.size() - 1).fecha));
        }
    }

    private void bindCategoria(View fila, VistaEstadisticas.Categoria c) {
        int color = EstiloCategoria.color(c.codigo);
        fila.findViewById(R.id.punto).setBackgroundTintList(ColorStateList.valueOf(color));
        ((TextView) fila.findViewById(R.id.tvEtiqueta)).setText(c.etiqueta);
        ((TextView) fila.findViewById(R.id.tvMonto)).setText(c.monto);
        ((TextView) fila.findViewById(R.id.tvPorcentaje)).setText(c.porcentaje);
        ProgressBar barra = fila.findViewById(R.id.barra);
        // El porcentaje del backend (0-100, 1 decimal) sin recalcular
        barra.setProgress(Math.min(1000, c.porcentajeValor.movePointRight(1).intValue()));
        barra.setProgressTintList(ColorStateList.valueOf(color));
        fila.setAlpha(c.sinGasto ? 0.45f : 1f);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
