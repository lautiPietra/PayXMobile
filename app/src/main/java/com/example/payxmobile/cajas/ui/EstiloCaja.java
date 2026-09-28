package com.example.payxmobile.cajas.ui;

import android.content.res.ColorStateList;
import android.graphics.Color;

import com.example.payxmobile.R;
import com.example.payxmobile.cajas.TemasCaja;

/** El drawable de cada ícono de la whitelist y el color de la caja listo para pintar. */
public final class EstiloCaja {

    private EstiloCaja() {}

    public static int icono(String clave) {
        switch (TemasCaja.iconoODefecto(clave)) {
            case "target": return R.drawable.ic_target;
            case "shield": return R.drawable.ic_shield;
            case "umbrella": return R.drawable.ic_umbrella;
            case "briefcase": return R.drawable.ic_briefcase;
            case "book-open": return R.drawable.ic_book;
            case "heart": return R.drawable.ic_heart;
            case "gift": return R.drawable.ic_gift;
            case "shopping-bag": return R.drawable.ic_bag;
            case "home": return R.drawable.ic_home;
            case "wallet": return R.drawable.ic_wallet;
            case "trending-up": return R.drawable.ic_trending_up;
            default: return R.drawable.ic_piggy_bank;
        }
    }

    public static int color(String hex) {
        return Color.parseColor(TemasCaja.colorODefecto(hex));
    }

    public static ColorStateList tinte(String hex) {
        return ColorStateList.valueOf(color(hex));
    }
}
