package com.example.payxmobile.utils;

import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

/**
 * Avatar igual al de la web: la foto recortada en círculo o, si no hay (o no carga),
 * la inicial del nombre completo sobre el degradé naranja (el fondo de tvInicial).
 */
public final class Avatar {

    private Avatar() {}

    public static String inicial(String nombreCompleto) {
        if (nombreCompleto == null || nombreCompleto.trim().isEmpty()) return "U";
        return nombreCompleto.trim().substring(0, 1).toUpperCase();
    }

    public static void mostrar(ImageView ivFoto, TextView tvInicial, String nombreCompleto, String fotoUrl) {
        tvInicial.setText(inicial(nombreCompleto));
        if (fotoUrl == null || fotoUrl.trim().isEmpty()) {
            Glide.with(ivFoto).clear(ivFoto);
            ivFoto.setVisibility(View.GONE);
            tvInicial.setVisibility(View.VISIBLE);
            return;
        }
        ivFoto.setVisibility(View.VISIBLE);
        Glide.with(ivFoto)
                .load(fotoUrl)
                .circleCrop()
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model,
                                                @NonNull Target<Drawable> target, boolean isFirstResource) {
                        ivFoto.setVisibility(View.GONE);
                        tvInicial.setVisibility(View.VISIBLE);
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(@NonNull Drawable resource, @NonNull Object model,
                                                   Target<Drawable> target, @NonNull DataSource dataSource,
                                                   boolean isFirstResource) {
                        tvInicial.setVisibility(View.INVISIBLE);
                        return false;
                    }
                })
                .into(ivFoto);
    }
}
