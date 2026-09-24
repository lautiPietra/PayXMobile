package com.example.payxmobile.saldos;

import com.example.payxmobile.model.CotizacionCripto;
import com.example.payxmobile.model.PerfilResponse;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;

/**
 * Foto inmutable del estado de saldos que publica {@link SaldosRepository}.
 * Regla: si {@link #perfil} es null NO hay saldo para mostrar (nunca se inventa un 0).
 */
public final class EstadoSaldos {

    /** Último perfil bueno, o null si todavía no se pudo cargar ninguno. */
    public final PerfilResponse perfil;
    /** Falló la primera carga (no hay ningún saldo): mensaje para mostrar con "Reintentar". */
    public final String errorPrimeraCarga;
    /** Hay saldo, pero el último refresco falló: se muestra el anterior con un aviso discreto. */
    public final boolean desactualizado;
    /** Precio en pesos por símbolo. Si una cripto no está, NO tiene cotización (no es precio 0). */
    public final Map<String, CotizacionCripto> cotizaciones;
    public final boolean cargandoSaldo;
    public final boolean cargandoCotizaciones;
    /** El backend respondió 403 al pedir el perfil: token inválido/vencido o cuenta desactivada. */
    public final boolean sesionInvalida;

    EstadoSaldos(PerfilResponse perfil, String errorPrimeraCarga, boolean desactualizado,
                 Map<String, CotizacionCripto> cotizaciones, boolean cargandoSaldo,
                 boolean cargandoCotizaciones, boolean sesionInvalida) {
        this.perfil = perfil;
        this.errorPrimeraCarga = errorPrimeraCarga;
        this.desactualizado = desactualizado;
        this.cotizaciones = Collections.unmodifiableMap(cotizaciones);
        this.cargandoSaldo = cargandoSaldo;
        this.cargandoCotizaciones = cargandoCotizaciones;
        this.sesionInvalida = sesionInvalida;
    }

    public boolean tieneSaldos() {
        return perfil != null;
    }

    /** Primera carga todavía en curso (mostrar skeleton, no un número). */
    public boolean esPrimeraCarga() {
        return perfil == null && errorPrimeraCarga == null;
    }

    /** Precio en pesos de una unidad, o null si no hay cotización para ese símbolo. */
    public BigDecimal precio(String simbolo) {
        CotizacionCripto c = cotizaciones.get(simbolo);
        return c != null ? c.getPrecio() : null;
    }

    public boolean cotizacionDesactualizada(String simbolo) {
        CotizacionCripto c = cotizaciones.get(simbolo);
        return c != null && c.isDesactualizada();
    }
}
