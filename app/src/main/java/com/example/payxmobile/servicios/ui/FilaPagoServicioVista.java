package com.example.payxmobile.servicios.ui;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.payxmobile.R;
import com.example.payxmobile.activities.ServiciosActivity;
import com.example.payxmobile.model.FacturaResponse;
import com.example.payxmobile.servicios.FilaPagoServicio;

import java.time.ZoneId;

/** Dibuja un pago de servicio con la misma fila que las transferencias (item_transferencia.xml). */
public final class FilaPagoServicioVista {

    private FilaPagoServicioVista() {}

    public static void bind(View fila, FacturaResponse f) {
        Context c = fila.getContext();
        FilaPagoServicio p = FilaPagoServicio.de(f, ZoneId.systemDefault());
        TextView tvTitulo = fila.findViewById(R.id.tvTitulo);
        tvTitulo.setText(p.titulo);
        tvTitulo.setTextColor(c.getColor(R.color.text_primary));
        ((TextView) fila.findViewById(R.id.tvDetalle)).setText(p.detalle);
        fila.findViewById(R.id.tvBadgePendiente).setVisibility(View.GONE);
        TextView tvMonto = fila.findViewById(R.id.tvMonto);
        tvMonto.setText(p.monto);
        tvMonto.setTextColor(c.getColor(R.color.color_negativo));
        ((TextView) fila.findViewById(R.id.tvFecha)).setText(p.fecha);
        ImageView icono = fila.findViewById(R.id.ivIcono);
        icono.setImageResource(EstiloServicio.icono(p.codigo));
        icono.setImageTintList(c.getColorStateList(EstiloServicio.color(p.codigo)));
        fila.setOnClickListener(v -> v.getContext().startActivity(new Intent(v.getContext(), ServiciosActivity.class)));
    }
}
