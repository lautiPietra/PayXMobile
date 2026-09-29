package com.example.payxmobile.asistente;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * El modelo a veces contesta con **negritas** de markdown. En vez de mostrar los asteriscos, se
 * sacan y se marca qué tramos van en negrita. Un "**" sin pareja queda tal cual.
 */
public final class TextoAsistente {

    public final String texto;
    /** Pares [inicio, fin) sobre "texto". */
    public final List<int[]> negritas;

    private TextoAsistente(String texto, List<int[]> negritas) {
        this.texto = texto;
        this.negritas = negritas;
    }

    public static TextoAsistente de(String crudo) {
        if (crudo == null) return new TextoAsistente("", Collections.emptyList());
        StringBuilder sb = new StringBuilder(crudo.length());
        List<int[]> rangos = new ArrayList<>();
        int i = 0;
        while (i < crudo.length()) {
            int abre = crudo.indexOf("**", i);
            int cierra = abre >= 0 ? crudo.indexOf("**", abre + 2) : -1;
            if (abre < 0 || cierra < 0) {
                sb.append(crudo, i, crudo.length());
                break;
            }
            sb.append(crudo, i, abre);
            int inicio = sb.length();
            sb.append(crudo, abre + 2, cierra);
            if (sb.length() > inicio) rangos.add(new int[]{inicio, sb.length()});
            i = cierra + 2;
        }
        return new TextoAsistente(sb.toString(), rangos);
    }
}
