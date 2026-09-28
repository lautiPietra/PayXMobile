package com.example.payxmobile.cajas;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Colores e íconos que se pueden elegir para una caja. Tienen que ser EXACTAMENTE los de las
 * whitelists del backend (CajaAhorroService.COLORES_VALIDOS / ICONOS_VALIDOS): cualquier otro valor
 * da 400. El orden es el de la web (utils/cajaAhorroTemas.js). Clase pura.
 */
public final class TemasCaja {

    public static final List<String> COLORES = Collections.unmodifiableList(Arrays.asList(
            "#ff6b1a", "#f59e0b", "#16a34a", "#0ea5e9", "#8b5cf6", "#ec4899", "#64748b", "#dc2626"));

    public static final class Icono {
        /** El string que viaja al backend. */
        public final String clave;
        public final String etiqueta;

        Icono(String clave, String etiqueta) {
            this.clave = clave;
            this.etiqueta = etiqueta;
        }
    }

    public static final List<Icono> ICONOS = Collections.unmodifiableList(Arrays.asList(
            new Icono("piggy-bank", "Ahorro"),
            new Icono("target", "Meta"),
            new Icono("shield", "Emergencia"),
            new Icono("umbrella", "Imprevistos"),
            new Icono("briefcase", "Trabajo"),
            new Icono("book-open", "Estudio"),
            new Icono("heart", "Salud"),
            new Icono("gift", "Regalo"),
            new Icono("shopping-bag", "Compras"),
            new Icono("home", "Casa"),
            new Icono("wallet", "Billetera"),
            new Icono("trending-up", "Inversión")));

    private TemasCaja() {}

    public static boolean colorValido(String color) {
        return color != null && COLORES.contains(color);
    }

    public static boolean iconoValido(String icono) {
        if (icono == null) return false;
        for (Icono i : ICONOS) if (i.clave.equals(icono)) return true;
        return false;
    }

    /** Para dibujar una caja que vino del backend: si el color no es conocido, el primero. */
    public static String colorODefecto(String color) {
        return colorValido(color) ? color : COLORES.get(0);
    }

    public static String iconoODefecto(String icono) {
        return iconoValido(icono) ? icono : ICONOS.get(0).clave;
    }
}
