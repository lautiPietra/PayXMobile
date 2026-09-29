package com.example.payxmobile.servicios;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.example.payxmobile.actividad.Actividad;
import com.example.payxmobile.actividad.Actividades;
import com.example.payxmobile.model.FacturaResponse;
import com.example.payxmobile.model.LoginRequest;
import com.example.payxmobile.model.LoginResponse;
import com.example.payxmobile.model.NotificacionResponse;
import com.example.payxmobile.model.ServicioConFacturaResponse;
import com.example.payxmobile.network.ApiErrores;
import com.example.payxmobile.network.ApiService;
import com.example.payxmobile.network.RetrofitClient;
import com.example.payxmobile.notificaciones.TitulosNotificacion;
import com.example.payxmobile.utils.MontoFormatter;

import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

import retrofit2.Response;

/**
 * S10 extremo a extremo contra el backend LOCAL real, con la MISMA clase que usa la pantalla. Solo
 * corre con PAYX_E2E=1 (cuenta A: PAYX_E2E_EMAIL_A y PAYX_E2E_PASSWORD; opcional PAYX_E2E_URL).
 * Paga UNA factura real del mes (la pendiente más barata): esa plata no vuelve. 2 POST (límite 15/min).
 */
public class E2EServiciosRealTest {

    private static final String URL = env("PAYX_E2E_URL", "http://localhost:8080/");

    private static ApiService api, apiSinReintentos;

    @BeforeClass
    public static void login() throws Exception {
        assumeTrue("Definí PAYX_E2E=1 para correr contra el backend real", System.getenv("PAYX_E2E") != null);
        String email = requerida("PAYX_E2E_EMAIL_A");
        String password = requerida("PAYX_E2E_PASSWORD");
        ApiService sinToken = RetrofitClient.crear(URL, () -> null, () -> {}, false);
        Response<LoginResponse> r = sinToken.login(new LoginRequest(email, password)).execute();
        assertTrue("login -> " + r.code(), r.isSuccessful());
        String jwt = r.body().getToken();
        api = RetrofitClient.crear(URL, () -> jwt, () -> {}, false, true);
        apiSinReintentos = RetrofitClient.crear(URL, () -> jwt, () -> {}, false, false);
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
        return api.obtenerPerfil().execute().body().getSaldoPesos();
    }

    @Test
    public void s10_pagarUnServicioRealCuadraConPerfilHistorialFeedYCampana() throws Exception {
        List<ServicioConFacturaResponse> catalogo = api.listarServicios().execute().body();
        assertNotNull(catalogo);
        List<String> codigos = new ArrayList<>();
        for (ServicioConFacturaResponse s : catalogo) {
            codigos.add(s.getServicioCodigo());
            System.out.println(s.getServicioCodigo() + " " + s.getProveedor() + " $ " + MontoFormatter.fiat(s.getMonto())
                    + " vence " + s.getFechaVencimiento() + " " + s.getEstado() + (s.isVencida() ? " (vencida)" : ""));
        }
        assertEquals("S1: siempre los 6", Arrays.asList("LUZ", "GAS", "AGUA", "INTERNET", "CABLE", "TELEFONIA"), codigos);

        ServicioConFacturaResponse aPagar = null;
        for (ServicioConFacturaResponse s : catalogo) {
            if (!s.esPagada() && (aPagar == null || s.getMonto().compareTo(aPagar.getMonto()) < 0)) aPagar = s;
        }
        assumeTrue("La cuenta A ya pagó todos los servicios de este mes", aPagar != null);
        BigDecimal antes = pesos();
        assumeTrue("La cuenta A necesita al menos $ " + aPagar.getMonto(), antes.compareTo(aPagar.getMonto()) >= 0);
        long sinLeer = api.contarSinLeer().execute().body().getCantidad();

        PagoFactura pago = new PagoFactura(FacturaAPagar.de(aPagar), () -> apiSinReintentos, f -> {}, () -> {});
        pago.confirmar(antes);
        esperar("pago", () -> !pago.isEnviando());
        assertEquals("error: " + pago.getError(), PagoFactura.Paso.EXITO, pago.getPaso());
        FacturaResponse r = pago.getResultado();
        System.out.println("PAGADA -> " + r.getServicioNombre() + " " + r.getPeriodo() + " $ " + MontoFormatter.fiat(r.getMonto())
                + " fechaPago " + r.getFechaPago());
        assertEquals(aPagar.getFacturaId(), r.getId());
        assertEquals(0, aPagar.getMonto().compareTo(r.getMonto()));

        // Saldo: baja EXACTO el monto de esa factura
        assertEquals(0, antes.subtract(aPagar.getMonto()).compareTo(pesos()));

        // El catálogo ya la muestra PAGADA con su fecha
        for (ServicioConFacturaResponse s : api.listarServicios().execute().body()) {
            if (s.getFacturaId().equals(r.getId())) {
                assertTrue(s.esPagada());
                assertEquals(r.getFechaPago() != null, s.getFechaPago() != null);
            }
        }

        // Historial + feed: aparece como "Pago de ..." con key sv-{id}
        List<FacturaResponse> historial = api.historialFacturas().execute().body();
        List<String> keys = new ArrayList<>();
        for (Actividad a : Actividades.construir(Collections.emptyList(), null, null, null, null, historial, ZoneId.systemDefault())) {
            keys.add(a.key);
        }
        assertTrue(keys.contains("sv-" + r.getId()));

        // Campana
        assertTrue("sin-leer subió", api.contarSinLeer().execute().body().getCantidad() >= sinLeer + 1);
        NotificacionResponse n = api.obtenerNotificaciones().execute().body().get(0);
        System.out.println("Campana: [" + TitulosNotificacion.de(n.getPlantillaCodigo()) + "] " + n.getMensaje());
        assertEquals("SERVICIO_PAGADO", n.getPlantillaCodigo());
        assertEquals("Pago de servicio", TitulosNotificacion.de(n.getPlantillaCodigo()));
        assertTrue("el mensaje nombra el servicio", n.getMensaje().contains(aPagar.getNombre()));

        // S4 real: volver a pagarla
        Response<FacturaResponse> otra = apiSinReintentos.pagarFactura(r.getId()).execute();
        assertEquals(400, otra.code());
        assertEquals("Esta factura ya fue pagada", ApiErrores.mensaje(otra));
    }
}
