package com.example.payxmobile.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.payxmobile.R;
import com.example.payxmobile.model.NotificacionResponse;
import com.example.payxmobile.notificaciones.TitulosNotificacion;
import com.example.payxmobile.utils.FormatoFecha;

import java.time.ZoneId;
import java.util.Collections;
import java.util.List;

/** Fila: título corto según el código (genérico si no se conoce) + "mensaje" TAL CUAL + hora corta. */
public class NotificacionesAdapter extends RecyclerView.Adapter<NotificacionesAdapter.ViewHolder> {

    private List<NotificacionResponse> items = Collections.emptyList();

    /** La lista del repositorio es inmutable: si es la misma instancia no cambió nada. */
    public void setItems(List<NotificacionResponse> nuevos) {
        List<NotificacionResponse> lista = nuevos != null ? nuevos : Collections.emptyList();
        if (lista == items) return;
        items = lista;
        notifyDataSetChanged();
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
        holder.tvTitulo.setText(TitulosNotificacion.de(notif.getPlantillaCodigo()));
        holder.tvMensaje.setText(notif.getMensaje());
        // El backend manda UTC con microsegundos ("...T20:53:07.815346Z"): se muestra en hora local
        String hora = FormatoFecha.corta(notif.getFecha(), ZoneId.systemDefault());
        holder.tvFecha.setText(hora);
        holder.tvFecha.setVisibility(hora.isEmpty() ? View.GONE : View.VISIBLE);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvTitulo, tvMensaje, tvFecha;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitulo = itemView.findViewById(R.id.tvTituloNotif);
            tvMensaje = itemView.findViewById(R.id.tvMensajeNotif);
            tvFecha = itemView.findViewById(R.id.tvFechaNotif);
        }
    }
}
