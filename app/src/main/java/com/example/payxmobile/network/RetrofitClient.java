package com.example.payxmobile.network;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.example.payxmobile.BuildConfig;
import com.example.payxmobile.utils.JwtUtils;
import com.example.payxmobile.utils.SesionUtils;
import com.example.payxmobile.utils.SessionManager;
import com.google.gson.GsonBuilder;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {

    private static ApiService apiService;
    private static ApiService apiServiceSinReintentos;

    private RetrofitClient() {}

    public static synchronized ApiService getService(Context context) {
        if (apiService == null) apiService = crearParaApp(context, true);
        return apiService;
    }

    /**
     * Cliente para requests SIN idempotencia (POST /api/transferencias): OkHttp no la reintenta
     * sola ante un corte de conexión, porque un reintento podría crear la transferencia dos veces.
     */
    public static synchronized ApiService getServiceSinReintentos(Context context) {
        if (apiServiceSinReintentos == null) apiServiceSinReintentos = crearParaApp(context, false);
        return apiServiceSinReintentos;
    }

    private static ApiService crearParaApp(Context context, boolean reintentar) {
        Context appContext = context.getApplicationContext();
        SessionManager session = new SessionManager(appContext);
        Handler main = new Handler(Looper.getMainLooper());
        return crear(
                BuildConfig.API_BASE_URL,
                session::getToken,
                System::currentTimeMillis,
                () -> main.post(() -> SesionUtils.sesionVencida(appContext)),
                BuildConfig.DEBUG,
                reintentar);
    }

    /**
     * Arma el cliente sin depender de Android (lo usan los tests con MockWebServer).
     *
     * @param onSesionVencida se llama cuando una request autenticada recibe 403 y el "exp" del
     *                        token ya pasó. Un 403 con token vigente NO es sesión vencida: es un
     *                        error de validación del backend.
     */
    public static ApiService crear(String baseUrl, Supplier<String> tokenProvider,
                                   LongSupplier reloj, Runnable onSesionVencida, boolean debug) {
        return crear(baseUrl, tokenProvider, reloj, onSesionVencida, debug, true);
    }

    /** @param reintentar false = sin retryOnConnectionFailure (requests sin idempotencia). */
    public static ApiService crear(String baseUrl, Supplier<String> tokenProvider, LongSupplier reloj,
                                   Runnable onSesionVencida, boolean debug, boolean reintentar) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .retryOnConnectionFailure(reintentar)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(chain -> {
                    Request original = chain.request();
                    String token = tokenProvider.get();
                    if (token == null || original.url().encodedPath().startsWith("/api/auth/")) {
                        return chain.proceed(original);
                    }
                    Response response = chain.proceed(original.newBuilder()
                            .header("Authorization", "Bearer " + token)
                            .build());
                    if (response.code() == 403 && JwtUtils.estaVencido(token, reloj.getAsLong())) {
                        onSesionVencida.run();
                    }
                    return response;
                });

        if (debug) {
            // BASIC: solo método, URL, código y duración. Nunca headers (Authorization) ni bodies
            // (contraseñas, token del login).
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BASIC);
            logging.redactHeader("Authorization");
            builder.addInterceptor(logging);
        }

        return new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(builder.build())
                .addConverterFactory(GsonConverterFactory.create(new GsonBuilder()
                        .registerTypeAdapter(BigDecimal.class, new BigDecimalPlano())
                        .create()))
                .build()
                .create(ApiService.class);
    }
}
