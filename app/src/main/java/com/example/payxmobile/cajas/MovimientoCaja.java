package com.example.payxmobile.cajas;

import com.example.payxmobile.model.CajaAhorroResponse;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Un depósito o retiro en una caja de ahorro, para el feed de movimientos (extensión propia de la
 * app: la web no los muestra).
 *
 * El backend NO tiene historial de estas operaciones: depositar/retirar devuelven la caja con su
 * saldo nuevo, sin id de operación ni fecha. Por eso:
 * - id: LOCAL, "{cajaId}-{epochMillis}-{secuencia}" (la secuencia evita choques en el mismo ms).
 * - fecha: la hora del TELÉFONO en el momento en que llegó la respuesta, con su offset.
 * Solo existen los que hizo la app en este teléfono; se guardan cifrados por usuario (ver
 * {@link MovimientosCajaSesion}).
 */
public final class MovimientoCaja {

    public enum Tipo { DEPOSITO, RETIRO }

    private static final AtomicLong SECUENCIA = new AtomicLong();

    public final String id;
    public final Tipo tipo;
    public final String cajaId;
    public final String nombreCaja;
    public final String color;
    public final String icono;
    public final BigDecimal monto;
    /** ISO-8601 con offset (hora local del teléfono). */
    public final String fecha;
    /** Retiro implícito: la caja se eliminó con saldo y el backend lo devolvió a la cuenta. */
    public final boolean cajaEliminada;

    MovimientoCaja(String id, Tipo tipo, String cajaId, String nombreCaja, String color, String icono,
                   BigDecimal monto, String fecha, boolean cajaEliminada) {
        this.id = id;
        this.tipo = tipo;
        this.cajaId = cajaId;
        this.nombreCaja = nombreCaja;
        this.color = color;
        this.icono = icono;
        this.monto = monto;
        this.fecha = fecha;
        this.cajaEliminada = cajaEliminada;
    }

    /** @param caja la caja (la que devolvió el backend: nombre/color/ícono actuales) */
    public static MovimientoCaja de(Tipo tipo, CajaAhorroResponse caja, BigDecimal monto, Clock reloj) {
        return crear(tipo, caja, monto, reloj, false);
    }

    /** Eliminar una caja con saldo devuelve ese saldo a la cuenta: se registra como un retiro. */
    public static MovimientoCaja deEliminacion(CajaAhorroResponse caja, Clock reloj) {
        return crear(Tipo.RETIRO, caja, caja.getSaldo(), reloj, true);
    }

    private static MovimientoCaja crear(Tipo tipo, CajaAhorroResponse caja, BigDecimal monto, Clock reloj,
                                        boolean eliminada) {
        OffsetDateTime ahora = OffsetDateTime.now(reloj);
        String id = caja.getId() + "-" + ahora.toInstant().toEpochMilli() + "-" + SECUENCIA.incrementAndGet();
        return new MovimientoCaja(id, tipo, caja.getId(), caja.getNombre(), caja.getColor(), caja.getIcono(),
                monto, ahora.toString(), eliminada);
    }

    public boolean esDeposito() {
        return tipo == Tipo.DEPOSITO;
    }
}
