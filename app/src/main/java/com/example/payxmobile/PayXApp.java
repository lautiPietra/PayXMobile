package com.example.payxmobile;

import android.app.Application;

import com.example.payxmobile.asistente.ui.InstaladorChatAsistente;

public class PayXApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // Chat flotante del asistente de IA en todas las pantallas privadas
        registerActivityLifecycleCallbacks(new InstaladorChatAsistente());
    }
}
