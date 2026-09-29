package com.example.payxmobile.asistente;

import com.example.payxmobile.model.AsistenteRespuestaResponse.AccionSugerida;
import com.example.payxmobile.transferencias.FormatoTransferencia;
import com.example.payxmobile.transferencias.Moneda;
import com.example.payxmobile.transferencias.MontoInput;

import java.math.BigDecimal;

/**
 * Una transferencia que preparó el asistente, traducida a lo que muestra la tarjeta del chat y a los
 * datos con los que se precarga TransferenciaActivity (EXTRA_*). Nunca se ejecuta desde acá: el
 * formulario de siempre la muestra completa y el usuario la revisa y confirma.
 */
public final class TransferenciaSugerida {

    /** Código que entiende TransferenciaActivity ("PESOS", "USD", "BTC", ...). */
    public final String moneda;
    public final String destinatario;
    /** Monto como texto para el campo ("1500,00"), o null si no vino. */
    public final String monto;
    public final String motivo;
    public final String tipo;

    private TransferenciaSugerida(String moneda, String destinatario, String monto, String motivo, String tipo) {
        this.moneda = moneda;
        this.destinatario = destinatario;
        this.monto = monto;
        this.motivo = motivo;
        this.tipo = tipo;
    }

    /** null si no es una transferencia o no se puede armar el formulario (moneda desconocida, sin destinatario). */
    public static TransferenciaSugerida de(AccionSugerida accion) {
        if (accion == null || !accion.esTransferencia()) return null;
        Moneda moneda = Moneda.desde(accion.getMoneda()); // el backend manda "PESOS"; "ARS" también vale
        String destinatario = accion.getDestinatario() != null ? accion.getDestinatario().trim() : "";
        if (moneda == null || destinatario.isEmpty()) return null;
        String motivo = accion.getMotivo() != null && !accion.getMotivo().trim().isEmpty()
                ? accion.getMotivo().trim() : null;
        return new TransferenciaSugerida(moneda.codigo(), destinatario, montoTexto(accion.getMonto(), moneda),
                motivo, accion.getTipoTransferencia());
    }

    /**
     * Como lo escribiría el usuario en el campo: 500.0 -> "500,00", 0.00500000 BTC -> "0,005". Con más
     * decimales de los que admite la moneda NO se redondea: va tal cual y el formulario lo marca inválido.
     */
    static String montoTexto(BigDecimal monto, Moneda moneda) {
        if (monto == null) return null;
        if (monto.stripTrailingZeros().scale() > moneda.decimales) return monto.toPlainString();
        return MontoInput.aTexto(monto.setScale(moneda.decimales), moneda);
    }

    /** "$ 1.500,00", "US$ 20,00" o "0,005 BTC" (como la web: el ticker atrás en cripto). */
    public static String montoFormateado(AccionSugerida accion) {
        if (accion.getMonto() == null) return "";
        return FormatoTransferencia.montoListado(accion.getMonto(), accion.getMoneda());
    }
}
