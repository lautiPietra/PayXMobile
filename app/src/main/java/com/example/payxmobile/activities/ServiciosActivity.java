package com.example.payxmobile.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.payxmobile.R;
import com.example.payxmobile.actividad.ListaRemota;
import com.example.payxmobile.model.FacturaResponse;
import com.example.payxmobile.model.ServicioConFacturaResponse;
import com.example.payxmobile.servicios.FacturaAPagar;
import com.example.payxmobile.servicios.ServiciosRepository;
import com.example.payxmobile.servicios.VistaServicios;
import com.example.payxmobile.servicios.ui.EstiloServicio;
import com.google.gson.Gson;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * "Pago de servicios" (Servicios.jsx): pestaña "Facturas del mes" (las 6 tarjetas de GET /api/facturas
 * y las facturas anteriores sin pagar) y pestaña "Historial" (TODAS las facturas). Se refresca al
 * abrir, al volver de pagar y deslizando; sin polling.
 */
public class ServiciosActivity extends AppCompatActivity {

    private static final String K_TAB = "sv_tab";

    private ListaRemota<ServicioConFacturaResponse> catalogo;
    private ListaRemota<FacturaResponse> historial;
    private ListaRemota.Estado<ServicioConFacturaResponse> estadoCatalogo;
    private ListaRemota.Estado<FacturaResponse> estadoHistorial;
    private final ListaRemota.Observador<ServicioConFacturaResponse> observadorCatalogo = e -> {
        estadoCatalogo = e;
        render();
    };
    private final ListaRemota.Observador<FacturaResponse> observadorHistorial = e -> {
        estadoHistorial = e;
        render();
    };
    private boolean tabFacturas = true;
    private SwipeRefreshLayout swipeRefresh;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_servicios);
        catalogo = ServiciosRepository.catalogo(this);
        historial = ServiciosRepository.historial(this);
        if (savedInstanceState != null) tabFacturas = savedInstanceState.getBoolean(K_TAB, true);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.tabFacturas).setOnClickListener(v -> mostrarTab(true));
        findViewById(R.id.tabRealizados).setOnClickListener(v -> mostrarTab(false));
        findViewById(R.id.btnReintentarFacturas).setOnClickListener(v -> catalogo.refrescar());
        findViewById(R.id.btnReintentarHistorial).setOnClickListener(v -> historial.refrescar());
        swipeRefresh = findViewById(R.id.swipeRefresh);
        swipeRefresh.setColorSchemeResources(R.color.payx_orange);
        swipeRefresh.setOnRefreshListener(this::refrescar);
        mostrarTab(tabFacturas);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putBoolean(K_TAB, tabFacturas);
    }

    @Override
    protected void onStart() {
        super.onStart();
        catalogo.observar(observadorCatalogo);
        historial.observar(observadorHistorial);
        refrescar(); // al abrir y al volver de pagar
    }

    @Override
    protected void onStop() {
        super.onStop();
        catalogo.dejarDeObservar(observadorCatalogo);
        historial.dejarDeObservar(observadorHistorial);
    }

    private void refrescar() {
        catalogo.refrescar();
        historial.refrescar();
    }

    private void mostrarTab(boolean facturas) {
        tabFacturas = facturas;
        findViewById(R.id.contenedorFacturas).setVisibility(facturas ? View.VISIBLE : View.GONE);
        findViewById(R.id.contenedorRealizados).setVisibility(facturas ? View.GONE : View.VISIBLE);
        ((TextView) findViewById(R.id.tabFacturas)).setTextColor(getColor(facturas ? R.color.payx_orange : R.color.text_secondary));
        ((TextView) findViewById(R.id.tabRealizados)).setTextColor(getColor(facturas ? R.color.text_secondary : R.color.payx_orange));
        findViewById(R.id.indicadorFacturas).setBackgroundColor(getColor(facturas ? R.color.payx_orange : R.color.card_border));
        findViewById(R.id.indicadorRealizados).setBackgroundColor(getColor(facturas ? R.color.card_border : R.color.payx_orange));
    }

    private void pagar(FacturaAPagar f) {
        startActivity(new Intent(this, PagoServicioActivity.class)
                .putExtra(PagoServicioActivity.EXTRA_FACTURA, new Gson().toJson(f)));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    // ── Dibujo ────────────────────────────────────────────────────────────────

    private void render() {
        if (isFinishing()) return;
        ZoneId zona = ZoneId.systemDefault();
        VistaServicios v = VistaServicios.de(estadoCatalogo, estadoHistorial, LocalDate.now(zona), zona);
        boolean cargando = (estadoCatalogo != null && estadoCatalogo.cargando) || (estadoHistorial != null && estadoHistorial.cargando);
        if (!cargando) swipeRefresh.setRefreshing(false);

        // Facturas del mes
        estado(R.id.layoutEstadoFacturas, R.id.progressFacturas, R.id.tvEstadoFacturas, R.id.btnReintentarFacturas,
                v.modo, v.error, VistaServicios.MSG_CARGANDO, "");
        LinearLayout tarjetas = findViewById(R.id.listaTarjetas);
        ajustar(tarjetas, v.tarjetas.size(), R.layout.item_servicio);
        for (int i = 0; i < v.tarjetas.size(); i++) bindTarjeta(tarjetas.getChildAt(i), v.tarjetas.get(i));
        findViewById(R.id.tvTituloAnteriores).setVisibility(v.anteriores.isEmpty() ? View.GONE : View.VISIBLE);
        LinearLayout anteriores = findViewById(R.id.listaAnteriores);
        ajustar(anteriores, v.anteriores.size(), R.layout.item_factura);
        for (int i = 0; i < v.anteriores.size(); i++) bindFila(anteriores.getChildAt(i), v.anteriores.get(i), true);

        // Historial
        estado(R.id.layoutEstadoHistorial, R.id.progressHistorial, R.id.tvEstadoHistorial, R.id.btnReintentarHistorial,
                v.modoHistorial, v.errorHistorial, VistaServicios.MSG_CARGANDO_HISTORIAL, VistaServicios.MSG_HISTORIAL_VACIO);
        LinearLayout lista = findViewById(R.id.listaHistorial);
        ajustar(lista, v.historial.size(), R.layout.item_factura);
        for (int i = 0; i < v.historial.size(); i++) bindFila(lista.getChildAt(i), v.historial.get(i), false);
    }

    private void estado(int idLayout, int idProgress, int idTexto, int idReintentar, VistaServicios.Modo modo,
                        String error, String msgCargando, String msgVacio) {
        boolean lista = modo == VistaServicios.Modo.LISTA;
        findViewById(idLayout).setVisibility(lista ? View.GONE : View.VISIBLE);
        findViewById(idProgress).setVisibility(modo == VistaServicios.Modo.CARGANDO ? View.VISIBLE : View.GONE);
        findViewById(idReintentar).setVisibility(modo == VistaServicios.Modo.ERROR ? View.VISIBLE : View.GONE);
        ((TextView) findViewById(idTexto)).setText(modo == VistaServicios.Modo.CARGANDO ? msgCargando
                : modo == VistaServicios.Modo.ERROR ? error : msgVacio);
    }

    /** Reusa las filas existentes (sin parpadeo al refrescar). */
    private void ajustar(LinearLayout contenedor, int cantidad, int layout) {
        while (contenedor.getChildCount() > cantidad) contenedor.removeViewAt(contenedor.getChildCount() - 1);
        LayoutInflater inflater = LayoutInflater.from(this);
        while (contenedor.getChildCount() < cantidad) contenedor.addView(inflater.inflate(layout, contenedor, false));
    }

    private void bindTarjeta(View item, VistaServicios.Tarjeta t) {
        ImageView icono = item.findViewById(R.id.ivIconoServicio);
        icono.setImageResource(EstiloServicio.icono(t.codigo));
        icono.setImageTintList(getColorStateList(R.color.white));
        icono.setBackgroundTintList(getColorStateList(EstiloServicio.color(t.codigo)));
        ((TextView) item.findViewById(R.id.tvNombreServicio)).setText(t.nombre);
        ((TextView) item.findViewById(R.id.tvProveedorServicio)).setText(t.proveedor);
        ((TextView) item.findViewById(R.id.tvMontoServicio)).setText(t.monto);
        TextView fecha = item.findViewById(R.id.tvFechaServicio);
        fecha.setText(t.fecha);
        // Vencida: color de alerta, pero se puede pagar igual
        fecha.setTextColor(getColor(t.estado == VistaServicios.EstadoFactura.VENCIDA ? R.color.color_negativo
                : t.estado == VistaServicios.EstadoFactura.PAGADA ? R.color.color_positivo : R.color.text_secondary));
        TextView boton = item.findViewById(R.id.btnEstadoServicio);
        boolean pagada = t.estado == VistaServicios.EstadoFactura.PAGADA;
        boton.setText(pagada ? "Pagada ✓" : "Pagar");
        boton.setBackgroundResource(pagada ? R.drawable.bg_pill_pagada : R.drawable.bg_pill_pagar);
        boton.setTextColor(getColor(pagada ? R.color.color_positivo : R.color.white));
        boton.setEnabled(t.puedePagar);
        boton.setOnClickListener(t.puedePagar ? x -> pagar(FacturaAPagar.de(t.servicio)) : null);
    }

    private void bindFila(View item, VistaServicios.Fila f, boolean pagable) {
        ImageView icono = item.findViewById(R.id.ivIcono);
        icono.setImageResource(EstiloServicio.icono(f.codigo));
        icono.setBackgroundTintList(getColorStateList(EstiloServicio.color(f.codigo)));
        ((TextView) item.findViewById(R.id.tvNombre)).setText(f.nombre);
        ((TextView) item.findViewById(R.id.tvDetalle)).setText(f.detalle);
        ((TextView) item.findViewById(R.id.tvMonto)).setText(f.monto);
        TextView badge = item.findViewById(R.id.tvBadge);
        boolean pagada = f.estado == VistaServicios.EstadoFactura.PAGADA;
        boolean vencida = f.estado == VistaServicios.EstadoFactura.VENCIDA;
        // En "anteriores sin pagar" la fila entera paga; en el historial solo informa
        badge.setText(pagable ? "Pagar" : f.badge);
        badge.setBackgroundResource(pagable ? R.drawable.bg_pill_pagar : pagada ? R.drawable.bg_pill_pagada : R.drawable.bg_badge_pendiente);
        badge.setTextColor(getColor(pagable ? R.color.white : pagada ? R.color.color_positivo
                : vencida ? R.color.color_negativo : R.color.text_secondary));
        if (pagable && f.puedePagar) item.setOnClickListener(x -> pagar(FacturaAPagar.deAnterior(f.factura)));
        else {
            item.setOnClickListener(null);
            item.setClickable(false);
        }
    }
}
