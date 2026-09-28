package com.example.payxmobile.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.payxmobile.R;
import com.example.payxmobile.actividad.ListaRemota;
import com.example.payxmobile.model.PlazoFijoResponse;
import com.example.payxmobile.model.TasasPlazoFijoResponse;
import com.example.payxmobile.network.RetrofitClient;
import com.example.payxmobile.plazofijo.PlazosFijosRepository;
import com.example.payxmobile.plazofijo.VistaPlazosFijos;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * "Tus plazos fijos" (PlazosFijos.jsx): activos y vencidos, más recientes primero. Refresca al
 * abrir, tras constituir (el repositorio es compartido) y cada 10 s en primer plano, así se ve
 * cuando el backend vence y acredita uno solo.
 */
public class MisPlazosFijosActivity extends AppCompatActivity {

    private ListaRemota<PlazoFijoResponse> repo;
    private final ListaRemota.Observador<PlazoFijoResponse> observador = e -> {
        estado = e;
        render();
    };
    private ListaRemota.Estado<PlazoFijoResponse> estado;
    /** De /tasas (lo define el admin); null mientras no llegó. */
    private Integer maxActivos;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mis_plazos_fijos);
        repo = PlazosFijosRepository.get(this);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnReintentar).setOnClickListener(v -> repo.refrescar());
        findViewById(R.id.btnConstituirNuevo).setOnClickListener(v -> {
            VistaPlazosFijos vista = VistaPlazosFijos.de(estado, maxActivos);
            if (vista.avisoLimite != null) {
                Toast.makeText(this, vista.avisoLimite, Toast.LENGTH_SHORT).show();
                return;
            }
            startActivity(new Intent(this, PlazoFijoActivity.class));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        repo.observar(observador);
        repo.iniciarAutoRefresco(); // YA y cada 10 s
        cargarMaximo();
    }

    @Override
    protected void onStop() {
        super.onStop();
        repo.detenerAutoRefresco();
        repo.dejarDeObservar(observador);
    }

    /** Cuántos activos se permiten: sin esto se muestra "n activos" y el botón queda habilitado. */
    private void cargarMaximo() {
        RetrofitClient.getService(this).obtenerTasasPlazoFijo().enqueue(new Callback<TasasPlazoFijoResponse>() {
            @Override
            public void onResponse(Call<TasasPlazoFijoResponse> call, Response<TasasPlazoFijoResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getMaxActivos() != null) {
                    maxActivos = response.body().getMaxActivos();
                    render();
                }
            }

            @Override
            public void onFailure(Call<TasasPlazoFijoResponse> call, Throwable t) {
                // Se sigue mostrando "n activos"; el backend igual rechaza de más
            }
        });
    }

    private void render() {
        if (isFinishing()) return;
        VistaPlazosFijos v = VistaPlazosFijos.de(estado, maxActivos);

        TextView tvSubtitulo = findViewById(R.id.tvSubtitulo);
        tvSubtitulo.setVisibility(v.subtitulo != null ? View.VISIBLE : View.GONE);
        tvSubtitulo.setText(v.subtitulo);
        TextView tvAviso = findViewById(R.id.tvAvisoLimite);
        tvAviso.setVisibility(v.avisoLimite != null ? View.VISIBLE : View.GONE);
        tvAviso.setText(v.avisoLimite);
        View btnNuevo = findViewById(R.id.btnConstituirNuevo);
        btnNuevo.setAlpha(v.avisoLimite != null ? 0.5f : 1f);
        findViewById(R.id.tvDesactualizado).setVisibility(v.desactualizado ? View.VISIBLE : View.GONE);

        boolean lista = v.modo == VistaPlazosFijos.Modo.LISTA;
        findViewById(R.id.cardEstado).setVisibility(lista ? View.GONE : View.VISIBLE);
        findViewById(R.id.progressEstado).setVisibility(v.modo == VistaPlazosFijos.Modo.CARGANDO ? View.VISIBLE : View.GONE);
        findViewById(R.id.ivEstado).setVisibility(v.modo == VistaPlazosFijos.Modo.VACIO ? View.VISIBLE : View.GONE);
        findViewById(R.id.btnReintentar).setVisibility(v.modo == VistaPlazosFijos.Modo.ERROR ? View.VISIBLE : View.GONE);
        ((TextView) findViewById(R.id.tvEstado)).setText(
                v.modo == VistaPlazosFijos.Modo.CARGANDO ? VistaPlazosFijos.MSG_CARGANDO
                        : v.modo == VistaPlazosFijos.Modo.ERROR ? v.error
                        : VistaPlazosFijos.MSG_VACIO);

        dibujarGrupo(R.id.tvTituloActivos, R.id.listaActivos, "Activos", v.activos);
        dibujarGrupo(R.id.tvTituloVencidos, R.id.listaVencidos, "Vencidos", v.vencidos);
    }

    /** Reusa las tarjetas existentes: el refresco cada 10 s no parpadea. */
    private void dibujarGrupo(int idTitulo, int idLista, String titulo, List<VistaPlazosFijos.Tarjeta> tarjetas) {
        TextView tvTitulo = findViewById(idTitulo);
        tvTitulo.setVisibility(tarjetas.isEmpty() ? View.GONE : View.VISIBLE);
        tvTitulo.setText(titulo + " (" + tarjetas.size() + ")");
        LinearLayout contenedor = findViewById(idLista);
        LayoutInflater inflater = LayoutInflater.from(this);
        while (contenedor.getChildCount() > tarjetas.size()) contenedor.removeViewAt(contenedor.getChildCount() - 1);
        while (contenedor.getChildCount() < tarjetas.size()) {
            contenedor.addView(inflater.inflate(R.layout.item_plazo_fijo, contenedor, false));
        }
        for (int i = 0; i < tarjetas.size(); i++) bind(contenedor.getChildAt(i), tarjetas.get(i));
    }

    private void bind(View card, VistaPlazosFijos.Tarjeta t) {
        ((TextView) card.findViewById(R.id.tvMonto)).setText(t.monto);
        TextView badge = card.findViewById(R.id.tvBadge);
        badge.setText(t.badge);
        badge.setBackgroundResource(t.activo ? R.drawable.bg_pill_orange_tint : R.drawable.bg_pill_pagada);
        badge.setTextColor(getColor(t.activo ? R.color.payx_orange : R.color.color_positivo));
        ((android.widget.ImageView) card.findViewById(R.id.ivIcono)).setImageTintList(
                getColorStateList(t.activo ? R.color.payx_orange : R.color.color_positivo));
        ((TextView) card.findViewById(R.id.tvConstituido)).setText(t.constituido);
        ((TextView) card.findViewById(R.id.tvPlazo)).setText(t.plazo);
        ((TextView) card.findViewById(R.id.tvInteres)).setText(t.interes);
        ((TextView) card.findViewById(R.id.tvEtiquetaTotal)).setText(t.etiquetaTotal);
        TextView total = card.findViewById(R.id.tvTotal);
        total.setText(t.total);
        total.setTextColor(getColor(t.activo ? R.color.text_primary : R.color.color_positivo));
        ((TextView) card.findViewById(R.id.tvEtiquetaFecha)).setText(t.etiquetaFecha);
        ((TextView) card.findViewById(R.id.tvVencimiento)).setText(t.vencimiento);
    }
}
