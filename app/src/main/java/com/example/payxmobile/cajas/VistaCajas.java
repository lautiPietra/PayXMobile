package com.example.payxmobile.cajas;

import com.example.payxmobile.actividad.ListaRemota;
import com.example.payxmobile.model.CajaAhorroResponse;
import com.example.payxmobile.utils.MontoFormatter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Qué muestra "Cajas de ahorro" (CajasAhorro.jsx + CajaAhorroCard.jsx) para un estado de la lista y
 * el límite: tarjetas con saldo y progreso de la meta, y si se puede crear otra. Sin Android.
 */
public final class VistaCajas {

    public enum Modo { CARGANDO, ERROR, VACIO, LISTA }

    public static final String MSG_CARGANDO = "Cargando tus cajas de ahorro...";
    public static final String MSG_VACIO = "Todavía no tenés cajas de ahorro";
    public static final String SIN_META = "Sin meta definida";
    public static final String META_CUMPLIDA = "¡Meta cumplida!";

    public static final class Tarjeta {
        public final CajaAhorroResponse caja;
        public final String nombre;
        public final String color;
        public final String icono;
        public final String saldo;
        public final boolean tieneMeta;
        /** 0-100, con tope visual en 100 aunque el saldo pase la meta. */
        public final int progreso;
        /** "$ 2.500,00 de $ 10.000,00" (null sin meta). */
        public final String textoMeta;
        /** "Faltan $ 7.500,00" / "¡Meta cumplida!" / "Sin meta definida". */
        public final String estadoMeta;
        public final boolean metaCumplida;
        /** Sin saldo no hay nada que retirar (como la web). */
        public final boolean puedeRetirar;
        /** Se está eliminando: acciones deshabilitadas. */
        public final boolean eliminando;

        Tarjeta(CajaAhorroResponse c, boolean eliminando) {
            caja = c;
            nombre = c.getNombre();
            color = TemasCaja.colorODefecto(c.getColor());
            icono = TemasCaja.iconoODefecto(c.getIcono());
            BigDecimal s = c.getSaldo() != null ? c.getSaldo() : BigDecimal.ZERO;
            saldo = "$ " + MontoFormatter.fiat(s);
            BigDecimal meta = c.getMontoObjetivo();
            tieneMeta = meta != null && meta.signum() > 0;
            metaCumplida = tieneMeta && s.compareTo(meta) >= 0;
            progreso = tieneMeta ? progreso(s, meta) : 0;
            textoMeta = tieneMeta ? saldo + " de $ " + MontoFormatter.fiat(meta) : null;
            estadoMeta = !tieneMeta ? SIN_META
                    : metaCumplida ? META_CUMPLIDA
                    : "Faltan $ " + MontoFormatter.fiat(meta.subtract(s));
            puedeRetirar = s.signum() > 0 && !eliminando;
            this.eliminando = eliminando;
        }
    }

    public final Modo modo;
    public final String error;
    public final boolean desactualizado;
    /** "2/5 cajas" (o "2 cajas" si todavía no llegó el límite). */
    public final String subtitulo;
    public final boolean puedeCrear;
    /** Con el máximo alcanzado: el aviso (y "Nueva caja" deshabilitado). */
    public final String avisoLimite;
    public final List<Tarjeta> tarjetas;

    private VistaCajas(Modo modo, String error, boolean desactualizado, String subtitulo, boolean puedeCrear,
                       String avisoLimite, List<Tarjeta> tarjetas) {
        this.modo = modo;
        this.error = error;
        this.desactualizado = desactualizado;
        this.subtitulo = subtitulo;
        this.puedeCrear = puedeCrear;
        this.avisoLimite = avisoLimite;
        this.tarjetas = tarjetas;
    }

    /**
     * @param maxCajas     de GET /limite; null si no llegó (no se inventa: decide el backend)
     * @param idEliminando la caja que se está eliminando, o null
     */
    public static VistaCajas de(ListaRemota.Estado<CajaAhorroResponse> estado, Integer maxCajas, String idEliminando) {
        if (estado == null || estado.lista == null) {
            boolean error = estado != null && estado.errorPrimeraCarga != null;
            // Sin saber cuántas tiene no se habilita crear (se habilita en cuanto llega la lista)
            return new VistaCajas(error ? Modo.ERROR : Modo.CARGANDO, error ? estado.errorPrimeraCarga : null,
                    false, null, false, null, Collections.emptyList());
        }
        List<Tarjeta> tarjetas = new ArrayList<>();
        for (CajaAhorroResponse c : estado.lista) tarjetas.add(new Tarjeta(c, c.getId() != null && c.getId().equals(idEliminando)));
        int n = tarjetas.size();
        boolean limite = FormularioCaja.limiteAlcanzado(maxCajas, n);
        String subtitulo = maxCajas != null ? n + "/" + maxCajas + (maxCajas == 1 ? " caja" : " cajas")
                : n + (n == 1 ? " caja" : " cajas");
        return new VistaCajas(tarjetas.isEmpty() ? Modo.VACIO : Modo.LISTA, null, estado.desactualizado, subtitulo,
                !limite, limite ? FormularioCaja.msgLimite(maxCajas) : null, tarjetas);
    }

    /** saldo/meta en %, hacia abajo (no dice 100 % si falta un centavo), con tope en 100. */
    static int progreso(BigDecimal saldo, BigDecimal meta) {
        if (meta == null || meta.signum() <= 0 || saldo == null || saldo.signum() <= 0) return 0;
        BigDecimal pct = saldo.multiply(BigDecimal.valueOf(100)).divide(meta, 0, RoundingMode.DOWN);
        return Math.min(100, pct.intValue());
    }
}
