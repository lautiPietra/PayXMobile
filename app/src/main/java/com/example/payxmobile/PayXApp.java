package com.example.payxmobile;

import android.app.Application;

import com.example.payxmobile.asistente.ui.InstaladorChatAsistente;
import com.example.payxmobile.utils.AjusteBarrasSistema;

public class PayXApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // Chat flotante del asistente de IA en todas las pantallas privadas
        registerActivityLifecycleCallbacks(new InstaladorChatAsistente());
        // Contenido debajo de la barra de estado y arriba de la de navegación (Android 15+)
        registerActivityLifecycleCallbacks(new AjusteBarrasSistema());
    }
}
