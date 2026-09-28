package com.example.payxmobile.servicios.ui;

import com.example.payxmobile.R;

/**
 * Ícono y color por código de servicio (como serviciosTemas.js), solo para dibujar el catálogo que
 * trae el backend. Un código nuevo que la app no conozca usa el rayo en gris: no rompe.
 */
public final class EstiloServicio {

    private EstiloServicio() {}

    public static int icono(String codigo) {
        if (codigo == null) return R.drawable.ic_zap;
        switch (codigo) {
            case "GAS": return R.drawable.ic_flame;
            case "AGUA": return R.drawable.ic_droplet;
            case "INTERNET": return R.drawable.ic_wifi;
            case "CABLE": return R.drawable.ic_tv;
            case "TELEFONIA": return R.drawable.ic_phone;
            default: return R.drawable.ic_zap; // LUZ y desconocidos
        }
    }

    public static int color(String codigo) {
        if (codigo == null) return R.color.text_secondary;
        switch (codigo) {
            case "LUZ": return R.color.color_servicio_luz;
            case "GAS": return R.color.color_servicio_gas;
            case "AGUA": return R.color.color_servicio_agua;
            case "INTERNET": return R.color.color_servicio_internet;
            case "CABLE": return R.color.color_servicio_tv;
            case "TELEFONIA": return R.color.color_servicio_telefonia;
            default: return R.color.text_secondary;
        }
    }
}
