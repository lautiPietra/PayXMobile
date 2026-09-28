package com.example.payxmobile.cajas;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.example.payxmobile.actividad.Actividad;
import com.example.payxmobile.actividad.Actividades;
import com.example.payxmobile.actividad.FeedCombinado;
import com.example.payxmobile.actividad.FiltroMoneda;
import com.example.payxmobile.actividad.ListaRemota;
import com.example.payxmobile.actividad.VistaMovimientos;
import com.example.payxmobile.model.CajaAhorroResponse;
import com.example.payxmobile.model.PlazoFijoResponse;
import com.example.payxmobile.model.TransferenciaResponse;
import com.example.payxmobile.transferencias.TransferenciasRepository;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Tarjetas de "Cajas de ahorro" (K1, K3) y depósitos/retiros en el feed de la sesión (K12-K14). Sin red. */
public class VistaYFeedCajasTest {

    private static final ZoneId BA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final Gson GSON = new Gson();

    private static CajaAhorroResponse caja(String id, String nombre, String saldo, String meta) {
        return GSON.fromJson(CajasContratoTest.caja(id, nombre, "#8b5cf6", "gift", saldo, meta), CajaAhorroResponse.class);
    }

    private static Clock a(String instante) {
        return Clock.fixed(Instant.parse(instante), BA);
    }

    // ── Tarjetas ──────────────────────────────────────────────────────────────

    @Test
    public void k3_sinMetaNoHayBarra() {
        VistaCajas.Tarjeta t = VistaCajas.de(estado(Collections.singletonList(caja("a", "Auto", "1500.5", null))), 5, null).tarjetas.get(0);
        assertFalse(t.tieneMeta);
        assertNull(t.textoMeta);
        assertEquals(0, t.progreso);
        assertEquals("Sin meta definida", t.estadoMeta);
        assertEquals("$ 1.500,50", t.saldo);
    }

    @Test
    public void k3_conMetaBarraYTexto() {
        VistaCajas.Tarjeta t = VistaCajas.de(estado(Collections.singletonList(caja("a", "Viaje", "2500", "10000"))), 5, null).tarjetas.get(0);
        assertTrue(t.tieneMeta);
        assertEquals(25, t.progreso);
        assertEquals("$ 2.500,00 de $ 10.000,00", t.textoMeta);
        assertEquals("Faltan $ 7.500,00", t.estadoMeta);
        assertFalse(t.metaCumplida);
        assertEquals("#8b5cf6", t.color);
        assertEquals("gift", t.icono);
    }

    @Test
    public void k3_topeVisualEn100AunquePaseLaMeta() {
        assertEquals(100, VistaCajas.progreso(new BigDecimal("15000"), new BigDecimal("10000")));
        assertEquals(100, VistaCajas.progreso(new BigDecimal("10000"), new BigDecimal("10000")));
        assertEquals("no dice 100 % si falta un centavo", 99, VistaCajas.progreso(new BigDecimal("9999.99"), new BigDecimal("10000")));
        assertEquals(0, VistaCajas.progreso(BigDecimal.ZERO, new BigDecimal("10000")));
        assertEquals(0, VistaCajas.progreso(new BigDecimal("0.01"), new BigDecimal("10000")));
        VistaCajas.Tarjeta t = VistaCajas.de(estado(Collections.singletonList(caja("a", "Viaje", "15000", "10000"))), 5, null).tarjetas.get(0);
        assertTrue(t.metaCumplida);
        assertEquals("¡Meta cumplida!", t.estadoMeta);
        assertEquals("$ 15.000,00 de $ 10.000,00", t.textoMeta);
    }

    @Test
    public void k1_limiteYContador() {
        List<CajaAhorroResponse> dos = Arrays.asList(caja("a", "A", "0", null), caja("b", "B", "0", null));
        VistaCajas v = VistaCajas.de(estado(dos), 3, null);
        assertTrue(v.puedeCrear);
        assertNull(v.avisoLimite);
        assertEquals("2/3 cajas", v.subtitulo);
        VistaCajas lleno = VistaCajas.de(estado(dos), 2, null);
        assertFalse("con el máximo, Crear ya está deshabilitado", lleno.puedeCrear);
        assertEquals("Ya tenés el máximo de 2 cajas de ahorro", lleno.avisoLimite);
        VistaCajas sinLimite = VistaCajas.de(estado(dos), null, null);
        assertTrue("sin /limite no se inventa: decide el backend", sinLimite.puedeCrear);
        assertEquals("2 cajas", sinLimite.subtitulo);
        assertFalse("sin lista todavía no se sabe cuántas tiene", VistaCajas.de(null, 3, null).puedeCrear);
        assertEquals(VistaCajas.Modo.VACIO, VistaCajas.de(estado(Collections.emptyList()), 3, null).modo);
    }

    @Test
    public void k7_k8_accionesDeLaTarjeta() {
        List<CajaAhorroResponse> l = Arrays.asList(caja("a", "A", "0.00", null), caja("b", "B", "10", null));
        VistaCajas v = VistaCajas.de(estado(l), 5, "b");
        assertFalse("sin saldo no se puede retirar (como la web)", v.tarjetas.get(0).puedeRetirar);
        assertFalse(v.tarjetas.get(0).eliminando);
        assertTrue(v.tarjetas.get(1).eliminando);
        assertFalse("mientras se elimina, nada más", v.tarjetas.get(1).puedeRetirar);
    }

    @Test
    public void coloresEIconosDesconocidosNoRompen() {
        CajaAhorroResponse rara = GSON.fromJson(CajasContratoTest.caja("x", "X", "#000000", "plane", "0", null), CajaAhorroResponse.class);
        VistaCajas.Tarjeta t = VistaCajas.de(estado(Collections.singletonList(rara)), 5, null).tarjetas.get(0);
        assertEquals("#ff6b1a", t.color);
        assertEquals("piggy-bank", t.icono);
    }

    // ── K12: depositar/retirar aparecen en el feed de la sesión ────────────────

    @Test
    public void k12_depositoYRetiroEnElFeed() {
        MovimientosCajaSesion sesion = new MovimientosCajaSesion();
        CajaAhorroResponse viaje = caja("c1", "Viaje", "1500", null);
        sesion.registrar(MovimientoCaja.de(MovimientoCaja.Tipo.DEPOSITO, viaje, new BigDecimal("1500"), a("2026-09-28T13:00:00Z")));
        sesion.registrar(MovimientoCaja.de(MovimientoCaja.Tipo.RETIRO, viaje, new BigDecimal("200.5"), a("2026-09-28T14:30:00Z")));
        List<MovimientoCaja> lista = sesion.getLista();
        assertEquals("más reciente primero", MovimientoCaja.Tipo.RETIRO, lista.get(0).tipo);
        assertNotEquals("ids locales únicos", lista.get(0).id, lista.get(1).id);
        assertTrue(lista.get(1).id.startsWith("c1-" + Instant.parse("2026-09-28T13:00:00Z").toEpochMilli() + "-"));

        List<Actividad> feed = Actividades.construir(Collections.emptyList(), null, null, null, lista, BA);
        assertEquals(2, feed.size());
        Actividad retiro = feed.get(0), deposito = feed.get(1);
        assertEquals(Actividad.Tipo.CAJA_AHORRO, retiro.tipo);
        assertEquals("ca-" + lista.get(0).id, retiro.key);

        FilaMovimientoCaja fd = FilaMovimientoCaja.de(deposito.movimientoCaja, BA);
        assertEquals("Depósito en Viaje", fd.titulo);
        assertEquals("sale del saldo principal", "-$ 1.500,00", fd.monto);
        assertFalse(fd.positivo);
        assertEquals("28/09 10:00", fd.fecha);
        assertEquals("el color y el ícono de la caja", "#8b5cf6", fd.color);
        assertEquals("gift", fd.icono);
        FilaMovimientoCaja fr = FilaMovimientoCaja.de(retiro.movimientoCaja, BA);
        assertEquals("Retiro de Viaje", fr.titulo);
        assertEquals("vuelve al saldo principal", "+$ 200,50", fr.monto);
        assertTrue(fr.positivo);
        assertEquals("Caja de ahorro", fr.detalle);
    }

    @Test
    public void k12_eliminarConSaldoSeVeComoRetiro() {
        FilaMovimientoCaja f = FilaMovimientoCaja.de(
                MovimientoCaja.deEliminacion(caja("c1", "Viaje", "750.00", null), a("2026-09-28T13:00:00Z")), BA);
        assertEquals("Retiro de Viaje", f.titulo);
        assertEquals("+$ 750,00", f.monto);
        assertEquals("Caja eliminada: el saldo volvió a tu cuenta", f.detalle);
    }

    @Test
    public void k12_elFeedSeReArmaSoloCuandoCambiaLaSesion() {
        MovimientosCajaSesion sesion = new MovimientosCajaSesion();
        AtomicInteger avisos = new AtomicInteger();
        sesion.observar(l -> avisos.incrementAndGet());
        assertEquals("al observar recibe el estado", 1, avisos.get());
        FeedCombinado combinado = new FeedCombinado(BA);
        List<TransferenciaResponse> t = Collections.emptyList();
        List<Actividad> vacio = combinado.de(t, null, null, null, sesion.getLista());
        assertSame(vacio, combinado.de(t, null, null, null, sesion.getLista()));
        MovimientoCaja m = MovimientoCaja.de(MovimientoCaja.Tipo.DEPOSITO, caja("c1", "Viaje", "1", null),
                BigDecimal.ONE, a("2026-09-28T13:00:00Z"));
        sesion.registrar(m);
        sesion.registrar(m); // repetido: no duplica
        assertEquals(2, avisos.get());
        assertEquals(1, combinado.de(t, null, null, null, sesion.getLista()).size());
        sesion.limpiar(); // cerrar sesión
        assertTrue(sesion.getLista().isEmpty());
        assertEquals(0, combinado.de(t, null, null, null, sesion.getLista()).size());
    }

    // ── K13: filtro "Cajas de ahorro" ──────────────────────────────────────────

    @Test
    public void k13_elFiltroCajasDeAhorroCuentaBien() {
        List<TransferenciaResponse> t = GSON.fromJson("[{\"id\":\"t1\",\"moneda\":\"PESOS\",\"monto\":5,"
                + "\"estado\":\"COMPLETADA\",\"fecha\":\"2026-09-28T12:30:00Z\",\"direccion\":\"RECIBIDA\","
                + "\"contraparteNombre\":\"Ana\",\"esEmisor\":false}]", new TypeToken<List<TransferenciaResponse>>() {}.getType());
        PlazoFijoResponse pf = GSON.fromJson("{\"id\":\"p1\",\"monto\":1000,\"tna\":35.5,\"plazoDias\":30,"
                + "\"interesEstimado\":29.18,\"montoTotal\":1029.18,\"estado\":\"ACTIVO\",\"fechaInicio\":\"2026-09-28\","
                + "\"fechaVencimiento\":\"2026-10-28\",\"fechaCreacion\":\"2026-09-28T11:00:00-03:00\"}", PlazoFijoResponse.class);
        CajaAhorroResponse viaje = caja("c1", "Viaje", "0", null);
        List<MovimientoCaja> cajas = Arrays.asList(
                MovimientoCaja.de(MovimientoCaja.Tipo.RETIRO, viaje, BigDecimal.TEN, a("2026-09-28T15:00:00Z")),
                MovimientoCaja.de(MovimientoCaja.Tipo.DEPOSITO, viaje, BigDecimal.TEN, a("2026-09-28T13:00:00Z")));
        List<Actividad> feed = Actividades.construir(t, null, null, Collections.singletonList(pf), cajas, BA);

        // Orden intercalado: retiro 12:00 BA, pf 11:00 BA... (15:00Z, 14:00Z, 13:00Z, 12:30Z)
        List<String> tipos = new ArrayList<>();
        for (Actividad x : feed) tipos.add(x.tipo.id);
        assertEquals(Arrays.asList("cajas-ahorro", "plazos-fijos", "cajas-ahorro", "transferencias"), tipos);

        Map<FiltroMoneda, Integer> cuentas = FiltroMoneda.contar(feed);
        assertEquals("depósito + retiro", Integer.valueOf(2), cuentas.get(FiltroMoneda.CAJAS_AHORRO));
        assertEquals(Integer.valueOf(1), cuentas.get(FiltroMoneda.PLAZOS_FIJOS));
        assertEquals("una caja no es una transferencia en pesos", Integer.valueOf(1), cuentas.get(FiltroMoneda.PESOS));
        assertEquals(Integer.valueOf(4), cuentas.get(FiltroMoneda.TODAS));
        for (Actividad x : FiltroMoneda.filtrar(feed, FiltroMoneda.CAJAS_AHORRO)) assertEquals(Actividad.Tipo.CAJA_AHORRO, x.tipo);
        assertEquals(Integer.valueOf(2), Actividades.contarPorTipo(feed).get(Actividad.Tipo.CAJA_AHORRO));
        assertEquals("Cajas de ahorro", FiltroMoneda.CAJAS_AHORRO.etiqueta);
        assertEquals("cajas-ahorro", Actividad.Tipo.CAJA_AHORRO.id);
    }

    @Test
    public void k13_sinMovimientosDeCajasElCartelHablaDeTipo() {
        TransferenciasRepository.Estado estado = estadoTransferencias(GSON.fromJson("[{\"id\":\"t1\",\"moneda\":\"PESOS\","
                + "\"monto\":5,\"estado\":\"COMPLETADA\",\"fecha\":\"2026-09-28T12:30:00Z\",\"direccion\":\"RECIBIDA\","
                + "\"contraparteNombre\":\"Ana\",\"esEmisor\":false}]", new TypeToken<List<TransferenciaResponse>>() {}.getType()));
        List<Actividad> feed = Actividades.construir(estado.lista, null, null, null, Collections.emptyList(), BA);
        VistaMovimientos v = VistaMovimientos.de(estado, feed, null, null, FiltroMoneda.CAJAS_AHORRO, 1, BA);
        assertEquals(VistaMovimientos.MSG_SIN_RESULTADOS_TIPO, v.sinResultados);
    }

    // ── K14: la limitación está documentada en el código ───────────────────────

    @Test
    public void k14_sinHistorialEnElBackendElFeedEsSoloDeLaSesion() {
        // No hay endpoint de historial: ApiService no tiene ningún GET de movimientos de cajas
        for (java.lang.reflect.Method m : com.example.payxmobile.network.ApiService.class.getMethods()) {
            retrofit2.http.GET get = m.getAnnotation(retrofit2.http.GET.class);
            if (get != null && get.value().startsWith("api/cajas-ahorro")) {
                assertTrue(get.value(), get.value().equals("api/cajas-ahorro") || get.value().equals("api/cajas-ahorro/limite"));
            }
        }
        // Una sesión nueva (reinstalar, otro dispositivo, volver a entrar) arranca vacía
        assertTrue(new MovimientosCajaSesion().getLista().isEmpty());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static ListaRemota.Estado<CajaAhorroResponse> estado(List<CajaAhorroResponse> lista) {
        try {
            Constructor<?> c = ListaRemota.Estado.class.getDeclaredConstructor(List.class, String.class, boolean.class, boolean.class);
            c.setAccessible(true);
            return (ListaRemota.Estado<CajaAhorroResponse>) c.newInstance(lista, null, false, false);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static TransferenciasRepository.Estado estadoTransferencias(List<TransferenciaResponse> lista) {
        try {
            Constructor<?> c = TransferenciasRepository.Estado.class.getDeclaredConstructor(List.class, String.class,
                    boolean.class, boolean.class);
            c.setAccessible(true);
            return (TransferenciasRepository.Estado) c.newInstance(lista, null, false, false);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
