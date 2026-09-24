package com.example.payxmobile.transferencias;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.example.payxmobile.model.LoginRequest;
import com.example.payxmobile.model.NotificacionResponse;
import com.example.payxmobile.model.PerfilResponse;
import com.example.payxmobile.model.TransferenciaResponse;
import com.example.payxmobile.network.ApiErrores;
import com.example.payxmobile.network.ApiService;
import com.example.payxmobile.network.RetrofitClient;

import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.List;
import java.util.function.BooleanSupplier;

import retrofit2.Response;

/**
 * Extremo a extremo contra el backend LOCAL real, usando el código de la app (no la UI).
 * Solo corre con la variable de entorno PAYX_E2E=1 (mueve plata simulada entre las cuentas de
 * prueba A y B). Dosificado para no pasar los límites: ~8 GET /destinatario y ~6 POST.
 *
 * T24 conservación de saldos, T25 pendiente -> confirmar / cancelar, T23 vista del receptor,
 * T19 permisos reales, T27 errores textuales del backend, T13 nombres de campo reales.
 */
public class E2EBackendRealTest {

    // Credenciales y datos de las cuentas de prueba: por variables de entorno, nunca en el código.
    // PAYX_E2E_EMAIL_A, PAYX_E2E_EMAIL_B, PAYX_E2E_PASSWORD, PAYX_E2E_ALIAS_A, PAYX_E2E_ALIAS_B,
    // PAYX_E2E_USUARIO_B (@nombreUsuario de B, sin la @). Opcional: PAYX_E2E_URL.
    private static final String URL = env("PAYX_E2E_URL", "http://localhost:8080/");
    private static String EMAIL_A, EMAIL_B, PASSWORD, ALIAS_A, ALIAS_B, USUARIO_B;

    private static ApiService apiA, apiASinReintentos, apiB;

    @BeforeClass
    public static void login() throws Exception {
        assumeTrue("Definí PAYX_E2E=1 para correr contra el backend real", System.getenv("PAYX_E2E") != null);
        EMAIL_A = requerida("PAYX_E2E_EMAIL_A");
        EMAIL_B = requerida("PAYX_E2E_EMAIL_B");
        PASSWORD = requerida("PAYX_E2E_PASSWORD");
        ALIAS_A = requerida("PAYX_E2E_ALIAS_A");
        ALIAS_B = requerida("PAYX_E2E_ALIAS_B");
        USUARIO_B = requerida("PAYX_E2E_USUARIO_B");
        apiA = conToken(token(EMAIL_A), true);
        apiASinReintentos = conToken(token(EMAIL_A), false);
        apiB = conToken(token(EMAIL_B), true);
    }

    private static String env(String nombre, String porDefecto) {
        String v = System.getenv(nombre);
        return v != null && !v.isEmpty() ? v : porDefecto;
    }

    private static String requerida(String nombre) {
        String v = System.getenv(nombre);
        assumeTrue("Falta la variable de entorno " + nombre, v != null && !v.isEmpty());
        return v;
    }

    private static String token(String email) throws Exception {
        ApiService sinToken = RetrofitClient.crear(URL, () -> null, System::currentTimeMillis, () -> {}, false);
        Response<com.example.payxmobile.model.LoginResponse> r = sinToken.login(new LoginRequest(email, PASSWORD)).execute();
        assertTrue("login " + email + " -> " + r.code(), r.isSuccessful());
        return r.body().getToken();
    }

    private static ApiService conToken(String token, boolean reintentar) {
        return RetrofitClient.crear(URL, () -> token, System::currentTimeMillis, () -> {}, false, reintentar);
    }

    private static PerfilResponse perfil(ApiService api) throws Exception {
        return api.obtenerPerfil().execute().body();
    }

    private static void esperar(String que, BooleanSupplier c) throws InterruptedException {
        long fin = System.currentTimeMillis() + 15000;
        while (!c.getAsBoolean()) {
            if (System.currentTimeMillis() > fin) throw new AssertionError("Timeout: " + que);
            Thread.sleep(20);
        }
    }

    /** Recorre el flujo de la app: Continuar (resuelve) -> Confirmar (POST). */
    private static EnvioTransferencia enviar(Moneda moneda, String destinatario, String monto, String tipo,
                                             BigDecimal saldoParaValidar) throws Exception {
        EnvioTransferencia e = new EnvioTransferencia(moneda, () -> apiA, () -> apiASinReintentos, () -> {});
        e.setDestinatario(destinatario);
        assertTrue(e.setMonto(monto));
        e.setTipo(tipo);
        e.continuar(saldoParaValidar);
        esperar("resolver", () -> !e.isResolviendo());
        if (e.getPaso() != EnvioTransferencia.Paso.CONFIRMAR) return e;
        e.confirmar();
        esperar("confirmar", () -> !e.isEnviando());
        return e;
    }

    @Test
    public void t24_directasEnPesosDolaresYCriptoConservanLaSuma() throws Exception {
        Object[][] casos = {
                {Moneda.PESOS, "1,25", new BigDecimal("1.25")},
                {Moneda.USD, "0,50", new BigDecimal("0.50")},
                {Moneda.BTC, "0,0000001", new BigDecimal("0.0000001")},
        };
        for (Object[] c : casos) {
            Moneda m = (Moneda) c[0];
            BigDecimal monto = (BigDecimal) c[2];
            BigDecimal a0 = m.saldoEn(perfil(apiA)), b0 = m.saldoEn(perfil(apiB));

            EnvioTransferencia e = enviar(m, ALIAS_B, (String) c[1], EnvioTransferencia.DIRECTA, a0);
            assertEquals(m + ": " + e.getError(), EnvioTransferencia.Paso.EXITO, e.getPaso());
            assertEquals("COMPLETADA", e.getResultado().getEstado());
            assertFalse("muestra el nombre del destinatario", e.nombreDestino().isEmpty());

            BigDecimal a1 = m.saldoEn(perfil(apiA)), b1 = m.saldoEn(perfil(apiB));
            System.out.println("T24 " + m + " A: " + a0.toPlainString() + " -> " + a1.toPlainString()
                    + " | B: " + b0.toPlainString() + " -> " + b1.toPlainString());
            assertEquals(m + " A baja exactamente el monto", 0, a0.subtract(monto).compareTo(a1));
            assertEquals(m + " B sube exactamente el monto", 0, b0.add(monto).compareTo(b1));
            assertEquals(m + " conservación A+B", 0, a0.add(b0).compareTo(a1.add(b1)));
        }
        // Notificaciones que crea el backend (2.7)
        List<NotificacionResponse> nb = apiB.obtenerNotificaciones().execute().body();
        List<NotificacionResponse> na = apiA.obtenerNotificaciones().execute().body();
        assertTrue("B recibió TRANSFERENCIA_RECIBIDA", nb.stream().anyMatch(n -> "TRANSFERENCIA_RECIBIDA".equals(n.getPlantillaCodigo())));
        assertTrue("A recibió TRANSFERENCIA_ENVIADA", na.stream().anyMatch(n -> "TRANSFERENCIA_ENVIADA".equals(n.getPlantillaCodigo())));
    }

    @Test
    public void t25_t23_t19_pendienteConfirmarYCancelar() throws Exception {
        BigDecimal a0 = perfil(apiA).getSaldoPesos(), b0 = perfil(apiB).getSaldoPesos();

        // Pendiente: no mueve saldos
        EnvioTransferencia p = enviar(Moneda.PESOS, ALIAS_B, "2", EnvioTransferencia.PENDIENTE, a0);
        assertEquals(p.getError(), EnvioTransferencia.Paso.EXITO, p.getPaso());
        TransferenciaResponse creada = p.getResultado();
        assertEquals("PENDIENTE", creada.getEstado());
        assertEquals(0, a0.compareTo(perfil(apiA).getSaldoPesos()));
        assertEquals(0, b0.compareTo(perfil(apiB).getSaldoPesos()));

        // T23: B la ve en su listado, recibida, pendiente, "+", sin acciones
        TransferenciaResponse vistaB = buscar(apiB.listarTransferencias().execute().body(), creada.getId());
        assertNotNull("B la ve desde que se crea", vistaB);
        assertEquals("RECIBIDA", vistaB.getDireccion());
        assertFalse(vistaB.isEsEmisor());
        FilaTransferencia filaB = FilaTransferencia.de(vistaB, ZoneId.systemDefault());
        assertEquals("+$ 2,00", filaB.monto);
        assertTrue(filaB.pendiente);
        DetalleTransferencia detalleB = new DetalleTransferencia(vistaB, () -> apiB, t -> {}, () -> {});
        assertFalse(detalleB.puedeConfirmarOCancelar());
        assertFalse(detalleB.puedeCambiarConcepto());

        // T19 real: si B igual lo intenta, el backend responde 403 CON body
        Response<TransferenciaResponse> r403 = apiB.confirmarTransferencia(creada.getId()).execute();
        assertEquals(403, r403.code());
        assertEquals("Solo quien envio la transferencia puede hacer esta accion", ApiErrores.mensaje(r403));

        // A la confirma desde el detalle -> recién ahí se mueven los saldos
        DetalleTransferencia detalleA = new DetalleTransferencia(
                buscar(apiA.listarTransferencias().execute().body(), creada.getId()), () -> apiA, t -> {}, () -> {});
        assertTrue(detalleA.puedeConfirmarOCancelar());
        detalleA.confirmar();
        esperar("confirmada", () -> !detalleA.isProcesando());
        assertEquals(detalleA.getError(), "COMPLETADA", detalleA.getTransferencia().getEstado());
        assertNotNull(detalleA.getTransferencia().getFechaConfirmacion());
        BigDecimal a1 = perfil(apiA).getSaldoPesos(), b1 = perfil(apiB).getSaldoPesos();
        assertEquals(0, a0.subtract(new BigDecimal("2")).compareTo(a1));
        assertEquals(0, b0.add(new BigDecimal("2")).compareTo(b1));

        // Otra pendiente -> cancelar -> saldos sin cambios
        EnvioTransferencia p2 = enviar(Moneda.PESOS, "@" + USUARIO_B, "3", EnvioTransferencia.PENDIENTE, a1);
        assertEquals(p2.getError(), EnvioTransferencia.Paso.EXITO, p2.getPaso());
        DetalleTransferencia d2 = new DetalleTransferencia(p2.getResultado(), () -> apiA, t -> {}, () -> {});
        d2.cancelar();
        esperar("cancelada", () -> !d2.isProcesando());
        assertTrue(d2.getError(), d2.getTransferencia().esCancelada());
        assertEquals("$ 3,00", FilaTransferencia.de(d2.getTransferencia(), ZoneId.systemDefault()).monto);
        assertEquals(0, a1.compareTo(perfil(apiA).getSaldoPesos()));
        assertEquals(0, b1.compareTo(perfil(apiB).getSaldoPesos()));
        // Cancelar de nuevo: 400 textual
        DetalleTransferencia d3 = new DetalleTransferencia(p2.getResultado(), () -> apiA, t -> {}, () -> {});
        d3.cancelar();
        esperar("400", () -> !d3.isProcesando());
        assertEquals("Solo se pueden cancelar transferencias pendientes", d3.getError());

        // Concepto: el emisor lo cambia
        d2.guardarConcepto("Prueba app Android");
        esperar("concepto", () -> !d2.isGuardando());
        assertEquals("Prueba app Android", d2.getTransferencia().getConcepto());
        System.out.println("T25 pesos A: " + a0 + " -> " + a1 + " | B: " + b0 + " -> " + b1);
    }

    @Test
    public void t27_erroresDelBackendLleganTextuales() throws Exception {
        BigDecimal saldo = perfil(apiA).getSaldoPesos();
        EnvioTransferencia inexistente = enviar(Moneda.PESOS, "no.existe.payx.zz9", "1", EnvioTransferencia.DIRECTA, saldo);
        assertEquals("No encontramos ninguna cuenta con ese alias", inexistente.getError());

        EnvioTransferencia yoMismo = enviar(Moneda.PESOS, ALIAS_A, "1", EnvioTransferencia.DIRECTA, saldo);
        assertEquals("No podes transferirte a vos mismo", yoMismo.getError());

        // Saldo insuficiente que solo detecta el backend: se pasa un saldo local "desactualizado"
        EnvioTransferencia sinSaldo = enviar(Moneda.PESOS, ALIAS_B, "99999999", EnvioTransferencia.DIRECTA,
                new BigDecimal("100000000"));
        assertEquals("No tenes saldo suficiente para esta transferencia", sinSaldo.getError());
        assertEquals("conserva los datos para corregir", EnvioTransferencia.Paso.CONFIRMAR, sinSaldo.getPaso());
    }

    @Test
    public void t13_nombresDeCampoReales() throws Exception {
        // B ya tenía transferencias antes de estas pruebas (A arrancó con la lista vacía)
        List<TransferenciaResponse> lista = apiB.listarTransferencias().execute().body();
        assertNotNull(lista);
        assertFalse(lista.isEmpty());
        TransferenciaResponse t = lista.get(0);
        assertNotNull(t.getId());
        assertNotNull(t.getMoneda());
        assertNotNull(t.getEstado());
        assertNotNull(t.getDireccion());
        assertNotNull(FormatoTransferencia.parsear(t.getFecha()));
        assertNotNull(t.getContraparteAlias());
    }

    private static TransferenciaResponse buscar(List<TransferenciaResponse> lista, String id) {
        for (TransferenciaResponse t : lista) if (t.getId().equals(id)) return t;
        return null;
    }
}
