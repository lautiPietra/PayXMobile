package com.example.payxmobile.transferencias;

import com.example.payxmobile.model.CrearTransferenciaRequest;
import com.example.payxmobile.model.DestinatarioResponse;
import com.example.payxmobile.model.TransferenciaResponse;
import com.example.payxmobile.network.ApiErrores;
import com.example.payxmobile.network.ApiService;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.util.function.Supplier;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Flujo de envío de la web en 3 pasos: FORMULARIO -> CONFIRMAR -> EXITO.
 * - GET /destinatario solo al tocar "Continuar" (nunca mientras se tipea).
 * - El POST es el único que mueve plata y NO tiene idempotencia en el backend: un solo pedido en
 *   vuelo, por un cliente sin reintentos, y si no llega respuesta se pasa a INCIERTO (no se
 *   reintenta solo).
 * Sin Android: la Activity lo guarda en un ViewModel para que sobreviva a la rotación.
 */
public class EnvioTransferencia {

    public enum Paso { FORMULARIO, CONFIRMAR, EXITO, INCIERTO }

    public static final String DIRECTA = "DIRECTA";
    public static final String PENDIENTE = "PENDIENTE";
    public static final String MSG_DESTINATARIO_NO_ENCONTRADO = "No pudimos encontrar ese destinatario.";
    public static final String MSG_NO_SE_PUDO = "No se pudo realizar la transferencia. Intenta de nuevo.";

    public interface Observador {
        void onCambio(EnvioTransferencia envio);
    }

    private final Supplier<ApiService> api;
    private final Supplier<ApiService> apiSinReintentos;
    private final Runnable alPosiblementeMoverPlata;
    private Observador observador;

    // Formulario
    private Moneda moneda;
    private String destinatario = "";
    private String montoTexto = "";
    private String motivo;
    private String tipo = DIRECTA;

    // Flujo
    private Paso paso = Paso.FORMULARIO;
    private DestinatarioResponse destinatarioInfo;
    private BigDecimal montoAEnviar;
    private TransferenciaResponse resultado;
    private String error;
    private boolean resolviendo;
    private boolean enviando;

    /**
     * @param alPosiblementeMoverPlata se llama tras crear (o si no se sabe si se creó): refrescar
     *                                 saldos, listado y notificaciones.
     */
    public EnvioTransferencia(Moneda moneda, Supplier<ApiService> api, Supplier<ApiService> apiSinReintentos,
                              Runnable alPosiblementeMoverPlata) {
        this.moneda = moneda;
        this.api = api;
        this.apiSinReintentos = apiSinReintentos;
        this.alPosiblementeMoverPlata = alPosiblementeMoverPlata;
    }

    public void setObservador(Observador o) {
        observador = o;
    }

    private void notificar() {
        if (observador != null) observador.onCambio(this);
    }

    // ── Formulario ────────────────────────────────────────────────────────────

    /** Al cambiar de cripto se borran el monto y el error (como la web). */
    public void setMoneda(Moneda nueva) {
        if (nueva == moneda || paso != Paso.FORMULARIO) return;
        moneda = nueva;
        montoTexto = "";
        error = null;
        notificar();
    }

    public void setDestinatario(String texto) {
        destinatario = texto != null ? texto : "";
    }

    /** Devuelve false (y no cambia nada) si el texto no se puede tipear para esta moneda. */
    public boolean setMonto(String texto) {
        String t = texto != null ? texto : "";
        if (!MontoInput.esTipeoValido(t, moneda)) return false;
        montoTexto = t;
        return true;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    public void setTipo(String tipo) {
        if (DIRECTA.equals(tipo) || PENDIENTE.equals(tipo)) this.tipo = tipo;
    }

    /** "Usar todo": el saldo exacto de ESTA moneda, sin perder decimales. */
    public void usarTodo(BigDecimal saldo) {
        if (saldo == null) return;
        montoTexto = MontoInput.aTexto(saldo, moneda);
        error = null;
        notificar();
    }

    /**
     * Datos precargados (ej. el asistente de IA). Solo completa el formulario: NUNCA avanza ni
     * envía; el usuario siempre pasa por "Continuar" y "Confirmar".
     */
    public void precargar(String destinatario, String monto, String motivo, String tipo) {
        if (paso != Paso.FORMULARIO) return;
        if (destinatario != null) setDestinatario(destinatario);
        if (monto != null) {
            String normalizado = monto.trim().replace('.', ',');
            if (!setMonto(normalizado)) {
                // Más decimales de los permitidos: se descarta en vez de redondear sin avisar
                error = ValidadorTransferencia.MONTO_INVALIDO;
            }
        }
        if (motivo != null) setMotivo(motivo);
        if (tipo != null) setTipo(tipo);
        notificar();
    }

    // ── Paso 1 -> 2 ───────────────────────────────────────────────────────────

    /** "Continuar": valida localmente y, solo si pasa, resuelve el destinatario. */
    public void continuar(BigDecimal saldoDisponible) {
        if (resolviendo || enviando || paso != Paso.FORMULARIO) return;
        error = ValidadorTransferencia.validar(destinatario, montoTexto, moneda, saldoDisponible);
        if (error != null) {
            notificar();
            return;
        }
        final BigDecimal monto = MontoInput.parsear(montoTexto);
        final String valor = destinatario.trim();
        resolviendo = true;
        notificar();
        api.get().resolverDestinatario(codificar(valor)).enqueue(new Callback<DestinatarioResponse>() {
            @Override
            public void onResponse(Call<DestinatarioResponse> call, Response<DestinatarioResponse> response) {
                resolviendo = false;
                if (response.isSuccessful() && response.body() != null) {
                    destinatarioInfo = response.body();
                    montoAEnviar = monto;
                    paso = Paso.CONFIRMAR;
                    error = null;
                } else {
                    error = mensajeError(response, MSG_DESTINATARIO_NO_ENCONTRADO);
                }
                notificar();
            }

            @Override
            public void onFailure(Call<DestinatarioResponse> call, Throwable t) {
                resolviendo = false;
                error = ApiErrores.mensajeFallo(t);
                notificar();
            }
        });
    }

    /** "Editar": vuelve al formulario CONSERVANDO los datos. */
    public void editar() {
        if (enviando || paso != Paso.CONFIRMAR) return;
        paso = Paso.FORMULARIO;
        error = null;
        notificar();
    }

    // ── Paso 2 -> 3: el ÚNICO lugar donde se crea la transferencia ─────────────

    public void confirmar() {
        if (enviando || paso != Paso.CONFIRMAR || montoAEnviar == null) return; // un solo pedido en vuelo
        enviando = true;
        error = null;
        notificar();
        CrearTransferenciaRequest body = new CrearTransferenciaRequest(
                destinatario, moneda.codigo(), montoAEnviar, motivo, tipo);
        apiSinReintentos.get().crearTransferencia(body).enqueue(new Callback<TransferenciaResponse>() {
            @Override
            public void onResponse(Call<TransferenciaResponse> call, Response<TransferenciaResponse> response) {
                enviando = false;
                if (response.isSuccessful() && response.body() != null) {
                    resultado = response.body();
                    paso = Paso.EXITO;
                    alPosiblementeMoverPlata.run();
                } else if (response.isSuccessful()) {
                    // 2xx sin body legible: se creó, pero no sabemos cómo quedó
                    paso = Paso.INCIERTO;
                    error = FalloEnvio.MSG_INCIERTO;
                    alPosiblementeMoverPlata.run();
                } else {
                    // Rechazo explícito del backend: no se creó nada, se puede corregir y reintentar
                    error = mensajeError(response, MSG_NO_SE_PUDO);
                }
                notificar();
            }

            @Override
            public void onFailure(Call<TransferenciaResponse> call, Throwable t) {
                enviando = false;
                if (FalloEnvio.seguroNoEnviado(t)) {
                    error = ApiErrores.mensajeFallo(t);
                } else {
                    // Timeout / corte después de enviar / respuesta ilegible: pudo haberse creado.
                    // No se reintenta: se avisa y se refrescan listado y saldos.
                    paso = Paso.INCIERTO;
                    error = FalloEnvio.MSG_INCIERTO;
                    alPosiblementeMoverPlata.run();
                }
                notificar();
            }
        });
    }

    // ── Estado para la UI ─────────────────────────────────────────────────────

    public Moneda getMoneda() { return moneda; }
    public String getDestinatario() { return destinatario; }
    public String getMontoTexto() { return montoTexto; }
    public String getMotivo() { return motivo; }
    public String getTipo() { return tipo; }
    public boolean esPendiente() { return PENDIENTE.equals(tipo); }
    public Paso getPaso() { return paso; }
    public DestinatarioResponse getDestinatarioInfo() { return destinatarioInfo; }
    public BigDecimal getMontoAEnviar() { return montoAEnviar; }
    public TransferenciaResponse getResultado() { return resultado; }
    public String getError() { return error; }
    public boolean isResolviendo() { return resolviendo; }
    public boolean isEnviando() { return enviando; }

    /** Monto tipeado (null si no es un número). */
    public BigDecimal getMonto() { return MontoInput.parsear(montoTexto); }

    /** Saldo luego de transferir: baja solo si es DIRECTA; en PENDIENTE queda igual. */
    public BigDecimal saldoLuego(BigDecimal saldo) {
        if (saldo == null) return null;
        BigDecimal monto = getMonto();
        if (!DIRECTA.equals(tipo) || monto == null) return saldo;
        return saldo.subtract(monto);
    }

    /** Nombre a mostrar en el éxito (como la web: contraparte del backend o el resuelto). */
    public String nombreDestino() {
        if (resultado != null && resultado.getContraparteNombre() != null) return resultado.getContraparteNombre();
        if (destinatarioInfo != null) return destinatarioInfo.getNombreCompleto();
        return destinatario.trim();
    }

    // ── Guardado ante muerte del proceso ───────────────────────────────────────

    /** Restaura un paso guardado. Una request en vuelo no sobrevive a la muerte del proceso. */
    public void restaurar(Paso paso, DestinatarioResponse info, String nombreResultado) {
        this.destinatarioInfo = info;
        if (paso == Paso.CONFIRMAR && info != null && getMonto() != null) {
            this.montoAEnviar = getMonto();
            this.paso = Paso.CONFIRMAR;
        } else if (paso == Paso.EXITO || paso == Paso.INCIERTO) {
            this.paso = paso;
            if (paso == Paso.INCIERTO) error = FalloEnvio.MSG_INCIERTO;
            if (nombreResultado != null && info == null) {
                this.destinatarioInfo = new DestinatarioResponse(nombreResultado, null, null);
            }
        }
        notificar();
    }

    static String codificar(String valor) {
        try {
            return URLEncoder.encode(valor, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String mensajeError(Response<?> response, String porDefecto) {
        String mensaje = ApiErrores.mensaje(response);
        // Sin {"error"} en el body (ej. 403 vacío por validación): el genérico de la web
        boolean generico = mensaje.equals(ApiErrores.MSG_DATOS_INVALIDOS)
                || mensaje.equals(ApiErrores.MSG_RESPUESTA_INESPERADA);
        return generico ? porDefecto : mensaje;
    }
}
