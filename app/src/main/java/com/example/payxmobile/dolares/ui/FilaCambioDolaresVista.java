package com.example.payxmobile.dolares.ui;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.payxmobile.R;
import com.example.payxmobile.dolares.FilaCambioDolares;
import com.example.payxmobile.model.OperacionCambioResponse;

import java.time.ZoneId;

/** Dibuja una compra/venta de dólares con la misma fila que las transferencias (item_transferencia.xml). */
public final class FilaCambioDolaresVista {

    private FilaCambioDolaresVista() {}

    public static void bind(View fila, OperacionCambioResponse op) {
        Context c = fila.getContext();
        FilaCambioDolares f = FilaCambioDolares.de(op, ZoneId.systemDefault());
        int color = c.getColor(f.esCompra ? R.color.color_positivo : R.color.color_negativo);
        TextView tvTitulo = fila.findViewById(R.id.tvTitulo);
        tvTitulo.setText(f.titulo);
        tvTitulo.setTextColor(c.getColor(R.color.text_primary));
        ((TextView) fila.findViewById(R.id.tvDetalle)).setText(f.detalle);
        fila.findViewById(R.id.tvBadgePendiente).setVisibility(View.GONE);
        TextView tvMonto = fila.findViewById(R.id.tvMonto);
        tvMonto.setText(f.monto);
        tvMonto.setTextColor(color);
        ((TextView) fila.findViewById(R.id.tvFecha)).setText(f.fechaCorta);
        ImageView icono = fila.findViewById(R.id.ivIcono);
        icono.setImageResource(R.drawable.ic_dollar_sign);
        icono.setImageTintList(c.getColorStateList(f.esCompra ? R.color.color_positivo : R.color.color_negativo));
        // Sin detalle (como la web): la fila no es tocable
        fila.setOnClickListener(null);
        fila.setClickable(false);
    }
}
