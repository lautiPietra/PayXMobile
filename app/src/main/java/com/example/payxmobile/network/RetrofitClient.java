package com.example.payxmobile.network;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.example.payxmobile.BuildConfig;
import com.example.payxmobile.utils.SesionUtils;
import com.example.payxmobile.utils.SessionManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import okhttp3.ConnectionPool;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {

    private static ApiService apiService;
    private static ApiService apiServiceSinReintentos;
    private static ApiService apiServiceAsistente;
    private static final int SEGUNDOS_LECTURA = 30;
    // El backend espera hasta 30 s a Anthropic por vuelta y puede dar hasta 5 vueltas por mensaje; en la
    // práctica contesta en pocos segundos. 90 s cubre los casos lentos sin dejar al usuario colgado.
    static final int SEGUNDOS_LECTURA_ASISTENTE = 90;
    // Un solo pool para los dos clientes de la app: el POST (sin reintentos) reusa la conexión ya
    // abierta por los GET del polling en vez de abrir una nueva (en Wi-Fi eso es lo que más tarda).
    private static final ConnectionPool POOL_APP = new ConnectionPool();

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

    /**
     * Cliente del asistente de IA: sin reintentos (cada pedido le cuesta al backend una llamada paga a
     * Anthropic) y con una espera de lectura más larga, porque el backend puede encadenar varias
     * consultas antes de contestar y no manda nada hasta tener la respuesta completa.
     */
    public static synchronized ApiService getServiceAsistente(Context context) {
        if (apiServiceAsistente == null) {
            apiServiceAsistente = crearParaApp(context, false, SEGUNDOS_LECTURA_ASISTENTE);
        }
        return apiServiceAsistente;
    }

    private static ApiService crearParaApp(Context context, boolean reintentar) {
        return crearParaApp(context, reintentar, SEGUNDOS_LECTURA);
    }

    private static ApiService crearParaApp(Context context, boolean reintentar, int segundosLectura) {
        Context appContext = context.getApplicationContext();
        SessionManager session = new SessionManager(appContext);
        Handler main = new Handler(Looper.getMainLooper());
        SesionHttp sesion = new SesionHttp() {
            @Override
            public String token() {
                return session.getToken();
            }

            @Override
            public void noAutenticado(String tokenEnviado) {
                main.post(() -> SesionUtils.noAutenticado(appContext, tokenEnviado));
            }

            @Override
            public void tokenRenovado(String tokenEnviado, String nuevo) {
                session.renovarToken(tokenEnviado, nuevo);
            }
        };
        return crear(BuildConfig.API_BASE_URL, sesion, BuildConfig.DEBUG, reintentar, POOL_APP, segundosLectura);
    }

    /**
     * Header con un token nuevo: cuando al que se mandó le quedaban menos de 30 min (sesión deslizante) y
     * en la respuesta a PUT /api/perfil/password (cambiar la contraseña invalida todos los tokens anteriores,
     * incluido el de esta sesión). Se guarda en cualquier respuesta que no sea 401, sea cual sea la pantalla.
     */
    public static final String HEADER_TOKEN_RENOVADO = "X-Renewed-Token";

    /** Lo que el cliente HTTP necesita de la sesión. En la app: SessionManager + SesionUtils. */
    public interface SesionHttp {
        /** El token actual, o null sin sesión. */
        String token();

        /**
         * 401 a un pedido autenticado: sin token válido (ausente, inválido, vencido, cuenta desactivada o
         * emitido antes del último cambio/reset de contraseña, claim "pv"). Hay que cerrar la sesión, salvo
         * que ya no sea la que mandó el pedido.
         */
        void noAutenticado(String tokenEnviado);

        /** La respuesta a un pedido con "tokenEnviado" trajo X-Renewed-Token: reemplazar el guardado. */
        void tokenRenovado(String tokenEnviado, String nuevo);
    }

    /**
     * Arma el cliente sin depender de Android (lo usan los tests con MockWebServer).
     *
     * @param onNoAutenticado se llama cuando una request autenticada recibe 401. Un 403 NO cierra la
     *                        sesión: es "autenticado pero sin permiso para esto".
     */
    public static ApiService crear(String baseUrl, Supplier<String> tokenProvider, Runnable onNoAutenticado,
                                   boolean debug) {
        return crear(baseUrl, tokenProvider, onNoAutenticado, debug, true);
    }

    /** @param reintentar false = sin retryOnConnectionFailure (requests sin idempotencia). */
    public static ApiService crear(String baseUrl, Supplier<String> tokenProvider, Runnable onNoAutenticado,
                                   boolean debug, boolean reintentar) {
        return crear(baseUrl, sesionDeTest(tokenProvider, onNoAutenticado), debug, reintentar, null, SEGUNDOS_LECTURA);
    }

    /** @param segundosLectura cuánto esperar la respuesta una vez mandado el pedido. */
    public static ApiService crear(String baseUrl, Supplier<String> tokenProvider, Runnable onNoAutenticado,
                                   boolean debug, boolean reintentar, int segundosLectura) {
        return crear(baseUrl, sesionDeTest(tokenProvider, onNoAutenticado), debug, reintentar, null, segundosLectura);
    }

    private static SesionHttp sesionDeTest(Supplier<String> tokenProvider, Runnable onNoAutenticado) {
        return new SesionHttp() {
            @Override public String token() { return tokenProvider.get(); }
            @Override public void noAutenticado(String tokenEnviado) { onNoAutenticado.run(); }
            @Override public void tokenRenovado(String tokenEnviado, String nuevo) {}
        };
    }

    /**
     * @param pool            pool de conexiones compartido (null = uno propio).
     * @param segundosLectura cuánto esperar la respuesta una vez mandado el pedido.
     */
    public static ApiService crear(String baseUrl, SesionHttp sesion, boolean debug, boolean reintentar,
                                   ConnectionPool pool, int segundosLectura) {
        return new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(crearCliente(HttpUrl.get(baseUrl), sesion, debug, reintentar, pool, segundosLectura))
                .addConverterFactory(GsonConverterFactory.create(gson()))
                .build()
                .create(ApiService.class);
    }

    /** Cómo viaja el JSON: los BigDecimal siempre planos ("10.2", "0.00000001"), nunca vía double. */
    public static Gson gson() {
        return new GsonBuilder().registerTypeAdapter(BigDecimal.class, new BigDecimalPlano()).create();
    }

    /**
     * El cliente HTTP con el interceptor de sesión (Authorization, 401, X-Renewed-Token).
     *
     * @param base URL base del backend de PayX: el token se manda SOLO a pedidos que van ahí.
     */
    public static OkHttpClient crearCliente(HttpUrl base, SesionHttp sesion, boolean debug, boolean reintentar,
                                            ConnectionPool pool, int segundosLectura) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .retryOnConnectionFailure(reintentar)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(segundosLectura, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(chain -> {
                    Request original = chain.request();
                    // El token viaja solo al backend de PayX, nunca a otro dominio. Y no a /api/auth/**, que es
                    // público: ahí un 401 es un login fallido ("Credenciales invalidas"), no una sesión vencida.
                    if (!esDelBackend(base, original.url()) || esPublica(base, original.url())) {
                        return chain.proceed(original);
                    }
                    String token = sesion.token();
                    if (token == null) return chain.proceed(original);
                    Response response = chain.proceed(original.newBuilder()
                            .header("Authorization", "Bearer " + token)
                            .build());
                    if (response.code() == 401) {
                        sesion.noAutenticado(token);
                    } else {
                        // Sesión deslizante: mientras se use la app, el token se renueva solo, sin avisar
                        String renovado = response.header(HEADER_TOKEN_RENOVADO);
                        if (renovado != null && !renovado.trim().isEmpty()) {
                            sesion.tokenRenovado(token, renovado.trim());
                        }
                    }
                    return response;
                });

        // Antes de mandar un POST por una conexión reusada, OkHttp verifica que siga viva (chequeo
        // extensivo para todo lo que no es GET): no se manda la compra por un socket muerto.
        if (pool != null) builder.connectionPool(pool);

        if (debug) {
            // BASIC: solo método, URL, código y duración. Nunca headers (Authorization) ni bodies
            // (contraseñas, token del login).
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BASIC);
            logging.redactHeader("Authorization");
            logging.redactHeader(HEADER_TOKEN_RENOVADO);
            builder.addInterceptor(logging);
        }

        return builder.build();
    }

    /** Mismo esquema, host y puerto que la URL base, y dentro de su ruta. */
    static boolean esDelBackend(HttpUrl base, HttpUrl url) {
        return url.scheme().equals(base.scheme()) && url.host().equals(base.host()) && url.port() == base.port()
                && url.encodedPath().startsWith(base.encodedPath());
    }

    /** /api/auth/** (login, Google, registro, códigos): lo único que el backend atiende sin token. */
    static boolean esPublica(HttpUrl base, HttpUrl url) {
        return url.encodedPath().startsWith(base.encodedPath() + "api/auth/");
    }
}
