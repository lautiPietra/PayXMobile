package com.example.payxmobile.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.payxmobile.R;
import com.example.payxmobile.model.NotificacionResponse;
import com.example.payxmobile.utils.FormatoFecha;

import java.time.ZoneId;
import java.util.List;

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

    // El backend manda "2026-09-24T20:53:07.815346Z" (UTC con microsegundos): se muestra en hora local
    private String formatearFecha(String fechaIso) {
        return FormatoFecha.fechaHora(fechaIso, ZoneId.systemDefault());
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
