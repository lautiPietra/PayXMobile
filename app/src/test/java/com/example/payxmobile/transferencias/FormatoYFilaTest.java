package com.example.payxmobile.transferencias;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.payxmobile.model.TransferenciaResponse;
import com.google.gson.Gson;

import org.junit.Test;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.List;

/** T13 (mapeo real), T14 (fila en todas las combinaciones), T15 (fechas y vencimiento), T6 (USD). */
public class FormatoYFilaTest {

    private static final ZoneId BA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final Gson GSON = new Gson();

    /** Copiado de una respuesta REAL de GET /api/transferencias (cuenta B). */
    static final String REAL = "{\"id\":\"a65c5011-e462-49ac-9b7e-7a52b438abe8\",\"moneda\":\"PESOS\","
            + "\"monto\":1500.00000000,\"concepto\":null,\"estado\":\"COMPLETADA\","
            + "\"fecha\":\"2026-09-22T14:13:47.98468Z\",\"fechaConfirmacion\":\"2026-09-22T14:13:48.125512Z\","
            + "\"direccion\":\"ENVIADA\",\"contraparteNombre\":\"Carlos Ruiz\","
            + "\"contraparteAlias\":\"carlos.ruiz.payx\",\"esEmisor\":true}";

    static TransferenciaResponse t(String moneda, String monto, String estado, String direccion, boolean esEmisor) {
        return GSON.fromJson("{\"id\":\"x\",\"moneda\":\"" + moneda + "\",\"monto\":" + monto + ",\"estado\":\""
                + estado + "\",\"fecha\":\"2026-09-24T10:15:30.123-03:00\",\"direccion\":\"" + direccion
                + "\",\"contraparteNombre\":\"Ana Pérez\",\"contraparteAlias\":\"ana.payx\",\"esEmisor\":" + esEmisor + "}",
                TransferenciaResponse.class);
    }

    @Test
    public void t13_mapeoDeUnaRespuestaReal() {
        TransferenciaResponse r = GSON.fromJson(REAL, TransferenciaResponse.class);
        assertEquals("a65c5011-e462-49ac-9b7e-7a52b438abe8", r.getId());
        assertEquals("PESOS", r.getMoneda());
        assertEquals(0, new BigDecimal("1500").compareTo(r.getMonto()));
        assertNull(r.getConcepto());
        assertEquals("COMPLETADA", r.getEstado());
        assertEquals("ENVIADA", r.getDireccion());
        assertTrue(r.isEsEmisor());
        assertEquals("Carlos Ruiz", r.getContraparteNombre());
        // Z = UTC -> 11:13 en Buenos Aires
        assertEquals("22/09/2026 11:13", FormatoTransferencia.fechaHora(r.getFecha(), BA));
        assertEquals("22/09/2026 11:13", FormatoTransferencia.fechaHora(r.getFechaConfirmacion(), BA));
    }

    @Test
    public void t13_usuarioEliminadoYSinConfirmacion() {
        TransferenciaResponse r = GSON.fromJson("{\"id\":\"y\",\"moneda\":\"USD\",\"monto\":10,\"estado\":\"PENDIENTE\","
                + "\"fecha\":\"2026-09-24T10:15:30-03:00\",\"fechaConfirmacion\":null,\"direccion\":\"RECIBIDA\","
                + "\"contraparteNombre\":\"Usuario eliminado\",\"contraparteAlias\":\"-\",\"esEmisor\":false}",
                TransferenciaResponse.class);
        assertNull(r.getFechaConfirmacion());
        assertFalse(r.isEsEmisor());
        FilaTransferencia f = FilaTransferencia.de(r, BA);
        assertEquals("De Usuario eliminado", f.detalle);
    }

    @Test
    public void t15_fechasEnHoraLocalConDistintosOffsets() {
        // Mismo instante con -03:00 y con +00:00
        assertEquals("24/09/2026 10:15", FormatoTransferencia.fechaHora("2026-09-24T10:15:30.123-03:00", BA));
        assertEquals("24/09/2026 10:15", FormatoTransferencia.fechaHora("2026-09-24T13:15:30+00:00", BA));
        assertEquals("24/09/2026 13:15", FormatoTransferencia.fechaHora("2026-09-24T13:15:30Z", ZoneId.of("UTC")));
        assertEquals("24/09 10:15", FormatoTransferencia.fechaCorta("2026-09-24T13:15:30Z", BA));
        // Cruce de día
        assertEquals("23/09/2026 22:30", FormatoTransferencia.fechaHora("2026-09-24T01:30:00Z", BA));
        assertEquals("", FormatoTransferencia.fechaHora("basura", BA));
        assertEquals("", FormatoTransferencia.fechaHora(null, BA));
    }

    @Test
    public void t15_vencimientoEsFechaMas24Horas() {
        assertEquals("25/09/2026 10:15", FormatoTransferencia.vencimiento("2026-09-24T10:15:30-03:00", BA));
        assertEquals("25/09/2026 10:15", FormatoTransferencia.vencimiento("2026-09-24T13:15:30Z", BA));
    }

    @Test
    public void t14_filaEnTodasLasCombinaciones() {
        String[][] esperado = {
                // moneda, monto, estado, direccion -> titulo, detalle, monto, estilo, icono, pendiente
                {"PESOS", "1234.5", "COMPLETADA", "ENVIADA", "Transferencia enviada", "A Ana Pérez", "-$ 1.234,50", "NEGATIVO", "ENVIADA", "false"},
                {"PESOS", "1234.5", "COMPLETADA", "RECIBIDA", "Transferencia recibida", "De Ana Pérez", "+$ 1.234,50", "POSITIVO", "RECIBIDA", "false"},
                {"PESOS", "10", "PENDIENTE", "ENVIADA", "Transferencia enviada", "A Ana Pérez", "-$ 10,00", "NEGATIVO", "ENVIADA", "true"},
                // Pendiente recibida: "+ monto" y badge, aunque la plata todavía no llegó (como la web)
                {"PESOS", "10", "PENDIENTE", "RECIBIDA", "Transferencia recibida", "De Ana Pérez", "+$ 10,00", "POSITIVO", "RECIBIDA", "true"},
                // Cancelada: sin signo, estilo gris, ícono X
                {"PESOS", "10", "CANCELADA", "ENVIADA", "Transferencia cancelada", "A Ana Pérez", "$ 10,00", "CANCELADA", "CANCELADA", "false"},
                {"PESOS", "10", "CANCELADA", "RECIBIDA", "Transferencia cancelada", "De Ana Pérez", "$ 10,00", "CANCELADA", "CANCELADA", "false"},
                {"USD", "10", "COMPLETADA", "ENVIADA", "Transferencia enviada", "A Ana Pérez", "-US$ 10,00", "NEGATIVO", "ENVIADA", "false"},
                // Cripto: sin ceros de relleno e ícono de monedas
                {"BTC", "0.50000000", "COMPLETADA", "ENVIADA", "Transferencia enviada", "A Ana Pérez", "-0,5 BTC", "NEGATIVO", "CRIPTO", "false"},
                {"SOL", "0.00170772", "PENDIENTE", "RECIBIDA", "Transferencia recibida", "De Ana Pérez", "+0,00170772 SOL", "POSITIVO", "CRIPTO", "true"},
                {"ETH", "1", "CANCELADA", "ENVIADA", "Transferencia cancelada", "A Ana Pérez", "1 ETH", "CANCELADA", "CANCELADA", "false"},
        };
        for (String[] c : esperado) {
            FilaTransferencia f = FilaTransferencia.de(t(c[0], c[1], c[2], c[3], c[3].equals("ENVIADA")), BA);
            String caso = String.join("/", c[0], c[2], c[3]);
            assertEquals(caso, c[4], f.titulo);
            assertEquals(caso, c[5], f.detalle);
            assertEquals(caso, c[6], f.monto);
            assertEquals(caso, c[7], f.estilo.name());
            assertEquals(caso, c[8], f.icono.name());
            assertEquals(caso, Boolean.parseBoolean(c[9]), f.pendiente);
            assertEquals(caso, "24/09 10:15", f.fechaCorta);
        }
    }

    @Test
    public void montosDelFormularioConDecimalesFijos() {
        assertEquals("$ 1.234,50", FormatoTransferencia.montoFormulario(new BigDecimal("1234.5"), Moneda.PESOS));
        assertEquals("US$ 10,00", FormatoTransferencia.montoFormulario(BigDecimal.TEN, Moneda.USD));
        assertEquals("BTC 0,50000000", FormatoTransferencia.montoFormulario(new BigDecimal("0.5"), Moneda.BTC));
        assertEquals("SOL 0,00170772", FormatoTransferencia.montoFormulario(new BigDecimal("0.00170772"), Moneda.SOL));
    }

    @Test
    public void t6_equivalenteUsd() {
        // node: (10.5*1535).toLocaleString('es-AR', 2 dec) -> "16.117,50"
        assertEquals("≈ $ 16.117,50 al tipo de cambio actual ($ 1.535,00)",
                FormatoTransferencia.equivalenteUsd(new BigDecimal("10.5"), new BigDecimal("1535")));
        assertEquals("≈ $ 0,02 al tipo de cambio actual ($ 1.535,00)",
                FormatoTransferencia.equivalenteUsd(new BigDecimal("0.00001"), new BigDecimal("1535")));
        // Sin cotización (503): se oculta, nunca "0"
        assertNull(FormatoTransferencia.equivalenteUsd(BigDecimal.TEN, null));
    }

    // ── T8: contactos frecuentes ─────────────────────────────────────────────

    @Test
    public void t8_contactosFrecuentes() {
        String lista = "[" + String.join(",",
                c("ENVIADA", "zeta.payx", "Zeta"),
                c("RECIBIDA", "recibi.payx", "Recibí"),
                c("ENVIADA", "ana.payx", "Ana"),
                c("ENVIADA", "zeta.payx", "Zeta"),   // repetido
                c("ENVIADA", "-", "Usuario eliminado"),
                c("ENVIADA", "Anabel.PAYX", "Anabel"),
                c("ENVIADA", "beto.payx", "Beto"),
                c("ENVIADA", "carla.payx", "Carla"),
                c("ENVIADA", "dani.payx", "Dani")) + "]";
        List<TransferenciaResponse> ts = GSON.fromJson(lista,
                new com.google.gson.reflect.TypeToken<List<TransferenciaResponse>>() {}.getType());
        List<ContactosFrecuentes.Contacto> contactos = ContactosFrecuentes.de(ts);
        assertEquals(6, contactos.size());
        assertEquals("zeta.payx", contactos.get(0).alias);  // más reciente primero
        assertEquals("ana.payx", contactos.get(1).alias);
        assertEquals("Ana", contactos.get(1).nombre);

        List<ContactosFrecuentes.Contacto> vacio = ContactosFrecuentes.sugerencias(contactos, "  ");
        assertEquals(5, vacio.size());                        // campo vacío: los 5 más recientes
        assertEquals("zeta.payx", vacio.get(0).alias);

        List<ContactosFrecuentes.Contacto> an = ContactosFrecuentes.sugerencias(contactos, "AN");
        assertEquals(2, an.size());                           // empiezan con "an", sin mayúsculas
        assertEquals("ana.payx", an.get(0).alias);
        assertEquals("Anabel.PAYX", an.get(1).alias);
        assertTrue(ContactosFrecuentes.sugerencias(contactos, "payx").isEmpty()); // prefijo, no "contiene"
    }

    private static String c(String dir, String alias, String nombre) {
        return "{\"id\":\"" + alias + dir + "\",\"moneda\":\"PESOS\",\"monto\":1,\"estado\":\"COMPLETADA\","
                + "\"fecha\":\"2026-09-24T10:00:00Z\",\"direccion\":\"" + dir + "\",\"contraparteNombre\":\""
                + nombre + "\",\"contraparteAlias\":\"" + alias + "\",\"esEmisor\":" + dir.equals("ENVIADA") + "}";
    }
}
