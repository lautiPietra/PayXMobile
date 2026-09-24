package com.example.payxmobile.transferencias.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.example.payxmobile.R;
import com.example.payxmobile.model.TransferenciaResponse;
import com.example.payxmobile.network.RetrofitClient;
import com.example.payxmobile.transferencias.DetalleTransferencia;
import com.example.payxmobile.transferencias.FilaTransferencia;
import com.example.payxmobile.transferencias.FormatoTransferencia;
import com.example.payxmobile.transferencias.Refrescos;
import com.example.payxmobile.transferencias.TransferenciasRepository;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.time.ZoneId;

/**
 * Detalle de una transferencia en un bottom sheet (como TransferenciaDetalleModal de la web).
 * Lo abren Inicio y "Mis movimientos". Mientras está abierto se actualiza con cada refresco del
 * repositorio (el otro lado o el vencimiento pudieron cambiarla).
 */
public class DetalleTransferenciaSheet {

    private final Context context;
    private final TransferenciasRepository repo;
    private final TransferenciasRepository.Observador observadorRepo = this::onRepo;
    private BottomSheetDialog hoja;
    private DetalleTransferencia detalle;
    private View vista;

    public DetalleTransferenciaSheet(Context context) {
        this.context = context;
        this.repo = TransferenciasRepository.get(context);
    }

    public boolean estaAbierto() {
        return hoja != null && hoja.isShowing();
    }

    /** Id de la transferencia abierta (para restaurarla tras una rotación), o null. */
    public String idAbierto() {
        return estaAbierto() ? detalle.getTransferencia().getId() : null;
    }

    public void cerrar() {
        if (hoja != null) hoja.dismiss();
    }

    public void abrir(TransferenciaResponse t) {
        cerrar();
        Context app = context.getApplicationContext();
        detalle = new DetalleTransferencia(t, () -> RetrofitClient.getService(app),
                repo::reemplazar, () -> Refrescos.trasMoverPlata(app));

        vista = LayoutInflater.from(context).inflate(R.layout.sheet_detalle_transferencia, null);
        hoja = new BottomSheetDialog(context);
        hoja.setContentView(vista);
        // El dismiss de una hoja anterior llega después: tiene que soltar SU detalle, no el nuevo
        final DetalleTransferencia deEstaHoja = detalle;
        hoja.setOnDismissListener(d -> {
            deEstaHoja.setObservador(null);
            repo.dejarDeObservar(observadorRepo);
        });

        TextView btnCambiar = vista.findViewById(R.id.btnCambiarConcepto);
        View layoutEditar = vista.findViewById(R.id.layoutEditarConcepto);
        EditText etConcepto = vista.findViewById(R.id.etConcepto);
        btnCambiar.setOnClickListener(v -> {
            etConcepto.setText(detalle.getTransferencia().getConcepto());
            layoutEditar.setVisibility(View.VISIBLE);
            btnCambiar.setVisibility(View.GONE);
            vista.findViewById(R.id.tvConcepto).setVisibility(View.GONE);
            etConcepto.requestFocus();
        });
        vista.findViewById(R.id.btnCancelarConcepto).setOnClickListener(v -> cerrarEdicionConcepto());
        vista.findViewById(R.id.btnGuardarConcepto).setOnClickListener(v ->
                detalle.guardarConcepto(etConcepto.getText().toString()));
        vista.findViewById(R.id.btnConfirmarPendiente).setOnClickListener(v -> detalle.confirmar());
        vista.findViewById(R.id.btnCancelarPendiente).setOnClickListener(v -> detalle.cancelar());

        final boolean[] estabaGuardando = {false};
        detalle.setObservador(d -> {
            // Terminó de guardar sin error: se cierra la edición
            if (estabaGuardando[0] && !d.isGuardando() && d.getError() == null) cerrarEdicionConcepto();
            estabaGuardando[0] = d.isGuardando();
            render(d);
        });
        render(detalle);
        hoja.show();
        repo.observar(observadorRepo);
        detalle.refrescar(); // GET /{id}: la versión más nueva
    }

    private void onRepo(TransferenciasRepository.Estado e) {
        if (detalle != null && estaAbierto()) {
            detalle.actualizarDesdeListado(e.buscar(detalle.getTransferencia().getId()));
        }
    }

    private void cerrarEdicionConcepto() {
        vista.findViewById(R.id.layoutEditarConcepto).setVisibility(View.GONE);
        vista.findViewById(R.id.tvConcepto).setVisibility(View.VISIBLE);
        vista.findViewById(R.id.btnCambiarConcepto).setVisibility(
                detalle != null && detalle.puedeCambiarConcepto() ? View.VISIBLE : View.GONE);
    }

    private void render(DetalleTransferencia d) {
        View v = vista;
        TransferenciaResponse t = d.getTransferencia();
        ZoneId zona = ZoneId.systemDefault();
        FilaTransferencia fila = FilaTransferencia.de(t, zona);

        TextView tvMonto = v.findViewById(R.id.tvMontoDetalle);
        tvMonto.setText(fila.monto);
        tvMonto.setTextColor(context.getColor(FilaTransferenciaVista.colorMonto(fila.estilo)));
        TextView tvTitulo = v.findViewById(R.id.tvTituloDetalle);
        tvTitulo.setText(fila.titulo);
        tvTitulo.setTextColor(context.getColor(t.esCancelada() ? R.color.text_secondary : R.color.text_primary));

        // Badge: Pendiente / Completada; la cancelada no lleva
        TextView badge = v.findViewById(R.id.tvBadgeEstado);
        if (t.esCancelada()) {
            badge.setVisibility(View.GONE);
        } else if (t.esPendiente()) {
            badge.setVisibility(View.VISIBLE);
            badge.setText("Pendiente");
            badge.setBackgroundResource(R.drawable.bg_badge_pendiente);
            badge.setTextColor(0xFFF2C27B);
            badge.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_clock_12, 0, 0, 0);
        } else {
            badge.setVisibility(View.VISIBLE);
            badge.setText("Completada");
            badge.setBackgroundResource(R.drawable.bg_pill_pagada);
            badge.setTextColor(context.getColor(R.color.color_positivo));
            badge.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
        }

        ((TextView) v.findViewById(R.id.tvEtiquetaContraparte)).setText(t.esRecibida() ? "De" : "Para");
        ((TextView) v.findViewById(R.id.tvContraparte)).setText(
                t.getContraparteNombre() + " (" + t.getContraparteAlias() + ")");
        ((TextView) v.findViewById(R.id.tvFechaDetalle)).setText(FormatoTransferencia.fechaHora(t.getFecha(), zona));
        boolean confirmada = t.getFechaConfirmacion() != null;
        v.findViewById(R.id.filaConfirmada).setVisibility(confirmada ? View.VISIBLE : View.GONE);
        ((TextView) v.findViewById(R.id.tvFechaConfirmacion)).setText(
                confirmada ? FormatoTransferencia.fechaHora(t.getFechaConfirmacion(), zona) : "");

        String concepto = t.getConcepto();
        ((TextView) v.findViewById(R.id.tvConcepto)).setText(
                concepto == null || concepto.isEmpty() ? "Sin concepto" : concepto);
        boolean editando = v.findViewById(R.id.layoutEditarConcepto).getVisibility() == View.VISIBLE;
        v.findViewById(R.id.btnCambiarConcepto).setVisibility(d.puedeCambiarConcepto() && !editando ? View.VISIBLE : View.GONE);
        Button guardar = v.findViewById(R.id.btnGuardarConcepto);
        guardar.setEnabled(!d.isGuardando());
        guardar.setText(d.isGuardando() ? "Guardando..." : "Guardar");
        v.findViewById(R.id.btnCancelarConcepto).setEnabled(!d.isGuardando());

        TextView tvError = v.findViewById(R.id.tvErrorDetalle);
        tvError.setVisibility(d.getError() != null ? View.VISIBLE : View.GONE);
        tvError.setText(d.getError());

        v.findViewById(R.id.layoutPendiente).setVisibility(d.puedeConfirmarOCancelar() ? View.VISIBLE : View.GONE);
        ((TextView) v.findViewById(R.id.tvAvisoPendienteDetalle)).setText(
                "Esta transferencia todavía no se confirmó: la plata no se movió de tu cuenta. "
                        + "Si no la confirmás, se cancelará automáticamente el "
                        + FormatoTransferencia.vencimiento(t.getFecha(), zona) + ".");
        Button confirmar = v.findViewById(R.id.btnConfirmarPendiente);
        confirmar.setEnabled(!d.isProcesando());
        confirmar.setText(d.isProcesando() ? "Procesando..." : "Confirmar ahora");
        v.findViewById(R.id.btnCancelarPendiente).setEnabled(!d.isProcesando());
    }
}
