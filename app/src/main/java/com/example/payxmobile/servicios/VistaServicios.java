package com.example.payxmobile.servicios;

import com.example.payxmobile.actividad.ListaRemota;
import com.example.payxmobile.model.FacturaResponse;
import com.example.payxmobile.model.ServicioConFacturaResponse;
import com.example.payxmobile.transferencias.FormatoTransferencia;
import com.example.payxmobile.utils.MontoFormatter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Qué muestra "Pago de servicios" (Servicios.jsx + ServicioCard.jsx) para el estado de las dos listas.
 * Todo (montos, fechas, "vencida") sale de las respuestas del backend; el cliente solo ordena y
 * formatea. Sin Android.
 */
public final class VistaServicios {

    public enum Modo { CARGANDO, ERROR, VACIO, LISTA }

    public enum EstadoFactura { PENDIENTE, VENCIDA, PAGADA }

    public static final String MSG_CARGANDO = "Cargando tus servicios...";
    public static final String MSG_CARGANDO_HISTORIAL = "Cargando tus facturas...";
    public static final String MSG_HISTORIAL_VACIO = "Todavía no tenés facturas";

    /** Una tarjeta del mes (siempre las que trae GET /api/facturas, en su orden). */
    public static final class Tarjeta {
        public final ServicioConFacturaResponse servicio;
        public final String codigo;
        public final String nombre;
        public final String proveedor;
        public final String monto;
        public final EstadoFactura estado;
        /** "Vence el 15/10/2026" / "Venció el 15/09/2026" / "Pagada el 20/09/2026". */
        public final String fecha;
        public final boolean puedePagar;

        Tarjeta(ServicioConFacturaResponse s, ZoneId zona) {
            servicio = s;
            codigo = s.getServicioCodigo();
            nombre = s.getNombre();
            proveedor = s.getProveedor();
            monto = "$ " + MontoFormatter.fiat(s.getMonto());
            estado = s.esPagada() ? EstadoFactura.PAGADA : s.isVencida() ? EstadoFactura.VENCIDA : EstadoFactura.PENDIENTE;
            fecha = estado == EstadoFactura.PAGADA ? "Pagada el " + FormatoServicio.diaDeInstante(s.getFechaPago(), zona)
                    : (estado == EstadoFactura.VENCIDA ? "Venció el " : "Vence el ") + FormatoServicio.diaTexto(s.getFechaVencimiento());
            puedePagar = estado != EstadoFactura.PAGADA && s.getFacturaId() != null;
        }
    }

    /** Una fila de factura (anteriores sin pagar e historial). */
    public static final class Fila {
        public final FacturaResponse factura;
        public final String codigo;
        public final String nombre;
        /** "septiembre de 2026 · Pagada el 20/09/2026". */
        public final String detalle;
        public final String monto;
        public final EstadoFactura estado;
        /** "Pagada" / "Vencida" / "Pendiente". */
        public final String badge;
        public final boolean puedePagar;

        Fila(FacturaResponse f, LocalDate hoy, ZoneId zona) {
            factura = f;
            codigo = f.getServicioCodigo();
            nombre = f.getServicioNombre();
            monto = "$ " + MontoFormatter.fiat(f.getMonto());
            LocalDate vence = FormatoServicio.dia(f.getFechaVencimiento());
            // El historial no trae "vencida": mismo criterio que el backend (pendiente y ya pasó el día)
            estado = f.esPagada() ? EstadoFactura.PAGADA
                    : vence != null && vence.isBefore(hoy) ? EstadoFactura.VENCIDA : EstadoFactura.PENDIENTE;
            String cuando = estado == EstadoFactura.PAGADA ? "Pagada el " + FormatoServicio.diaDeInstante(f.getFechaPago(), zona)
                    : (estado == EstadoFactura.VENCIDA ? "Venció el " : "Vence el ") + FormatoServicio.diaTexto(f.getFechaVencimiento());
            detalle = FormatoServicio.periodo(f.getPeriodo()) + " · " + cuando;
            badge = estado == EstadoFactura.PAGADA ? "Pagada" : estado == EstadoFactura.VENCIDA ? "Vencida" : "Pendiente";
            puedePagar = !f.esPagada();
        }
    }

    public final Modo modo;
    public final String error;
    public final List<Tarjeta> tarjetas;
    /** Facturas de meses anteriores que quedaron sin pagar (se pagan desde la primera pestaña, como la web). */
    public final List<Fila> anteriores;
    public final Modo modoHistorial;
    public final String errorHistorial;
    /** TODAS las facturas, la más reciente primero. */
    public final List<Fila> historial;

    private VistaServicios(Modo modo, String error, List<Tarjeta> tarjetas, List<Fila> anteriores, Modo modoHistorial,
                           String errorHistorial, List<Fila> historial) {
        this.modo = modo;
        this.error = error;
        this.tarjetas = tarjetas;
        this.anteriores = anteriores;
        this.modoHistorial = modoHistorial;
        this.errorHistorial = errorHistorial;
        this.historial = historial;
    }

    public static VistaServicios de(ListaRemota.Estado<ServicioConFacturaResponse> catalogo,
                                    ListaRemota.Estado<FacturaResponse> facturas, LocalDate hoy, ZoneId zona) {
        List<Tarjeta> tarjetas = new ArrayList<>();
        Modo modo;
        String error = null;
        if (catalogo == null || catalogo.lista == null) {
            boolean fallo = catalogo != null && catalogo.errorPrimeraCarga != null;
            modo = fallo ? Modo.ERROR : Modo.CARGANDO;
            error = fallo ? catalogo.errorPrimeraCarga : null;
        } else {
            for (ServicioConFacturaResponse s : catalogo.lista) tarjetas.add(new Tarjeta(s, zona));
            modo = tarjetas.isEmpty() ? Modo.VACIO : Modo.LISTA;
        }

        List<Fila> anteriores = new ArrayList<>();
        List<Fila> historial = new ArrayList<>();
        Modo modoHistorial;
        String errorHistorial = null;
        if (facturas == null || facturas.lista == null) {
            boolean fallo = facturas != null && facturas.errorPrimeraCarga != null;
            modoHistorial = fallo ? Modo.ERROR : Modo.CARGANDO;
            errorHistorial = fallo ? facturas.errorPrimeraCarga : null;
        } else {
            for (FacturaResponse f : ordenarHistorial(facturas.lista)) historial.add(new Fila(f, hoy, zona));
            modoHistorial = historial.isEmpty() ? Modo.VACIO : Modo.LISTA;
            // Las del mes ya son tarjetas: no se repiten. Solo se sabe cuáles son cuando llegó el catálogo.
            if (catalogo != null && catalogo.lista != null) {
                Set<String> delMes = new HashSet<>();
                for (ServicioConFacturaResponse s : catalogo.lista) delMes.add(s.getFacturaId());
                List<FacturaResponse> pendientes = new ArrayList<>();
                for (FacturaResponse f : facturas.lista) {
                    if (!f.esPagada() && !delMes.contains(f.getId())) pendientes.add(f);
                }
                // La que venció hace más tiempo primero (como la web)
                pendientes.sort(Comparator.comparing((FacturaResponse f) -> diaOMin(f.getFechaVencimiento())));
                for (FacturaResponse f : pendientes) anteriores.add(new Fila(f, hoy, zona));
            }
        }
        return new VistaServicios(modo, error, tarjetas, anteriores, modoHistorial, errorHistorial, historial);
    }

    /**
     * Historial, la más reciente primero: por vencimiento (= período) y, dentro del mismo, la pagada
     * más recientemente arriba. El backend no garantiza el orden. Estable: en un empate queda el de llegada.
     */
    public static List<FacturaResponse> ordenarHistorial(List<FacturaResponse> lista) {
        List<FacturaResponse> copia = new ArrayList<>(lista);
        copia.sort(Comparator.comparing((FacturaResponse f) -> diaOMin(f.getFechaVencimiento())).reversed()
                .thenComparing(Comparator.comparing((FacturaResponse f) -> instanteOMin(f.getFechaPago())).reversed()));
        return Collections.unmodifiableList(copia);
    }

    private static LocalDate diaOMin(String iso) {
        LocalDate d = FormatoServicio.dia(iso);
        return d != null ? d : LocalDate.MIN;
    }

    private static Instant instanteOMin(String iso) {
        OffsetDateTime f = FormatoTransferencia.parsear(iso);
        return f != null ? f.toInstant() : Instant.MIN;
    }
}
