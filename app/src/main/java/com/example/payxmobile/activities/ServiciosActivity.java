package com.example.payxmobile.activities;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.payxmobile.R;

public class ServiciosActivity extends AppCompatActivity {

    private static class Servicio {
        String nombre, proveedor, monto, fecha;
        int icono, color;

        Servicio(String nombre, String proveedor, String monto, String fecha, int icono, int color) {
            this.nombre = nombre;
            this.proveedor = proveedor;
            this.monto = monto;
            this.fecha = fecha;
            this.icono = icono;
            this.color = color;
        }
    }

    private final Servicio[] servicios = {
            new Servicio("Luz", "Edesur", "$ 6.401,38", "Vence el 30-sept", R.drawable.ic_zap, R.color.color_servicio_luz),
            new Servicio("Gas", "Metrogas", "$ 2.614,85", "Vence el 30-sept", R.drawable.ic_flame, R.color.color_servicio_gas),
            new Servicio("Agua", "AySA", "$ 2.414,81", "Vence el 30-sept", R.drawable.ic_droplet, R.color.color_servicio_agua),
            new Servicio("Internet", "Fibertel", "$ 10.206,06", "Vence el 30-sept", R.drawable.ic_wifi, R.color.color_servicio_internet),
            new Servicio("TV por cable", "DirecTV", "$ 6.151,37", "Vence el 15-sept", R.drawable.ic_tv, R.color.color_servicio_tv),
            new Servicio("Telefonía celular", "Movistar", "$ 5.668,65", "Vence el 15-sept", R.drawable.ic_phone, R.color.color_servicio_telefonia),
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_servicios);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        LinearLayout contenedorFacturas = findViewById(R.id.contenedorFacturas);
        for (Servicio servicio : servicios) {
            contenedorFacturas.addView(crearItemServicio(contenedorFacturas, servicio));
        }

        findViewById(R.id.tabFacturas).setOnClickListener(v -> mostrarTab(true));
        findViewById(R.id.tabRealizados).setOnClickListener(v -> mostrarTab(false));
    }

    private View crearItemServicio(LinearLayout padre, Servicio servicio) {
        View item = LayoutInflater.from(this).inflate(R.layout.item_servicio, padre, false);

        ImageView icono = item.findViewById(R.id.ivIconoServicio);
        icono.setImageResource(servicio.icono);
        icono.setBackgroundTintList(getColorStateList(servicio.color));

        ((TextView) item.findViewById(R.id.tvNombreServicio)).setText(servicio.nombre);
        ((TextView) item.findViewById(R.id.tvProveedorServicio)).setText(servicio.proveedor);
        ((TextView) item.findViewById(R.id.tvMontoServicio)).setText(servicio.monto);

        TextView tvFecha = item.findViewById(R.id.tvFechaServicio);
        tvFecha.setText(servicio.fecha);
        tvFecha.setTextColor(getColor(R.color.color_logout));

        item.findViewById(R.id.btnEstadoServicio).setOnClickListener(v ->
                Toast.makeText(this, "Esta función va a estar disponible próximamente.", Toast.LENGTH_SHORT).show());

        return item;
    }

    private void mostrarTab(boolean facturas) {
        findViewById(R.id.contenedorFacturas).setVisibility(facturas ? View.VISIBLE : View.GONE);
        findViewById(R.id.contenedorRealizados).setVisibility(facturas ? View.GONE : View.VISIBLE);

        ((TextView) findViewById(R.id.tabFacturas)).setTextColor(
                getColor(facturas ? R.color.payx_orange : R.color.text_secondary));
        ((TextView) findViewById(R.id.tabRealizados)).setTextColor(
                getColor(facturas ? R.color.text_secondary : R.color.payx_orange));

        findViewById(R.id.tabFacturas).setSelected(facturas);
        findViewById(R.id.indicadorFacturas).setBackgroundColor(
                getColor(facturas ? R.color.payx_orange : R.color.card_border));
        findViewById(R.id.indicadorRealizados).setBackgroundColor(
                getColor(facturas ? R.color.card_border : R.color.payx_orange));
    }
}
