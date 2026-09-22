package com.example.payxmobile.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.payxmobile.R;
import com.example.payxmobile.model.NotificacionResponse;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NotificacionesAdapter extends RecyclerView.Adapter<NotificacionesAdapter.ViewHolder> {

    private final List<NotificacionResponse> items;

    public NotificacionesAdapter(List<NotificacionResponse> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_notificacion, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        NotificacionResponse notif = items.get(position);
        holder.tvMensaje.setText(notif.getMensaje());
        holder.tvFecha.setText(formatearFecha(notif.getFecha()));
        holder.badgeNoLeida.setVisibility(notif.isLeida() ? View.GONE : View.VISIBLE);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private String formatearFecha(String fechaIso) {
        if (fechaIso == null) return "";
        try {
            SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault());
            Date fecha = parser.parse(fechaIso);
            if (fecha == null) return fechaIso;

            String hora = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(fecha);
            Calendar calFecha = Calendar.getInstance();
            calFecha.setTime(fecha);
            Calendar hoy = Calendar.getInstance();

            if (calFecha.get(Calendar.YEAR) == hoy.get(Calendar.YEAR)
                    && calFecha.get(Calendar.DAY_OF_YEAR) == hoy.get(Calendar.DAY_OF_YEAR)) {
                return "Hoy a las " + hora;
            }

            Calendar ayer = Calendar.getInstance();
            ayer.add(Calendar.DAY_OF_YEAR, -1);
            if (calFecha.get(Calendar.YEAR) == ayer.get(Calendar.YEAR)
                    && calFecha.get(Calendar.DAY_OF_YEAR) == ayer.get(Calendar.DAY_OF_YEAR)) {
                return "Ayer a las " + hora;
            }

            String d = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(fecha);
            return d + " a las " + hora;
        } catch (Exception e) {
            return fechaIso;
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvMensaje, tvFecha;
        View badgeNoLeida;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMensaje = itemView.findViewById(R.id.tvMensajeNotif);
            tvFecha = itemView.findViewById(R.id.tvFechaNotif);
            badgeNoLeida = itemView.findViewById(R.id.badgeNoLeida);
        }
    }
}
