package com.example.payxmobile.servicios;

import com.example.payxmobile.model.FacturaResponse;
import com.example.payxmobile.network.ApiService;
import com.example.payxmobile.network.EnvioSinReintento;

import java.math.BigDecimal;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Pagar una factura (FacturaPagoModal.jsx): CONFIRMAR (monto exacto, no hay nada que escribir) ->
 * EXITO. El POST no tiene idempotencia: un solo pedido en vuelo, por el cliente sin reintentos; sin
 * respuesta -> INCIERTO + refresco. Pagar una vencida funciona igual (el backend no tiene penalidad).
 */
public class PagoFactura {

    public enum Paso { CONFIRMAR, EXITO, INCIERTO }

    public static final String MSG_SIN_SALDO = "Todavía no pudimos cargar tu saldo. Probá de nuevo en unos segundos.";
    public static final String MSG_INSUFICIENTE = "No tenés saldo suficiente para pagar esta factura.";
    public static final String MSG_NO_SE_PUDO = "No se pudo pagar la factura. Intentá de nuevo.";
    public static final String MSG_INCIERTO =
            "No pudimos confirmar si el pago se realizó. Revisá tus pagos y tu saldo antes de intentar de nuevo";

    public interface Observador {
        void onCambio(PagoFactura p);
    }

    private final FacturaAPagar factura;
    private final Supplier<ApiService> apiSinReintentos;
    private final Consumer<FacturaResponse> alPagar;
    private final Runnable alRefrescar;
    private Observador observador;

    private Paso paso = Paso.CONFIRMAR;
    private String error;
    private boolean enviando;
    private FacturaResponse resultado;

    /**
     * @param alPagar     con la factura PAGADA que devolvió el backend (actualizar tarjeta, historial y feed ya)
     * @param alRefrescar tras pagar, un rechazo o no saber: saldos, notificaciones, catálogo e historial
     */
    public PagoFactura(FacturaAPagar factura, Supplier<ApiService> apiSinReintentos, Consumer<FacturaResponse> alPagar,
                       Runnable alRefrescar) {
        this.factura = factura;
        this.apiSinReintentos = apiSinReintentos;
        this.alPagar = alPagar;
        this.alRefrescar = alRefrescar;
    }

    public void setObservador(Observador o) {
        observador = o;
    }

    private void notificar() {
        if (observador != null) observador.onCambio(this);
    }

    /** null = se puede pagar. Como la web: sin saldo suficiente no se deja confirmar. */
    public static String validar(BigDecimal monto, BigDecimal saldoPesos) {
        if (saldoPesos == null) return MSG_SIN_SALDO;
        if (monto != null && monto.compareTo(saldoPesos) > 0) return MSG_INSUFICIENTE;
        return null;
    }

    /** "Confirmar pago": el único lugar donde se mueve plata. */
    public void confirmar(BigDecimal saldoPesos) {
        if (enviando || paso != Paso.CONFIRMAR) return; // doble tap = 1 request
        error = validar(factura.monto, saldoPesos);
        if (error != null) {
            notificar();
            return;
        }
        enviando = true;
        notificar();
        EnvioSinReintento.enviar(apiSinReintentos.get().pagarFactura(factura.facturaId), true,
                new EnvioSinReintento.Manejador<FacturaResponse>() {
                    @Override
                    public void exito(FacturaResponse pagada) {
                        enviando = false;
                        if (!pagada.esPagada()) { // 200 pero no dice PAGADA: no sabemos qué pasó
                            paso = Paso.INCIERTO;
                            error = MSG_INCIERTO;
                        } else {
                            resultado = pagada;
                            paso = Paso.EXITO;
                            alPagar.accept(pagada);
                        }
                        alRefrescar.run();
                        notificar();
                    }

                    @Override
                    public void rechazo(String mensaje, int codigo) {
                        enviando = false;
                        // "Esta factura ya fue pagada", "No tenes saldo suficiente...", 403 "No tenes acceso..."
                        error = EnvioSinReintento.textoRechazo(mensaje, MSG_NO_SE_PUDO);
                        alRefrescar.run(); // p. ej. ya estaba pagada: la tarjeta tiene que mostrarlo
                        notificar();
                    }

                    @Override
                    public void noEnviado(String mensaje) {
                        enviando = false;
                        error = mensaje;
                        notificar();
                    }

                    @Override
                    public void incierto() {
                        enviando = false;
                        paso = Paso.INCIERTO;
                        error = MSG_INCIERTO;
                        alRefrescar.run();
                        notificar();
                    }
                });
    }

    public FacturaAPagar getFactura() { return factura; }
    public Paso getPaso() { return paso; }
    public String getError() { return error; }
    public boolean isEnviando() { return enviando; }
    public FacturaResponse getResultado() { return resultado; }

    /** Un pedido en vuelo no sobrevive a la muerte del proceso: al volver, INCIERTO. */
    public void restaurarIncierto() {
        paso = Paso.INCIERTO;
        error = MSG_INCIERTO;
        notificar();
    }
}
