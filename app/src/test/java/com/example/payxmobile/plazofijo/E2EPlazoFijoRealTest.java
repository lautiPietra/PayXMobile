package com.example.payxmobile.plazofijo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.example.payxmobile.actividad.Actividad;
import com.example.payxmobile.actividad.Actividades;
import com.example.payxmobile.model.CrearPlazoFijoRequest;
import com.example.payxmobile.model.LoginRequest;
import com.example.payxmobile.model.LoginResponse;
import com.example.payxmobile.model.NotificacionResponse;
import com.example.payxmobile.model.PerfilResponse;
import com.example.payxmobile.model.PlazoFijoResponse;
import com.example.payxmobile.model.TasaPlazoFijo;
import com.example.payxmobile.model.TasasPlazoFijoResponse;
import com.example.payxmobile.network.ApiErrores;
import com.example.payxmobile.network.ApiService;
import com.example.payxmobile.network.RetrofitClient;
import com.example.payxmobile.notificaciones.TitulosNotificacion;
import com.example.payxmobile.utils.MontoFormatter;
import com.google.gson.Gson;

import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

import retrofit2.Response;

/**
 * P9 extremo a extremo contra el backend LOCAL real, usando el código de la app (no la UI).
 * Solo corre con PAYX_E2E=1. Cuenta A: PAYX_E2E_EMAIL_A y PAYX_E2E_PASSWORD (opcional
 * PAYX_E2E_URL). Constituye UN plazo fijo REAL por el monto mínimo al plazo más corto: esa plata
 * queda inmovilizada hasta el vencimiento y ocupa uno de los activos permitidos.
 * Dosificado: 4 POST (límite 15/min).
 */
public class E2EPlazoFijoRealTest {

    private static final String URL = env("PAYX_E2E_URL", "http://localhost:8080/");
    private static final ZoneId BA = ZoneId.of("America/Argentina/Buenos_Aires");

    private static ApiService api, apiSinReintentos;

    @BeforeClass
    public static void login() throws Exception {
        assumeTrue("Definí PAYX_E2E=1 para correr contra el backend real", System.getenv("PAYX_E2E") != null);
        String email = requerida("PAYX_E2E_EMAIL_A");
        String password = requerida("PAYX_E2E_PASSWORD");
        ApiService sinToken = RetrofitClient.crear(URL, () -> null, () -> {}, false);
        Response<LoginResponse> r = sinToken.login(new LoginRequest(email, password)).execute();
        assertTrue("login -> " + r.code(), r.isSuccessful());
        String token = r.body().getToken();
        api = RetrofitClient.crear(URL, () -> token, () -> {}, false, true);
        apiSinReintentos = RetrofitClient.crear(URL, () -> token, () -> {}, false, false);
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

    private static PerfilResponse perfil() throws Exception {
        Response<PerfilResponse> r = api.obtenerPerfil().execute();
        assertTrue("perfil -> " + r.code(), r.isSuccessful());
        return r.body();
    }

    private static TasasPlazoFijoResponse tasas() throws Exception {
        Response<TasasPlazoFijoResponse> r = api.obtenerTasasPlazoFijo().execute();
        assertTrue("tasas -> " + r.code(), r.isSuccessful());
        return r.body();
    }

    private static List<PlazoFijoResponse> plazos() throws Exception {
        Response<List<PlazoFijoResponse>> r = api.listarPlazosFijos().execute();
        assertTrue("listar -> " + r.code(), r.isSuccessful());
        return r.body();
    }

    private static String errorDe(BigDecimal monto, int dias) throws Exception {
        Response<PlazoFijoResponse> r = apiSinReintentos.crearPlazoFijo(new CrearPlazoFijoRequest(monto, dias)).execute();
        assertEquals("debe rechazar " + monto + " a " + dias + " días", 400, r.code());
        return ApiErrores.mensaje(r);
    }

    private static long sinLeer() throws Exception {
        return api.contarSinLeer().execute().body().getCantidad();
    }

    @Test
    public void p9_constituirRealCuadraConPerfilListaFeedYCampana() throws Exception {
        // Con la MISMA clase que usa la pantalla: tasas -> formulario -> confirmar
        ConstitucionPlazoFijo c = new ConstitucionPlazoFijo(() -> api, () -> apiSinReintentos, () -> {}, () -> {});
        c.cargarTasas();
        esperar("tasas", () -> c.getTasas() != null || c.getErrorTasas() != null);
        TasasPlazoFijoResponse t = c.getTasas();
        assertNotNull("tasas reales: " + c.getErrorTasas(), t);
        System.out.println("TASAS -> " + new Gson().toJson(t));

        List<PlazoFijoResponse> antesLista = plazos();
        Integer activos = PlazosFijosRepository.activos(antesLista);
        assumeTrue("La cuenta A ya tiene el máximo de activos (" + t.getMaxActivos() + ")", activos < t.getMaxActivos());
        c.setActivos(activos);
        PerfilResponse antes = perfil();
        BigDecimal monto = t.getMontoMinimo().setScale(2, RoundingMode.HALF_UP);
        assumeTrue("La cuenta A necesita al menos $ " + monto, antes.getSaldoPesos().compareTo(monto) >= 0);
        long sinLeerAntes = sinLeer();

        TasaPlazoFijo tasa = t.getTasas().get(0);
        assertTrue(c.elegirPlazo(tasa.getDias()));
        assertTrue(c.setMonto(monto.toPlainString().replace('.', ',')));
        BigDecimal interesPreview = c.getInteresPreview();
        c.continuar(antes.getSaldoPesos());
        assertEquals("error local: " + c.getError(), ConstitucionPlazoFijo.Paso.CONFIRMAR, c.getPaso());
        c.confirmar();
        esperar("POST", () -> !c.isEnviando());
        assertEquals("error: " + c.getError(), ConstitucionPlazoFijo.Paso.EXITO, c.getPaso());

        PlazoFijoResponse r = c.getResultado();
        System.out.println("201 -> " + new Gson().toJson(r));
        assertEquals(0, monto.compareTo(r.getMonto()));
        assertEquals(0, tasa.getTna().compareTo(r.getTna()));
        assertEquals(tasa.getDias(), r.getPlazoDias());
        assertEquals("ACTIVO", r.getEstado());
        assertEquals("el preview usó la misma fórmula", 0, interesPreview.compareTo(r.getInteresEstimado()));
        assertEquals(0, r.getMonto().add(r.getInteresEstimado()).compareTo(r.getMontoTotal()));
        LocalDate inicio = FormatoPlazoFijo.dia(r.getFechaInicio());
        assertEquals("fechaInicio es un día \"yyyy-MM-dd\"", 10, r.getFechaInicio().length());
        assertEquals(inicio.plusDays(r.getPlazoDias()), FormatoPlazoFijo.dia(r.getFechaVencimiento()));
        assertTrue("fechaCreacion tiene hora: " + r.getFechaCreacion(), r.getFechaCreacion().contains("T"));

        // Saldo: baja EXACTO el monto (no el total)
        PerfilResponse despues = perfil();
        System.out.println("Saldo pesos: " + MontoFormatter.fiat(antes.getSaldoPesos()) + " -> "
                + MontoFormatter.fiat(despues.getSaldoPesos()));
        assertEquals(0, antes.getSaldoPesos().subtract(monto).compareTo(despues.getSaldoPesos()));

        // Lista: el nuevo primero, con los mismos datos que el 201
        List<PlazoFijoResponse> lista = plazos();
        assertEquals(antesLista.size() + 1, lista.size());
        PlazoFijoResponse g = lista.get(0);
        System.out.println("GET[0] -> " + new Gson().toJson(g));
        assertEquals(r.getId(), g.getId());
        assertEquals(0, r.getMonto().compareTo(g.getMonto()));
        assertEquals(0, r.getTna().compareTo(g.getTna()));
        assertEquals(r.getPlazoDias(), g.getPlazoDias());
        assertEquals(0, r.getInteresEstimado().compareTo(g.getInteresEstimado()));
        assertEquals(0, r.getMontoTotal().compareTo(g.getMontoTotal()));
        assertEquals(r.getEstado(), g.getEstado());
        assertEquals(r.getFechaInicio(), g.getFechaInicio());
        assertEquals(r.getFechaVencimiento(), g.getFechaVencimiento());
        // La del 201 sale de memoria (nanos) y la del GET de la base (micros, quizás en otro offset): mismo instante
        long ms = Math.abs(java.time.Duration.between(
                com.example.payxmobile.transferencias.FormatoTransferencia.parsear(r.getFechaCreacion()),
                com.example.payxmobile.transferencias.FormatoTransferencia.parsear(g.getFechaCreacion())).toMillis());
        assertTrue("fechaCreacion: " + r.getFechaCreacion() + " vs " + g.getFechaCreacion(), ms <= 1);

        // Feed: solo el alta (todavía no venció)
        List<String> keys = new ArrayList<>();
        for (Actividad a : Actividades.construir(Collections.emptyList(), null, null, lista, BA)) keys.add(a.key);
        assertTrue(keys.contains("pf-alta-" + r.getId()));
        assertFalse(keys.contains("pf-venc-" + r.getId()));
        System.out.println("Feed: " + FilaPlazoFijo.de(r, Actividad.EventoPlazoFijo.ALTA, BA).titulo + " | "
                + FilaPlazoFijo.de(r, Actividad.EventoPlazoFijo.ALTA, BA).detalle + " | "
                + FilaPlazoFijo.de(r, Actividad.EventoPlazoFijo.ALTA, BA).monto + " | "
                + FilaPlazoFijo.de(r, Actividad.EventoPlazoFijo.ALTA, BA).fecha);

        // Campana
        assertTrue("sin-leer subió", sinLeer() >= sinLeerAntes + 1);
        NotificacionResponse n = api.obtenerNotificaciones().execute().body().get(0);
        System.out.println("Campana: [" + TitulosNotificacion.de(n.getPlantillaCodigo()) + "] " + n.getMensaje());
        assertEquals("PLAZO_FIJO_CONSTITUIDO", n.getPlantillaCodigo());
        assertEquals("Plazo fijo constituido", TitulosNotificacion.de(n.getPlantillaCodigo()));
    }

    @Test
    public void p4_textosRealesDelBackend() throws Exception {
        TasasPlazoFijoResponse t = tasas();
        int dias = t.getTasas().get(0).getDias();
        BigDecimal minimo = t.getMontoMinimo().setScale(2, RoundingMode.HALF_UP);

        String plazo = errorDe(minimo, 7);
        System.out.println("plazo inválido -> " + plazo);
        assertTrue(plazo, plazo.startsWith("El plazo elegido no es valido. Los plazos disponibles son: "));

        assertEquals("El monto minimo para constituir un plazo fijo es $ " + minimo.toPlainString(),
                errorDe(minimo.subtract(new BigDecimal("0.01")), dias));

        // El backend valida el tope ANTES que el saldo: según la cuenta sale uno u otro
        BigDecimal deMas = perfil().getSaldoPesos().add(new BigDecimal("1000")).max(minimo).setScale(2, RoundingMode.DOWN);
        String saldo = errorDe(deMas, dias);
        boolean lleno = PlazosFijosRepository.activos(plazos()) >= t.getMaxActivos();
        assertEquals(lleno ? "Ya tenes el maximo de " + t.getMaxActivos() + " plazos fijos activos"
                : "No tenes saldo suficiente para constituir este plazo fijo", saldo);
    }
}
