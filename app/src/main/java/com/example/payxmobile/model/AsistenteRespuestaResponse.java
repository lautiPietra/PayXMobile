package com.example.payxmobile.model;

import java.math.BigDecimal;

/**
 * Respuesta de POST /api/asistente/mensaje. "accionSugerida" viene null salvo que el asistente haya
 * preparado una acción en ESTA respuesta (por ahora solo tipo "TRANSFERENCIA"): la app la usa para
 * abrir el formulario ya completado, nunca para ejecutarla sola.
 */
public class AsistenteRespuestaResponse {
    private String texto;
    private AccionSugerida accionSugerida;

    public AsistenteRespuestaResponse() {}

    public AsistenteRespuestaResponse(String texto, AccionSugerida accionSugerida) {
        this.texto = texto;
        this.accionSugerida = accionSugerida;
    }

    public String getTexto() { return texto; }
    public AccionSugerida getAccionSugerida() { return accionSugerida; }

    /**
     * moneda: el código del backend ("PESOS", "USD", "BTC", ...). destinatario: CVU o alias tal cual lo
     * escribió el usuario. tipoTransferencia: "DIRECTA" o "PENDIENTE".
     */
    public static class AccionSugerida {
        public static final String TIPO_TRANSFERENCIA = "TRANSFERENCIA";

        private String tipo;
        private String destinatario;
        private String nombreResuelto;
        private BigDecimal monto;
        private String moneda;
        private String motivo;
        private String tipoTransferencia;

        public AccionSugerida() {}

        public AccionSugerida(String tipo, String destinatario, String nombreResuelto, BigDecimal monto,
                              String moneda, String motivo, String tipoTransferencia) {
            this.tipo = tipo;
            this.destinatario = destinatario;
            this.nombreResuelto = nombreResuelto;
            this.monto = monto;
            this.moneda = moneda;
            this.motivo = motivo;
            this.tipoTransferencia = tipoTransferencia;
        }

        public boolean esTransferencia() {
            return TIPO_TRANSFERENCIA.equals(tipo);
        }

        public String getTipo() { return tipo; }
        public String getDestinatario() { return destinatario; }
        public String getNombreResuelto() { return nombreResuelto; }
        public BigDecimal getMonto() { return monto; }
        public String getMoneda() { return moneda; }
        public String getMotivo() { return motivo; }
        public String getTipoTransferencia() { return tipoTransferencia; }
    }
}
