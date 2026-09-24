package com.example.payxmobile.actividad;

import com.example.payxmobile.model.TransferenciaResponse;
import com.example.payxmobile.transferencias.TransferenciasRepository;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Qué muestran "Mis movimientos" e Inicio para un estado del repositorio y unos filtros.
 * Sin Android: toda la lógica de la pantalla se testea acá.
 */
public final class VistaMovimientos {

    public enum Modo { CARGANDO, ERROR, VACIO, LISTA }

    public static final String MSG_CARGANDO = "Cargando movimientos...";
    public static final String MSG_VACIO = "Todavía no tenés movimientos";
    public static final String MSG_RANGO_INVALIDO = "La fecha \"Desde\" no puede ser posterior a \"Hasta\".";
    public static final String MSG_SIN_RESULTADOS = "No hay movimientos en ese rango de fechas.";
    public static final String MSG_SIN_RESULTADOS_MONEDA = "No hay transferencias en esa moneda.";
    public static final String MSG_SIN_RESULTADOS_MONEDA_Y_FECHA = "No hay transferencias en esa moneda en ese rango de fechas.";
    public static final String MSG_TOPE = "Tenés muchísimas transferencias: se muestran las 2.000 más recientes.";

    public final Modo modo;
    /** Mensaje de la primera carga fallida (modo ERROR). */
    public final String error;
    /** Hay lista, pero el último refresco falló. */
    public final boolean desactualizado;
    public final boolean avisoTope;
    public final boolean mostrarFiltros;
    public final boolean hayFiltro;
    /** Solo con rango inválido: se muestra ESTO y nada más (sin lista, sin resumen, sin "sin resultados"). */
    public final String errorRango;
    /** "{n} de {total} movimientos", solo con filtro activo y rango válido. */
    public final String resumen;
    /** Texto del cartel "sin resultados", o null si hay resultados (o el rango es inválido). */
    public final String sinResultados;
    /** Lo que se dibuja: las primeras tandas de 100. */
    public final List<Actividad> visibles;
    /** "Mostrar más (1.900 restantes)" o null si ya se ve todo. */
    public final String mostrarMas;
    /** Total sin filtros (para Inicio y el resumen). */
    public final int total;
    /** Contador de cada botón de moneda, dentro del rango de fechas elegido. */
    public final Map<FiltroMoneda, Integer> contadoresMoneda;

    private VistaMovimientos(Modo modo, String error, boolean desactualizado, boolean avisoTope,
                             boolean mostrarFiltros, boolean hayFiltro, String errorRango, String resumen,
                             String sinResultados, List<Actividad> visibles, String mostrarMas, int total,
                             Map<FiltroMoneda, Integer> contadoresMoneda) {
        this.modo = modo;
        this.error = error;
        this.desactualizado = desactualizado;
        this.avisoTope = avisoTope;
        this.mostrarFiltros = mostrarFiltros;
        this.hayFiltro = hayFiltro;
        this.errorRango = errorRango;
        this.resumen = resumen;
        this.sinResultados = sinResultados;
        this.visibles = visibles;
        this.mostrarMas = mostrarMas;
        this.total = total;
        this.contadoresMoneda = contadoresMoneda;
    }

    /** Pantalla "Mis movimientos" sin filtro de moneda. */
    public static VistaMovimientos de(TransferenciasRepository.Estado estado, LocalDate desde, LocalDate hasta,
                                      int tandas, ZoneId zona) {
        return de(estado, null, desde, hasta, FiltroMoneda.TODAS, tandas, zona);
    }

    public static VistaMovimientos de(TransferenciasRepository.Estado estado, LocalDate desde, LocalDate hasta,
                                      FiltroMoneda moneda, int tandas, ZoneId zona) {
        return de(estado, null, desde, hasta, moneda, tandas, zona);
    }

    /**
     * Con el feed ya construido (la pantalla lo arma una vez por cada lista nueva del
     * repositorio y lo reusa en cada cambio de filtro). Primero el rango de fechas y sobre eso
     * la moneda; los contadores de moneda salen del resultado por fecha.
     */
    public static VistaMovimientos de(TransferenciasRepository.Estado estado, List<Actividad> construidas,
                                      LocalDate desde, LocalDate hasta, FiltroMoneda moneda, int tandas, ZoneId zona) {
        List<Actividad> nada = Collections.emptyList();
        Map<FiltroMoneda, Integer> sinContadores = new EnumMap<>(FiltroMoneda.class);
        FiltroMoneda filtroMoneda = moneda != null ? moneda : FiltroMoneda.TODAS;
        if (estado == null || estado.lista == null) {
            boolean error = estado != null && estado.errorPrimeraCarga != null;
            // Nunca "no tenés movimientos" si en realidad no se pudieron cargar
            return new VistaMovimientos(error ? Modo.ERROR : Modo.CARGANDO, error ? estado.errorPrimeraCarga : null,
                    false, false, false, false, null, null, null, nada, null, 0, sinContadores);
        }
        List<TransferenciaResponse> transferencias = estado.lista;
        List<Actividad> todas = construidas != null ? construidas : Actividades.construir(transferencias);
        boolean tope = transferencias.size() >= TransferenciasRepository.TOPE_BACKEND;
        if (todas.isEmpty()) {
            return new VistaMovimientos(Modo.VACIO, null, estado.desactualizado, tope, false, false, null, null,
                    null, nada, null, 0, sinContadores);
        }

        boolean hayFiltroFecha = desde != null || hasta != null;
        boolean hayFiltroMoneda = filtroMoneda != FiltroMoneda.TODAS;
        boolean hayFiltro = hayFiltroFecha || hayFiltroMoneda;
        if (Actividades.rangoInvalido(desde, hasta)) {
            return new VistaMovimientos(Modo.LISTA, null, estado.desactualizado, tope, true, true,
                    MSG_RANGO_INVALIDO, null, null, nada, null, todas.size(), FiltroMoneda.contar(nada));
        }
        List<Actividad> enRango = Actividades.filtrarPorFecha(todas, desde, hasta, zona);
        Map<FiltroMoneda, Integer> contadores = FiltroMoneda.contar(enRango);
        List<Actividad> filtradas = FiltroMoneda.filtrar(enRango, filtroMoneda);

        String resumen = hayFiltro
                ? filtradas.size() + " de " + todas.size() + (todas.size() == 1 ? " movimiento" : " movimientos")
                : null;
        String sinResultados = null;
        if (filtradas.isEmpty()) {
            sinResultados = hayFiltroMoneda && hayFiltroFecha ? MSG_SIN_RESULTADOS_MONEDA_Y_FECHA
                    : hayFiltroMoneda ? MSG_SIN_RESULTADOS_MONEDA
                    : MSG_SIN_RESULTADOS;
        }
        int cantidad = Math.min(filtradas.size(), Math.max(1, tandas) * Actividades.POR_TANDA);
        int restantes = filtradas.size() - cantidad;
        String mostrarMas = restantes > 0 ? "Mostrar más (" + miles(restantes) + " restantes)" : null;
        return new VistaMovimientos(Modo.LISTA, null, estado.desactualizado, tope, true, hayFiltro, null, resumen,
                sinResultados, filtradas.subList(0, cantidad), mostrarMas, todas.size(), contadores);
    }

    /** Inicio: las 4 más recientes, sin filtros. */
    public static VistaMovimientos inicio(TransferenciasRepository.Estado estado) {
        VistaMovimientos v = de(estado, null, null, 1, ZoneId.of("UTC")); // el día no importa sin filtros
        if (v.modo != Modo.LISTA) return v;
        List<Actividad> cuatro = v.visibles.subList(0, Math.min(Actividades.CANTIDAD_INICIO, v.visibles.size()));
        return new VistaMovimientos(Modo.LISTA, null, v.desactualizado, false, false, false, null, null, null,
                cuatro, null, v.total, v.contadoresMoneda);
    }

    /** 1900 -> "1.900" (es-AR fijo). */
    static String miles(int n) {
        DecimalFormatSymbols s = new DecimalFormatSymbols(new Locale("es", "AR"));
        s.setGroupingSeparator('.');
        return new DecimalFormat("#,##0", s).format(n);
    }
}
