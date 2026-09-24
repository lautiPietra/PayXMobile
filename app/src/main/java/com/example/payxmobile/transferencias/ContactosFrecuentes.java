package com.example.payxmobile.transferencias;

import com.example.payxmobile.model.TransferenciaResponse;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Alias a los que ya se les transfirió (como la web): sugerencias del campo destinatario. */
public final class ContactosFrecuentes {

    public static final int MAX_SUGERENCIAS = 5;

    public static final class Contacto {
        public final String alias;
        public final String nombre;

        Contacto(String alias, String nombre) {
            this.alias = alias;
            this.nombre = nombre;
        }
    }

    private ContactosFrecuentes() {}

    /** De las ENVIADAS, más reciente primero (orden del backend), sin repetir alias. */
    public static List<Contacto> de(List<TransferenciaResponse> transferencias) {
        List<Contacto> contactos = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        for (TransferenciaResponse t : transferencias) {
            String alias = t.getContraparteAlias();
            // "-" = cuenta eliminada: no sirve como sugerencia
            if (!"ENVIADA".equals(t.getDireccion()) || alias == null || alias.equals("-") || !vistos.add(alias)) continue;
            contactos.add(new Contacto(alias, t.getContraparteNombre()));
        }
        return contactos;
    }

    /** Campo vacío: los 5 más recientes; si no, los que EMPIEZAN con lo escrito (sin mayúsculas). */
    public static List<Contacto> sugerencias(List<Contacto> contactos, String texto) {
        String busqueda = texto != null ? texto.trim().toLowerCase(Locale.ROOT) : "";
        List<Contacto> resultado = new ArrayList<>();
        for (Contacto c : contactos) {
            if (resultado.size() == MAX_SUGERENCIAS) break;
            if (busqueda.isEmpty() || c.alias.toLowerCase(Locale.ROOT).startsWith(busqueda)) resultado.add(c);
        }
        return resultado;
    }
}
