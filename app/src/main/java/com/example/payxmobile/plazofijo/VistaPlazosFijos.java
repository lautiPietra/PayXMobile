package com.example.payxmobile.plazofijo;

import com.example.payxmobile.actividad.ListaRemota;
import com.example.payxmobile.model.PlazoFijoResponse;
import com.example.payxmobile.transferencias.FormatoTransferencia;
import com.example.payxmobile.utils.MontoFormatter;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Qué muestra "Mis plazos fijos" (PlazosFijos.jsx) para un estado de la lista y las tasas:
 * ACTIVOS y VENCIDOS separados, cada grupo del más reciente al más viejo (fechaCreacion), el
 * contador "n/max activos" y si se puede constituir otro. Sin Android.
 */
public final class VistaPlazosFijos {

    public enum Modo { CARGANDO, ERROR, VACIO, LISTA }

    public static final String MSG_CARGANDO = "Cargando plazos fijos...";
    public static final String MSG_VACIO = "Todavía no constituiste ningún plazo fijo";

    /** Una tarjeta de la lista, ya formateada. */
    public static final class Tarjeta {
        public final String id;
        public final boolean activo;
        public final String monto;
        public final String badge;
        public final String constituido;
        public final String plazo;
        public final String interes;
        public final String etiquetaTotal;
        public final String total;
        public final String etiquetaFecha;
        public final String vencimiento;

        Tarjeta(PlazoFijoResponse p) {
            id = p.getId();
            activo = p.esActivo();
            monto = "$ " + MontoFormatter.fiat(p.getMonto());
            badge = activo ? "Activo" : "Acreditado";
            constituido = FormatoPlazoFijo.diaMesAnio(p.getFechaInicio());
            plazo = FormatoPlazoFijo.plazoYTna(p.getPlazoDias(), p.getTna());
            interes = "+$ " + MontoFormatter.fiat(p.getInteresEstimado());
            etiquetaTotal = activo ? "Vas a cobrar" : "Se acreditaron";
            total = "$ " + MontoFormatter.fiat(p.getMontoTotal());
            etiquetaFecha = activo ? "Se acredita el" : "Se acreditó el";
            vencimiento = FormatoPlazoFijo.diaMesAnio(p.getFechaVencimiento());
        }
    }

    public final Modo modo;
    public final String error;
    public final boolean desactualizado;
    /** "2/5 activos" (o "2 activos" si todavía no llegó el máximo). */
    public final String subtitulo;
    public final List<Tarjeta> activos;
    public final List<Tarjeta> vencidos;
    /** Ya tiene el máximo: "Constituir nuevo" deshabilitado, con este aviso (null si puede). */
    public final String avisoLimite;

    private VistaPlazosFijos(Modo modo, String error, boolean desactualizado, String subtitulo,
                             List<Tarjeta> activos, List<Tarjeta> vencidos, String avisoLimite) {
        this.modo = modo;
        this.error = error;
        this.desactualizado = desactualizado;
        this.subtitulo = subtitulo;
        this.activos = activos;
        this.vencidos = vencidos;
        this.avisoLimite = avisoLimite;
    }

    /** @param maxActivos de /tasas; null si todavía no llegó (el backend igual rechaza de más). */
    public static VistaPlazosFijos de(ListaRemota.Estado<PlazoFijoResponse> estado, Integer maxActivos) {
        List<Tarjeta> nada = Collections.emptyList();
        if (estado == null || estado.lista == null) {
            boolean error = estado != null && estado.errorPrimeraCarga != null;
            return new VistaPlazosFijos(error ? Modo.ERROR : Modo.CARGANDO, error ? estado.errorPrimeraCarga : null,
                    false, null, nada, nada, null);
        }
        List<PlazoFijoResponse> ordenados = ordenar(estado.lista);
        List<Tarjeta> activos = new ArrayList<>();
        List<Tarjeta> vencidos = new ArrayList<>();
        for (PlazoFijoResponse p : ordenados) {
            if (p.esActivo()) activos.add(new Tarjeta(p));
            else vencidos.add(new Tarjeta(p));
        }
        String subtitulo = maxActivos != null
                ? activos.size() + "/" + maxActivos + " activos"
                : activos.size() + " activos";
        String aviso = maxActivos != null && activos.size() >= maxActivos
                ? ConstitucionPlazoFijo.msgLimite(maxActivos) : null;
        return new VistaPlazosFijos(ordenados.isEmpty() ? Modo.VACIO : Modo.LISTA, null, estado.desactualizado,
                subtitulo, activos, vencidos, aviso);
    }

    /**
     * Más reciente primero por fechaCreacion (el backend ya lo manda así; se reordena por si se
     * agregó uno recién constituido). Estable: en un empate queda el orden de llegada.
     */
    static List<PlazoFijoResponse> ordenar(List<PlazoFijoResponse> lista) {
        List<PlazoFijoResponse> copia = new ArrayList<>(lista);
        copia.sort(Comparator.comparing((PlazoFijoResponse p) -> instante(p.getFechaCreacion())).reversed());
        return copia;
    }

    private static Instant instante(String iso) {
        OffsetDateTime f = FormatoTransferencia.parsear(iso);
        return f != null ? f.toInstant() : Instant.EPOCH;
    }
}
