package com.example.payxmobile.network;

import android.content.Context;

import com.example.payxmobile.utils.SessionManager;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {

    // Emulador Android: 10.0.2.2 apunta a localhost de la PC
    // Dispositivo físico: usar la IP de la PC en la red WiFi
    private static final String BASE_URL_EMULADOR = "http://10.0.2.2:8080/";
    private static final String BASE_URL_DISPOSITIVO = "http://192.168.0.228:8080/";
    private static final String BASE_URL = BASE_URL_DISPOSITIVO; // cambiá según dónde corras la app

    private static ApiService apiService;

    private RetrofitClient() {}

    public static ApiService getService(Context context) {
        if (apiService == null) {
            SessionManager session = new SessionManager(context.getApplicationContext());

            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);

            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(logging)
                    .addInterceptor(chain -> {
                        Request.Builder builder = chain.request().newBuilder();
                        String token = session.getToken();
                        if (token != null) {
                            builder.addHeader("Authorization", "Bearer " + token);
                        }
                        return chain.proceed(builder.build());
                    })
                    .build();

            apiService = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(ApiService.class);
        }
        return apiService;
    }
}
