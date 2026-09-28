package com.example.payxmobile.cajas.ui;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.payxmobile.R;
import com.example.payxmobile.activities.CajasAhorroActivity;
import com.example.payxmobile.cajas.FilaMovimientoCaja;
import com.example.payxmobile.cajas.MovimientoCaja;

import java.time.ZoneId;

/**
 * Dibuja un depósito/retiro de caja con la misma fila que las transferencias (item_transferencia.xml),
 * con el ícono y el color que el usuario eligió para esa caja.
 */
public final class FilaMovimientoCajaVista {

    private FilaMovimientoCajaVista() {}

    public static void bind(View fila, MovimientoCaja m) {
        Context c = fila.getContext();
        FilaMovimientoCaja f = FilaMovimientoCaja.de(m, ZoneId.systemDefault());
        TextView tvTitulo = fila.findViewById(R.id.tvTitulo);
        tvTitulo.setText(f.titulo);
        tvTitulo.setTextColor(c.getColor(R.color.text_primary));
        ((TextView) fila.findViewById(R.id.tvDetalle)).setText(f.detalle);
        fila.findViewById(R.id.tvBadgePendiente).setVisibility(View.GONE);
        TextView tvMonto = fila.findViewById(R.id.tvMonto);
        tvMonto.setText(f.monto);
        tvMonto.setTextColor(c.getColor(f.positivo ? R.color.color_positivo : R.color.color_negativo));
        ((TextView) fila.findViewById(R.id.tvFecha)).setText(f.fecha);
        ImageView icono = fila.findViewById(R.id.ivIcono);
        icono.setImageResource(EstiloCaja.icono(f.icono));
        icono.setImageTintList(EstiloCaja.tinte(f.color));
        fila.setOnClickListener(v -> v.getContext().startActivity(new Intent(v.getContext(), CajasAhorroActivity.class)));
    }
}
