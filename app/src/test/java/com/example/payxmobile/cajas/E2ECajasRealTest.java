package com.example.payxmobile.cajas;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.example.payxmobile.model.CajaAhorroRequest;
import com.example.payxmobile.model.CajaAhorroResponse;
import com.example.payxmobile.model.LoginRequest;
import com.example.payxmobile.model.LoginResponse;
import com.example.payxmobile.model.NotificacionResponse;
import com.example.payxmobile.model.PerfilResponse;
import com.example.payxmobile.network.ApiErrores;
import com.example.payxmobile.network.ApiService;
import com.example.payxmobile.network.RetrofitClient;
import com.example.payxmobile.notificaciones.TitulosNotificacion;
import com.example.payxmobile.utils.MontoFormatter;
import com.google.gson.Gson;

import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.function.BooleanSupplier;

import retrofit2.Response;

/**
 * K11 extremo a extremo contra el backend LOCAL real, con las MISMAS clases que usan las pantallas.
 * Solo corre con PAYX_E2E=1 (cuenta A: PAYX_E2E_EMAIL_A y PAYX_E2E_PASSWORD; opcional PAYX_E2E_URL).
 * Necesita $ 1.100 en pesos y un lugar libre de caja. Neto: el saldo principal termina igual
 * (-600 -500 +300 +800 al eliminar). Dosificado: 5 POST de cajas (límite 15/min).
 */
public class E2ECajasRealTest {

    private static final String URL = env("PAYX_E2E_URL", "http://localhost:8080/");

    private static ApiService api, apiSinReintentos;

    @BeforeClass
    public static void login() throws Exception {
        assumeTrue("Definí PAYX_E2E=1 para correr contra el backend real", System.getenv("PAYX_E2E") != null);
        String email = requerida("PAYX_E2E_EMAIL_A");
        String password = requerida("PAYX_E2E_PASSWORD");
        ApiService sinToken = RetrofitClient.crear(URL, () -> null, System::currentTimeMillis, () -> {}, false);
        Response<LoginResponse> r = sinToken.login(new LoginRequest(email, password)).execute();
        assertTrue("login -> " + r.code(), r.isSuccessful());
        String token = r.body().getToken();
        api = RetrofitClient.crear(URL, () -> token, System::currentTimeMillis, () -> {}, false, true);
        apiSinReintentos = RetrofitClient.crear(URL, () -> token, System::currentTimeMillis, () -> {}, false, false);
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

    private static void esperar(String que, BooleanSupplier c) throws InterruptedException {
        long fin = System.currentTimeMillis() + 15000;
        while (!c.getAsBoolean()) {
            if (System.currentTimeMillis() > fin) throw new AssertionError("Timeout: " + que);
            Thread.sleep(20);
        }
    }

    private static BigDecimal pesos() throws Exception {
        Response<PerfilResponse> r = api.obtenerPerfil().execute();
        assertTrue("perfil -> " + r.code(), r.isSuccessful());
        return r.body().getSaldoPesos();
    }

    private static CajaAhorroResponse delGet(String id) throws Exception {
        List<CajaAhorroResponse> lista = api.listarCajasAhorro().execute().body();
        return CajasAhorroRepository.buscar(lista, id);
    }

    private static List<NotificacionResponse> notificaciones() throws Exception {
        return api.obtenerNotificaciones().execute().body();
    }

    private static OperacionMontoCaja mover(MovimientoCaja.Tipo tipo, CajaAhorroResponse caja, String monto,
                                            MovimientosCajaSesion sesion) throws Exception {
        OperacionMontoCaja op = new OperacionMontoCaja(tipo, caja, () -> apiSinReintentos, sesion,
                Clock.systemDefaultZone(), c -> {}, () -> {});
        assertTrue(op.setMonto(monto));
        op.confirmar(pesos());
        esperar(tipo.name(), () -> !op.isEnviando());
        assertEquals("error: " + op.getError(), OperacionMontoCaja.Paso.EXITO, op.getPaso());
        CajaAhorroResponse g = delGet(caja.getId());
        assertEquals("respuesta == GET directo", 0, op.getResultado().getSaldo().compareTo(g.getSaldo()));
        return op;
    }

    @Test
    public void k11_cicloCompletoContraElBackendReal() throws Exception {
        Integer max = api.obtenerLimiteCajas().execute().body().getMaxPorUsuario();
        List<CajaAhorroResponse> antes = api.listarCajasAhorro().execute().body();
        System.out.println("LIMITE -> " + max + " | cajas actuales: " + antes.size());
        assumeTrue("La cuenta A ya tiene el máximo de cajas (" + max + ")", antes.size() < max);
        BigDecimal inicial = pesos();
        assumeTrue("La cuenta A necesita al menos $ 1.100", inicial.compareTo(new BigDecimal("1100")) >= 0);
        MovimientosCajaSesion sesion = new MovimientosCajaSesion();

        // ── Crear con meta ──
        FormularioCaja crear = new FormularioCaja(null, () -> api, () -> apiSinReintentos, c -> {}, () -> {});
        crear.cargarLimite();
        esperar("límite", () -> crear.getMaxCajas() != null);
        crear.setCantidadCajas(antes.size());
        crear.setNombre("E2E Meta");
        assertTrue(crear.elegirColor("#16a34a"));
        assertTrue(crear.elegirIcono("target"));
        assertTrue(crear.setMeta("1000"));
        crear.guardar();
        esperar("crear", () -> !crear.isEnviando());
        assertEquals("error: " + crear.getError(), FormularioCaja.Paso.EXITO, crear.getPaso());
        CajaAhorroResponse caja = crear.getResultado();
        System.out.println("CREADA -> " + new Gson().toJson(caja));
        CajaAhorroResponse g = delGet(caja.getId());
        assertNotNull("aparece en GET /api/cajas-ahorro", g);
        assertEquals(0, new BigDecimal("1000").compareTo(g.getMontoObjetivo()));
        assertEquals(0, BigDecimal.ZERO.compareTo(g.getSaldo()));

        try {
            // ── Depositar 600 (sin llegar a la meta) ──
            OperacionMontoCaja d1 = mover(MovimientoCaja.Tipo.DEPOSITO, caja, "600", sesion);
            assertEquals("principal baja EXACTO", 0, inicial.subtract(new BigDecimal("600")).compareTo(pesos()));
            assertEquals("CAJA_AHORRO_DEPOSITO", notificaciones().get(0).getPlantillaCodigo());

            // ── Depositar 500: llega a la meta -> DOS notificaciones ──
            OperacionMontoCaja d2 = mover(MovimientoCaja.Tipo.DEPOSITO, d1.getResultado(), "500", sesion);
            assertTrue(d2.isMetaAlcanzada());
            assertEquals(0, new BigDecimal("1100").compareTo(d2.getResultado().getSaldo()));
            List<NotificacionResponse> n = notificaciones();
            for (int i = 0; i < 3; i++) {
                System.out.println("Campana[" + i + "]: [" + TitulosNotificacion.de(n.get(i).getPlantillaCodigo()) + "] "
                        + n.get(i).getMensaje());
            }
            assertEquals("las dos del último depósito", new HashSet<>(Arrays.asList("CAJA_AHORRO_DEPOSITO", "CAJA_AHORRO_META_ALCANZADA")),
                    new HashSet<>(Arrays.asList(n.get(0).getPlantillaCodigo(), n.get(1).getPlantillaCodigo())));

            // ── Retirar 300 ──
            OperacionMontoCaja r = mover(MovimientoCaja.Tipo.RETIRO, d2.getResultado(), "300", sesion);
            assertEquals(0, new BigDecimal("800").compareTo(r.getResultado().getSaldo()));
            assertEquals(0, inicial.subtract(new BigDecimal("800")).compareTo(pesos()));
            assertEquals("CAJA_AHORRO_RETIRO", notificaciones().get(0).getPlantillaCodigo());
            assertEquals("feed de la sesión: 2 depósitos y 1 retiro", 3, sesion.getLista().size());

            // ── Editar nombre/color y quitar la meta (null explícito) ──
            FormularioCaja editar = new FormularioCaja(r.getResultado(), () -> api, () -> apiSinReintentos, c -> {}, () -> {});
            editar.setNombre("E2E Editada");
            assertTrue(editar.elegirColor("#dc2626"));
            assertTrue(editar.setMeta(""));
            editar.guardar();
            esperar("editar", () -> !editar.isEnviando());
            assertEquals("error: " + editar.getError(), FormularioCaja.Paso.EXITO, editar.getPaso());
            CajaAhorroResponse ge = delGet(caja.getId());
            assertEquals("E2E Editada", ge.getNombre());
            assertEquals("#dc2626", ge.getColor());
            assertNull("la meta se quitó de verdad", ge.getMontoObjetivo());
            assertEquals("editar no toca el saldo", 0, new BigDecimal("800").compareTo(ge.getSaldo()));

            // ── Textos reales del backend ──
            Response<CajaAhorroResponse> color = apiSinReintentos.crearCajaAhorro(
                    new CajaAhorroRequest("x", "#123456", "target", null)).execute();
            System.out.println("color inválido -> " + color.code() + " " + ApiErrores.mensaje(color));
            assertEquals(400, color.code());
            Response<CajaAhorroResponse> vacio = apiSinReintentos.crearCajaAhorro(
                    new CajaAhorroRequest("", "#16a34a", "target", null)).execute();
            String cuerpo = vacio.errorBody() != null ? vacio.errorBody().string() : "";
            System.out.println("nombre vacío (@Valid) -> " + vacio.code() + " body='" + cuerpo + "'");
            assertTrue("rechazado", !vacio.isSuccessful());
            Response<CajaAhorroResponse> meta0 = apiSinReintentos.editarCajaAhorro(caja.getId(),
                    new CajaAhorroRequest("x", "#16a34a", "target", BigDecimal.ZERO)).execute();
            String cuerpoMeta = meta0.errorBody() != null ? meta0.errorBody().string() : "";
            System.out.println("meta 0 (@Valid) -> " + meta0.code() + " body='" + cuerpoMeta + "'");
            assertTrue(!meta0.isSuccessful());
        } finally {
            // ── Eliminar: el saldo vuelve a la cuenta ──
            BigDecimal antesDeEliminar = pesos();
            CajaAhorroResponse actual = delGet(caja.getId());
            EliminacionCaja e = new EliminacionCaja(() -> apiSinReintentos, sesion, Clock.systemDefaultZone(), c -> {}, () -> {});
            e.eliminar(actual);
            esperar("eliminar", () -> !e.isEnviando());
            assertEquals("error: " + e.getMensaje(), EliminacionCaja.Estado.ELIMINADA, e.getEstado());
            assertNull("ya no está en el GET", delGet(caja.getId()));
            BigDecimal despues = pesos();
            System.out.println("Eliminar con $ " + MontoFormatter.fiat(actual.getSaldo()) + " adentro: principal "
                    + MontoFormatter.fiat(antesDeEliminar) + " -> " + MontoFormatter.fiat(despues));
            assertEquals("el saldo de la caja VOLVIÓ a la cuenta", 0, antesDeEliminar.add(actual.getSaldo()).compareTo(despues));
        }
        assertEquals("neto: el principal termina igual", 0, inicial.compareTo(pesos()));
    }
}
