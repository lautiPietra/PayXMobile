package com.example.payxmobile.actividad;

import static org.junit.Assume.assumeTrue;

import com.example.payxmobile.model.TransferenciaResponse;
import com.example.payxmobile.transferencias.TransferenciasRepository;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

/**
 * M15 (manual asistido): procesa con la lógica de la app un JSON real de GET /api/transferencias
 * (ruta en PAYX_M15_JSON) y escribe el orden de keys en PAYX_M15_SALIDA, para compararlo con lo
 * que calcula src/utils/actividad.js de la web sobre el mismo JSON.
 */
public class M15ComparacionWebTest {

    @Test
    public void imprimirVistaDeLaApp() throws Exception {
        String json = System.getenv("PAYX_M15_JSON");
        assumeTrue("Definí PAYX_M15_JSON para correr esta comparación", json != null);
        List<TransferenciaResponse> lista = new Gson().fromJson(
                new String(Files.readAllBytes(Paths.get(json)), StandardCharsets.UTF_8),
                new TypeToken<List<TransferenciaResponse>>() {}.getType());
        Constructor<TransferenciasRepository.Estado> c = TransferenciasRepository.Estado.class.getDeclaredConstructor(
                List.class, String.class, boolean.class, boolean.class);
        c.setAccessible(true);
        TransferenciasRepository.Estado estado = c.newInstance(lista, null, false, false);

        ZoneId zona = ZoneId.of("America/Argentina/Buenos_Aires");
        VistaMovimientos todo = VistaMovimientos.de(estado, null, null, 100, zona);
        System.out.println("APP total=" + todo.total);
        for (Actividades.Atajo a : Actividades.atajos(Clock.system(zona), zona)) {
            if (a.desde == null) continue;
            VistaMovimientos v = VistaMovimientos.de(estado, a.desde, a.hasta, 100, zona);
            System.out.println("APP " + a.etiqueta + " (" + a.desde + ".." + a.hasta + "): " + v.resumen);
        }
        Files.write(Paths.get(System.getenv("PAYX_M15_SALIDA")),
                todo.visibles.stream().map(x -> x.key).collect(Collectors.joining("\n")).getBytes(StandardCharsets.UTF_8));
    }
}
