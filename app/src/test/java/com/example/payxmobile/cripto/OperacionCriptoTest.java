package com.example.payxmobile.cripto;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.payxmobile.JwtFalso;
import com.example.payxmobile.model.CotizacionCripto;
import com.example.payxmobile.model.PerfilResponse;
import com.example.payxmobile.network.ApiErrores;
import com.example.payxmobile.network.ApiService;
import com.example.payxmobile.network.RetrofitClient;
import com.example.payxmobile.transferencias.Moneda;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;

/** Compra/venta de cripto: reglas puras (C2, C6, C7) y contrato HTTP con MockWebServer (C3, C4, C5, C8). */
public class OperacionCriptoTest {

    /** Respuesta real de GET /api/cotizacion/cripto (orden del backend). */
    static final String COTIZACIONES = "["
            + "{\"simbolo\":\"BTC\",\"nombre\":\"Bitcoin\",\"precio\":128127714,\"desactualizada\":false},"
            + "{\"simbolo\":\"ETH\",\"nombre\":\"Ethereum\",\"precio\":4057107,\"desactualizada\":false},"
            + "{\"simbolo\":\"SOL\",\"nombre\":\"Solana\",\"precio\":174485,\"desactualizada\":false},"
            + "{\"simbolo\":\"USDT\",\"nombre\":\"Tether\",\"precio\":1515.86,\"desactualizada\":false},"
            + "{\"simbolo\":\"BNB\",\"nombre\":\"BNB\",\"precio\":1164353,\"desactualizada\":true},"
            + "{\"simbolo\":\"XRP\",\"nombre\":\"XRP\",\"precio\":2322.37,\"desactualizada\":false}]";

    /** Un saldo DISTINTO por cripto: si se cruzan, los tests lo detectan. */
    static final String PERFIL = "{\"saldoPesos\":100000.00,\"saldoUsd\":10.00,\"saldoBtc\":0.12345678,"
            + "\"saldoEth\":1.00000000,\"saldoSolana\":2.50000000,\"saldoUsdt\":30.00000000,"
            + "\"saldoBnb\":0.00000001,\"saldoXrp\":400.10000000}";

    static Map<String, CotizacionCripto> cotizaciones() {
        List<CotizacionCripto> lista = new Gson().fromJson(COTIZACIONES, new TypeToken<List<CotizacionCripto>>() {}.getType());
        Map<String, CotizacionCripto> m = new LinkedHashMap<>();
        for (CotizacionCripto c : lista) m.put(c.getSimbolo(), c);
        return m;
    }

    static PerfilResponse perfil() {
        return new Gson().fromJson(PERFIL, PerfilResponse.class);
    }

    static String operacion(String tipo, String simbolo, String cripto, String pesos, String cotizacion) {
        return "{\"id\":\"9f1c2d3e-4a5b-6c7d-8e9f-0a1b2c3d4e5f\",\"tipo\":\"" + tipo + "\",\"simbolo\":\"" + simbolo
                + "\",\"montoCripto\":" + cripto + ",\"montoPesos\":" + pesos + ",\"cotizacion\":" + cotizacion
                + ",\"fecha\":\"2026-09-27T13:15:30.123456-03:00\"}";
    }

    private static final Moneda[] CRIPTOS = {Moneda.BTC, Moneda.ETH, Moneda.SOL, Moneda.USDT, Moneda.BNB, Moneda.XRP};

    private MockWebServer server;
    private ApiService apiSinReintentos;
    private final AtomicInteger refrescos = new AtomicInteger();

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        String token = JwtFalso.conExp(System.currentTimeMillis() / 1000 + 7200);
        apiSinReintentos = RetrofitClient.crear(server.url("/").toString(), () -> token, System::currentTimeMillis,
                () -> {}, false, false);
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
    }

    private OperacionCripto nueva(OperacionCripto.Tipo tipo) {
        return nueva(tipo, apiSinReintentos);
    }

    private OperacionCripto nueva(OperacionCripto.Tipo tipo, ApiService api) {
        OperacionCripto op = new OperacionCripto(tipo, () -> api, refrescos::incrementAndGet);
        op.setCotizaciones(cotizaciones(), false);
        return op;
    }

    private static void esperar(String que, BooleanSupplier c) throws InterruptedException {
        long fin = System.currentTimeMillis() + 5000;
        while (!c.getAsBoolean()) {
            if (System.currentTimeMillis() > fin) throw new AssertionError("Timeout: " + que);
            Thread.sleep(10);
        }
    }

    private static BigDecimal saldo(OperacionCripto op) {
        return OperacionCripto.saldoDisponible(op.getTipo(), op.getCripto(), perfil());
    }

    /** continuar + confirmar y espera a que termine el POST. */
    private void operar(OperacionCripto op) throws InterruptedException {
        op.continuar(saldo(op));
        assertEquals("error local: " + op.getError(), OperacionCripto.Paso.CONFIRMAR, op.getPaso());
        op.confirmar();
        esperar("fin del POST", () -> !op.isEnviando());
    }

    // ── C1: precios (de SaldosRepository) y 503 ──────────────────────────────

    @Test
    public void c1_precioDeCadaCriptoYDesactualizada() {
        OperacionCripto op = nueva(OperacionCripto.Tipo.COMPRA);
        assertEquals(new BigDecimal("128127714"), op.getPrecio());
        assertFalse(op.isPrecioDesactualizado());
        op.setCripto(Moneda.BNB);
        assertTrue("BNB viene desactualizada: se muestra con leyenda", op.isPrecioDesactualizado());
        assertEquals("se MUESTRA igual", new BigDecimal("1164353"), op.getPrecio());
        op.setCripto(Moneda.XRP);
        assertEquals(new BigDecimal("2322.37"), op.getPrecio());
    }

    @Test
    public void c1_503OcultaElPreviewYNoDejaOperar() {
        OperacionCripto op = nueva(OperacionCripto.Tipo.COMPRA);
        op.setMonto("1000");
        assertNotNull(op.getPreview());
        op.setCotizaciones(cotizaciones(), true); // el backend respondió 503 (el ticker guarda las viejas)
        assertNull(op.getPrecio());
        assertNull("sin preview, nunca un 0", op.getPreview());
        op.continuar(saldo(op));
        assertEquals(OperacionCripto.MSG_SIN_COTIZACION, op.getError());
        assertEquals(OperacionCripto.Paso.FORMULARIO, op.getPaso());
        assertEquals(0, server.getRequestCount());
    }

    // ── Preview con el redondeo del backend (DOWN) ───────────────────────────

    @Test
    public void previewRedondeaHaciaAbajoComoElBackend() {
        // 2 / 3 = 0,666666666... -> 0,66666666 (HALF_UP daría 0,66666667)
        assertEquals(new BigDecimal("0.66666666"),
                OperacionCripto.preview(OperacionCripto.Tipo.COMPRA, new BigDecimal("2"), new BigDecimal("3")));
        // 0,12345678 × 2322,37 = 286,71... -> 286,71 hacia abajo
        assertEquals(new BigDecimal("286.71"),
                OperacionCripto.preview(OperacionCripto.Tipo.VENTA, new BigDecimal("0.12345678"), new BigDecimal("2322.37")));
        // 0,019 × 1 = 0,019 -> 0,01 (HALF_UP daría 0,02)
        assertEquals(new BigDecimal("0.01"),
                OperacionCripto.preview(OperacionCripto.Tipo.VENTA, new BigDecimal("0.019"), BigDecimal.ONE));
        assertNull(OperacionCripto.preview(OperacionCripto.Tipo.COMPRA, BigDecimal.ONE, null));
    }

    // ── C2: las 6 criptos, cada una con SU símbolo y SU saldo ────────────────

    @Test
    public void c2_cadaCriptoUsaSuSaldoSinCruzarse() {
        PerfilResponse p = perfil();
        String[] esperados = {"0.12345678", "1.00000000", "2.50000000", "30.00000000", "0.00000001", "400.10000000"};
        for (int i = 0; i < CRIPTOS.length; i++) {
            assertEquals(CRIPTOS[i] + " vende contra SU saldo", new BigDecimal(esperados[i]),
                    OperacionCripto.saldoDisponible(OperacionCripto.Tipo.VENTA, CRIPTOS[i], p));
            assertEquals("comprar siempre es contra pesos", new BigDecimal("100000.00"),
                    OperacionCripto.saldoDisponible(OperacionCripto.Tipo.COMPRA, CRIPTOS[i], p));
        }
    }

    @Test
    public void c2_ventaDeCadaCriptoMandaSuSimboloYValidaContraSuSaldo() throws Exception {
        for (Moneda m : CRIPTOS) {
            OperacionCripto op = nueva(OperacionCripto.Tipo.VENTA);
            op.setCripto(m);
            // Un poco más que el saldo de ESA cripto: rechazo local, sin request
            BigDecimal mas = saldo(op).add(new BigDecimal("0.00000001"));
            op.setMonto(mas.toPlainString().replace('.', ','));
            op.continuar(saldo(op));
            assertEquals(m + ": saldo propio", OperacionCripto.msgInsuficienteVenta(m), op.getError());

            // Justo el saldo: pasa y manda SU símbolo
            op.usarTodo(saldo(op));
            server.enqueue(new MockResponse().setResponseCode(201)
                    .setBody(operacion("VENTA", m.codigo(), saldo(op).toPlainString(), "1.00", "1")));
            operar(op);
            RecordedRequest r = server.takeRequest(1, TimeUnit.SECONDS);
            JsonObject body = JsonParser.parseString(r.getBody().readUtf8()).getAsJsonObject();
            assertEquals("/api/cripto", r.getPath());
            assertEquals("VENTA", body.get("tipo").getAsString());
            assertEquals(m.codigo(), body.get("simbolo").getAsString());
            assertEquals(0, saldo(op).compareTo(new BigDecimal(body.get("monto").toString())));
            assertEquals(3, body.size());
        }
        assertEquals("una sola request por cripto", 6, server.getRequestCount());
    }

    @Test
    public void c2_compraDeCadaCriptoMandaPesosYSuSimbolo() throws Exception {
        for (Moneda m : CRIPTOS) {
            OperacionCripto op = nueva(OperacionCripto.Tipo.COMPRA);
            op.setCripto(m);
            op.setMonto("1500,50");
            server.enqueue(new MockResponse().setResponseCode(201)
                    .setBody(operacion("COMPRA", m.codigo(), "0.1", "1500.50", "15005")));
            operar(op);
            JsonObject body = JsonParser.parseString(server.takeRequest(1, TimeUnit.SECONDS).getBody().readUtf8()).getAsJsonObject();
            assertEquals("COMPRA", body.get("tipo").getAsString());
            assertEquals(m.codigo(), body.get("simbolo").getAsString());
            assertEquals("1500.5", body.get("monto").toString());
        }
    }

    @Test
    public void cambiarDeCriptoLimpiaElMonto() {
        OperacionCripto op = nueva(OperacionCripto.Tipo.VENTA);
        op.setMonto("0,5");
        op.continuar(null); // error local
        op.setCripto(Moneda.ETH);
        assertEquals("", op.getMontoTexto());
        assertNull(op.getError());
        assertEquals(Moneda.ETH, op.getCripto());
        op.setCripto(Moneda.USD); // no es cripto: se ignora
        assertEquals(Moneda.ETH, op.getCripto());
    }

    // ── C3: resultado real del backend ───────────────────────────────────────

    @Test
    public void c3_compraMuestraLoQueDevuelveElBackend() throws Exception {
        OperacionCripto op = nueva(OperacionCripto.Tipo.COMPRA);
        op.setMonto("5000");
        assertEquals("preview con el precio mostrado", new BigDecimal("0.00003902"), op.getPreview());
        server.enqueue(new MockResponse().setResponseCode(201)
                .setBody(operacion("COMPRA", "BTC", "0.00003900", "5000.00", "128205128.20")));
        operar(op);
        assertEquals(OperacionCripto.Paso.EXITO, op.getPaso());
        assertEquals("lo real, no el preview", new BigDecimal("0.00003900"), op.getResultado().getMontoCripto());
        assertEquals(new BigDecimal("5000.00"), op.getResultado().getMontoPesos());
        assertEquals(new BigDecimal("128205128.20"), op.getResultado().getCotizacion());
        assertEquals("refresca saldos, feed y notificaciones", 1, refrescos.get());
    }

    @Test
    public void c3_ventaMuestraLoQueDevuelveElBackend() throws Exception {
        OperacionCripto op = nueva(OperacionCripto.Tipo.VENTA);
        op.setCripto(Moneda.XRP);
        op.setMonto("400,1");
        server.enqueue(new MockResponse().setResponseCode(201)
                .setBody(operacion("VENTA", "XRP", "400.10000000", "929180.23", "2322.37")));
        operar(op);
        assertEquals(new BigDecimal("400.10000000"), op.getResultado().getMontoCripto());
        assertEquals(new BigDecimal("929180.23"), op.getResultado().getMontoPesos());
        assertEquals(1, refrescos.get());
    }

    // ── C4: el 400 de "cotización desactualizada para operar" ────────────────

    @Test
    public void c4_precioVisibleDesactualizadoPeroElBackendRechazaOperar() throws Exception {
        OperacionCripto op = nueva(OperacionCripto.Tipo.COMPRA);
        op.setCripto(Moneda.BNB); // el ticker la muestra con desactualizada:true
        assertNotNull("el precio se sigue mostrando", op.getPrecio());
        assertTrue(op.isPrecioDesactualizado());
        op.setMonto("1000");
        String msg = "La cotizacion de BNB esta desactualizada, no se puede operar en este momento. Intenta de nuevo en unos segundos";
        server.enqueue(new MockResponse().setResponseCode(400).setBody("{\"error\":\"" + msg + "\"}"));
        operar(op);
        assertEquals("texto exacto del backend", msg, op.getError());
        assertEquals("se puede reintentar o modificar", OperacionCripto.Paso.CONFIRMAR, op.getPaso());
        assertNotNull("el ticker sigue mostrando el precio", op.getPrecio());
        assertEquals("no se movió plata", 0, refrescos.get());
    }

    // ── C5: los demás 400 con su texto ───────────────────────────────────────

    @Test
    public void c5_erroresDelBackendConSuTexto() throws Exception {
        Object[][] casos = {
                {OperacionCripto.Tipo.COMPRA, Moneda.BTC, "No tenes saldo en pesos suficiente para esta compra"},
                {OperacionCripto.Tipo.VENTA, Moneda.ETH, "No tenes suficiente ETH para esta venta"},
                {OperacionCripto.Tipo.COMPRA, Moneda.BTC, "El monto ingresado es muy bajo: a esta cotizacion no alcanza para comprar nada de BTC"},
                {OperacionCripto.Tipo.VENTA, Moneda.BNB, "El monto ingresado es muy bajo para esta cotizacion"},
                {OperacionCripto.Tipo.COMPRA, Moneda.SOL, "No se pudo obtener la cotizacion de SOL"},
        };
        for (Object[] caso : casos) {
            OperacionCripto op = nueva((OperacionCripto.Tipo) caso[0]);
            op.setCripto((Moneda) caso[1]);
            op.setMonto(op.esCompra() ? "0,01" : "0,00000001");
            server.enqueue(new MockResponse().setResponseCode(400).setBody("{\"error\":\"" + caso[2] + "\"}"));
            operar(op);
            assertEquals(caso[2], op.getError());
        }
        assertEquals(0, refrescos.get());
    }

    @Test
    public void c5_simboloInvalidoForzadoEs403VacioYMensajeGenerico() throws Exception {
        // El @Valid del backend rechaza un símbolo inválido con 403 SIN body (la app no puede mandarlo:
        // Moneda solo tiene los 6; se simula la respuesta)
        OperacionCripto op = nueva(OperacionCripto.Tipo.COMPRA);
        op.setMonto("100");
        server.enqueue(new MockResponse().setResponseCode(403));
        operar(op);
        assertEquals(OperacionCripto.MSG_NO_SE_PUDO, op.getError());
        server.enqueue(new MockResponse().setResponseCode(429));
        op.confirmar();
        esperar("429", () -> !op.isEnviando());
        assertEquals(ApiErrores.MSG_RATE_LIMIT, op.getError());
    }

    // ── C6: validaciones locales, sin request ────────────────────────────────

    @Test
    public void c6_validacionesLocales() {
        BigDecimal precio = new BigDecimal("128127714");
        OperacionCripto.Tipo C = OperacionCripto.Tipo.COMPRA, V = OperacionCripto.Tipo.VENTA;
        BigDecimal pesos = new BigDecimal("100000.00"), eth = new BigDecimal("1.00000000");
        assertEquals(OperacionCripto.MSG_MONTO_INVALIDO, OperacionCripto.validar(C, Moneda.BTC, "", pesos, precio));
        assertEquals(OperacionCripto.MSG_MONTO_INVALIDO, OperacionCripto.validar(C, Moneda.BTC, "0", pesos, precio));
        assertEquals(OperacionCripto.MSG_MONTO_INVALIDO, OperacionCripto.validar(V, Moneda.ETH, "0,00000000", eth, precio));
        assertEquals(OperacionCripto.MSG_MONTO_INVALIDO, OperacionCripto.validar(C, Moneda.BTC, "-1", pesos, precio));
        assertEquals("pesos: 2 decimales", OperacionCripto.msgDecimales(2),
                OperacionCripto.validar(C, Moneda.BTC, "10,001", pesos, precio));
        assertEquals("cripto: 8 decimales", OperacionCripto.msgDecimales(8),
                OperacionCripto.validar(V, Moneda.ETH, "0,123456789", eth, precio));
        assertEquals("más de 13 enteros", OperacionCripto.MSG_MONTO_INVALIDO,
                OperacionCripto.validar(V, Moneda.ETH, "12345678901234", new BigDecimal("1E20"), precio));
        assertEquals(OperacionCripto.MSG_INSUFICIENTE_COMPRA, OperacionCripto.validar(C, Moneda.BTC, "100000,01", pesos, precio));
        assertEquals(OperacionCripto.msgInsuficienteVenta(Moneda.ETH),
                OperacionCripto.validar(V, Moneda.ETH, "1,00000001", eth, precio));
        assertEquals(OperacionCripto.MSG_SIN_SALDO, OperacionCripto.validar(V, Moneda.ETH, "1", null, precio));
        assertEquals(OperacionCripto.MSG_SIN_COTIZACION, OperacionCripto.validar(V, Moneda.ETH, "1", eth, null));
        assertNull(OperacionCripto.validar(V, Moneda.ETH, "1", eth, precio));
        assertNull(OperacionCripto.validar(V, Moneda.ETH, "0,00000001", eth, precio));
        assertNull(OperacionCripto.validar(C, Moneda.BTC, "100000", pesos, precio));
    }

    @Test
    public void c6_noSeDejaTipearDeMas() {
        OperacionCripto compra = nueva(OperacionCripto.Tipo.COMPRA);
        assertFalse("pesos: 2 decimales", compra.setMonto("10,001"));
        assertTrue(compra.setMonto("10,01"));
        OperacionCripto venta = nueva(OperacionCripto.Tipo.VENTA);
        assertTrue(venta.setMonto("0,12345678"));
        assertFalse("cripto: 8 decimales", venta.setMonto("0,123456789"));
        assertFalse("13 enteros", venta.setMonto("12345678901234"));
    }

    @Test
    public void c6_invalidoNoMandaNada() throws Exception {
        OperacionCripto op = nueva(OperacionCripto.Tipo.VENTA);
        op.setCripto(Moneda.BNB); // saldo 0,00000001
        op.setMonto("0,00000002");
        op.continuar(saldo(op));
        assertEquals(OperacionCripto.msgInsuficienteVenta(Moneda.BNB), op.getError());
        op.confirmar();
        Thread.sleep(100);
        assertEquals(0, server.getRequestCount());
        assertEquals(OperacionCripto.Paso.FORMULARIO, op.getPaso());
    }

    // ── C7: "Usar todo" en venta = saldo EXACTO ──────────────────────────────

    @Test
    public void c7_usarTodoMandaElSaldoExactoDeEsaCripto() throws Exception {
        OperacionCripto op = nueva(OperacionCripto.Tipo.VENTA);
        op.usarTodo(saldo(op)); // BTC 0,12345678
        assertEquals("0,12345678", op.getMontoTexto());
        op.setCripto(Moneda.XRP);
        op.usarTodo(saldo(op)); // 400,10000000
        assertEquals("sin ceros de relleno, mismo valor", "400,1", op.getMontoTexto());
        op.setCripto(Moneda.BNB);
        op.usarTodo(saldo(op)); // 0,00000001: no se redondea a 0
        assertEquals("0,00000001", op.getMontoTexto());
        server.enqueue(new MockResponse().setResponseCode(201)
                .setBody(operacion("VENTA", "BNB", "0.00000001", "0.01", "1164353")));
        operar(op);
        JsonObject body = JsonParser.parseString(server.takeRequest(1, TimeUnit.SECONDS).getBody().readUtf8()).getAsJsonObject();
        assertEquals("sin notación científica ni redondeo", "0.00000001", body.get("monto").toString());
    }

    // ── Confirmación ─────────────────────────────────────────────────────────

    @Test
    public void continuarNoMandaNadaHastaConfirmar() throws Exception {
        OperacionCripto op = nueva(OperacionCripto.Tipo.COMPRA);
        op.setMonto("1000");
        op.confirmar(); // desde el formulario: nada
        op.continuar(saldo(op));
        assertEquals(OperacionCripto.Paso.CONFIRMAR, op.getPaso());
        op.setCripto(Moneda.ETH); // en el resumen no se puede cambiar de cripto
        assertEquals(Moneda.BTC, op.getCripto());
        Thread.sleep(100);
        assertEquals(0, server.getRequestCount());
        op.editar();
        assertEquals("1000", op.getMontoTexto());
    }

    // ── C8: un solo pedido en vuelo, incertidumbre sin reintentos ────────────

    @Test
    public void c8_dobleTapEsUnaSolaRequest() throws Exception {
        OperacionCripto op = nueva(OperacionCripto.Tipo.COMPRA);
        op.setMonto("1000");
        op.continuar(saldo(op));
        server.enqueue(new MockResponse().setResponseCode(201).setHeadersDelay(300, TimeUnit.MILLISECONDS)
                .setBody(operacion("COMPRA", "BTC", "0.0000078", "1000.00", "128127714")));
        op.confirmar();
        op.confirmar();
        esperar("éxito", () -> op.getPaso() == OperacionCripto.Paso.EXITO);
        op.confirmar();
        Thread.sleep(150);
        assertEquals(1, server.getRequestCount());
    }

    @Test
    public void c8_timeoutEsInciertoYNoReintenta() throws Exception {
        ApiService lento = (ApiService) java.lang.reflect.Proxy.newProxyInstance(ApiService.class.getClassLoader(),
                new Class<?>[]{ApiService.class}, (proxy, metodo, args) -> {
                    Object r = metodo.invoke(apiSinReintentos, args);
                    if (r instanceof retrofit2.Call) ((retrofit2.Call<?>) r).timeout().timeout(300, TimeUnit.MILLISECONDS);
                    return r;
                });
        OperacionCripto op = nueva(OperacionCripto.Tipo.COMPRA, lento);
        op.setMonto("1000");
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
        operar(op);
        assertEquals(OperacionCripto.Paso.INCIERTO, op.getPaso());
        assertEquals(OperacionCripto.MSG_INCIERTO, op.getError());
        assertEquals("refresca para ver si se hizo", 1, refrescos.get());
        op.confirmar();
        Thread.sleep(300);
        assertEquals(1, server.getRequestCount());
    }

    @Test
    public void c8_corteDespuesDeEnviarEsInciertoYNoReintenta() throws Exception {
        OperacionCripto op = nueva(OperacionCripto.Tipo.VENTA);
        op.setMonto("0,1");
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST));
        server.enqueue(new MockResponse().setResponseCode(201).setBody(operacion("VENTA", "BTC", "0.1", "1.00", "1")));
        operar(op);
        assertEquals(OperacionCripto.Paso.INCIERTO, op.getPaso());
        Thread.sleep(200);
        assertEquals("OkHttp no reintentó el POST", 1, server.getRequestCount());
        assertEquals(1, refrescos.get());
    }

    @Test
    public void c8_2xxSinBodyEsIncierto() throws Exception {

        OperacionCripto otra = nueva(OperacionCripto.Tipo.COMPRA);
        otra.setMonto("100");
        server.enqueue(new MockResponse().setResponseCode(201));
        operar(otra);
        assertEquals(OperacionCripto.Paso.INCIERTO, otra.getPaso());
        assertEquals(1, refrescos.get());
    }

    @Test
    public void c8_sinConexionAntesDeEnviarSePuedeReintentar() throws Exception {
        MockWebServer apagado = new MockWebServer();
        apagado.start();
        String url = apagado.url("/").toString();
        apagado.shutdown();
        ApiService caido = RetrofitClient.crear(url, () -> "x", System::currentTimeMillis, () -> {}, false, false);
        OperacionCripto op = nueva(OperacionCripto.Tipo.COMPRA, caido);
        op.setMonto("100");
        operar(op);
        assertEquals(ApiErrores.MSG_SIN_CONEXION, op.getError());
        assertEquals(OperacionCripto.Paso.CONFIRMAR, op.getPaso());
        assertEquals(0, refrescos.get());
    }

    @Test
    public void restaurarTrasMuerteDelProceso() {
        OperacionCripto op = nueva(OperacionCripto.Tipo.VENTA);
        op.setMonto("0,5");
        op.restaurar(Moneda.SOL, OperacionCripto.Paso.CONFIRMAR, null);
        assertEquals(Moneda.SOL, op.getCripto());
        assertEquals(OperacionCripto.Paso.CONFIRMAR, op.getPaso());
        assertEquals(new BigDecimal("0.5"), op.getMontoAConfirmar());
    }
}
