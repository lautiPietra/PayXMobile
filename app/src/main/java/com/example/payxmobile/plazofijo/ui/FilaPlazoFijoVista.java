package com.example.payxmobile.plazofijo.ui;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.payxmobile.R;
import com.example.payxmobile.actividad.Actividad;
import com.example.payxmobile.activities.MisPlazosFijosActivity;
import com.example.payxmobile.plazofijo.FilaPlazoFijo;

import java.time.ZoneId;

/** Dibuja un evento de plazo fijo con la misma fila que las transferencias (item_transferencia.xml). */
public final class FilaPlazoFijoVista {

    private FilaPlazoFijoVista() {}

    public static void bind(View fila, Actividad a) {
        Context c = fila.getContext();
        FilaPlazoFijo f = FilaPlazoFijo.de(a.plazoFijo, a.eventoPlazoFijo, ZoneId.systemDefault());
        int color = f.positivo ? R.color.color_positivo : R.color.color_negativo;
        TextView tvTitulo = fila.findViewById(R.id.tvTitulo);
        tvTitulo.setText(f.titulo);
        tvTitulo.setTextColor(c.getColor(R.color.text_primary));
        ((TextView) fila.findViewById(R.id.tvDetalle)).setText(f.detalle);
        fila.findViewById(R.id.tvBadgePendiente).setVisibility(View.GONE);
        TextView tvMonto = fila.findViewById(R.id.tvMonto);
        tvMonto.setText(f.monto);
        tvMonto.setTextColor(c.getColor(color));
        ((TextView) fila.findViewById(R.id.tvFecha)).setText(f.fecha);
        ImageView icono = fila.findViewById(R.id.ivIcono);
        icono.setImageResource(R.drawable.ic_piggy_bank);
        icono.setImageTintList(c.getColorStateList(color));
        // Como la web: tocarla lleva a "Mis plazos fijos"
        fila.setOnClickListener(v -> v.getContext().startActivity(new Intent(v.getContext(), MisPlazosFijosActivity.class)));
    }
}
