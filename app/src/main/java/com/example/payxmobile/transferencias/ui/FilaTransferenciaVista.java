package com.example.payxmobile.transferencias.ui;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.payxmobile.R;
import com.example.payxmobile.model.TransferenciaResponse;
import com.example.payxmobile.transferencias.FilaTransferencia;

import java.time.ZoneId;

/** Dibuja una fila de transferencia (item_transferencia.xml). La usan Inicio y "Mis movimientos". */
public final class FilaTransferenciaVista {

    private FilaTransferenciaVista() {}

    public static void bind(View fila, TransferenciaResponse t) {
        Context c = fila.getContext();
        FilaTransferencia f = FilaTransferencia.de(t, ZoneId.systemDefault());
        TextView tvTitulo = fila.findViewById(R.id.tvTitulo);
        tvTitulo.setText(f.titulo);
        tvTitulo.setTextColor(c.getColor(t.esCancelada() ? R.color.text_secondary : R.color.text_primary));
        ((TextView) fila.findViewById(R.id.tvDetalle)).setText(f.detalle);
        fila.findViewById(R.id.tvBadgePendiente).setVisibility(f.pendiente ? View.VISIBLE : View.GONE);
        TextView tvMonto = fila.findViewById(R.id.tvMonto);
        tvMonto.setText(f.monto);
        tvMonto.setTextColor(c.getColor(colorMonto(f.estilo)));
        ((TextView) fila.findViewById(R.id.tvFecha)).setText(f.fechaCorta);

        ImageView icono = fila.findViewById(R.id.ivIcono);
        switch (f.icono) {
            case CANCELADA:
                icono.setImageResource(R.drawable.ic_close);
                icono.setImageTintList(c.getColorStateList(R.color.text_secondary));
                break;
            case CRIPTO:
                icono.setImageResource(R.drawable.ic_coins);
                icono.setImageTintList(c.getColorStateList(R.color.color_cripto));
                break;
            case RECIBIDA:
                icono.setImageResource(R.drawable.ic_arrow_down_circle);
                icono.setImageTintList(c.getColorStateList(R.color.color_positivo));
                break;
            default:
                icono.setImageResource(R.drawable.ic_arrow_up_circle);
                icono.setImageTintList(c.getColorStateList(R.color.color_negativo));
                break;
        }
    }

    public static int colorMonto(FilaTransferencia.Estilo estilo) {
        switch (estilo) {
            case POSITIVO: return R.color.color_positivo;
            case NEGATIVO: return R.color.color_negativo;
            default: return R.color.text_secondary;
        }
    }
}
