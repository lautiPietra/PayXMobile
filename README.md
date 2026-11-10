# PayX Mobile

App Android nativa, en Java, de PayX: una billetera virtual simulada que usa el mismo backend que la versión web. Es un proyecto de práctica y no mueve dinero real.

## Demo

Todavía no hay un APK publicado. El build release necesita el dominio del backend, que aún no existe.

![Captura](docs/captura-1.png)
![Captura](docs/captura-2.png)

## Funcionalidades

- Registro con código de verificación por email, login con email o con Google, recuperación de contraseña y sesión que se renueva sola.
- Inicio con saldos en pesos, dólares y cripto, y últimas actividades.
- Transferencias en pesos, dólares y cripto, directas o pendientes. Se pueden confirmar, cancelar y editar el concepto.
- Mis movimientos con filtros, paginado de a 30 y feed unificado de transferencias, dólares, cripto, plazos fijos, cajas y servicios.
- Compra y venta de dólares y de cripto, plazos fijos, cajas de ahorro (crear, depositar, retirar, editar y eliminar), tarjeta virtual y pago de servicios.
- Estadísticas de gastos, notificaciones y perfil con foto y cambio de contraseña.
- Asistente de IA en un chat superpuesto.

## Stack

- App: Java 11, minSdk 24, targetSdk 36, Gradle (AGP 9.1.1), vistas XML con Material Components.
- Red: Retrofit 2.11, OkHttp 4.12 y Gson.
- Otras librerías: Credential Manager para Google Sign-In, Glide para imágenes, ViewPager2 y SwipeRefreshLayout.
- Base de datos: no usa una local. Todo viene de la API.
- Tests: JUnit 4 y MockWebServer.
- Infraestructura: sin publicar. El backend es PayX-backend (repositorio aparte, Spring Boot y PostgreSQL).

## Arquitectura

Cada módulo (`transferencias`, `dolares`, `cripto`, `plazofijo`, `cajas`, `servicios`, `tarjeta`, `estadisticas`, `notificaciones`, `asistente`, `actividad`) agrupa su repositorio, su ViewModel cuando lo necesita, su lógica pura y su carpeta `ui`. Las pantallas son Activities con layouts XML. `network/` arma el cliente Retrofit con un interceptor que agrega el `Authorization`, atiende el 401 y guarda el token que el backend renueva en `X-Renewed-Token`. La URL del backend sale de `BuildConfig.API_BASE_URL`.

## Decisiones técnicas

- **Token de sesión cifrado**: se guarda con AES-GCM y la clave vive en el Android Keystore, donde no se puede extraer (`TokenCipher`).
- **HTTPS obligatorio en release**: `network_security_config` solo permite http en el build debug, para el backend local. El log de OkHttp no imprime headers ni cuerpos, para que el token no llegue a Logcat.
- **Sin URL escrita en el código**: el build debug usa `payxApiBaseUrl` y el release usa `payxApiBaseUrlRelease` (debe ser https). Sin definirla, el release apunta a un dominio inexistente.
- **Lógica fuera de las Activities**: validadores, formatos, cálculos y repositorios se prueban en la JVM, sin dispositivo ni emulador.

## Cómo correrlo

Requisitos: Android Studio con el SDK 36 y el backend PayX corriendo.

```
./gradlew assembleDebug
./gradlew installDebug
```

`installDebug` necesita un emulador o un teléfono conectado. En Windows usá `gradlew.bat`. También se puede abrir el proyecto en Android Studio y ejecutarlo desde ahí.

El emulador ve la PC en `http://10.0.2.2:8080/`, que es el valor por defecto del build debug. El archivo `gradle.properties` trae una línea `payxApiBaseUrl` con la IP de la PC del autor. Cambiala por la IP de tu PC si usás un teléfono físico en la misma Wi-Fi, o borrala si usás el emulador. También se puede pasar `-PpayxApiBaseUrl=http://<ip>:8080/`.

La app no usa variables de entorno. El Client ID de Google (`google_web_client_id` en `strings.xml`) no es un secreto.

## Tests

```
./gradlew testDebugUnitTest
```

Hay 466 tests JUnit en 54 clases y no falla ninguno. 456 corren sin backend ni dispositivo. Los otros 10 son pruebas end to end contra un backend real y se omiten si no se definen `PAYX_E2E=1`, `PAYX_E2E_EMAIL_A`, `PAYX_E2E_PASSWORD` y las demás variables `PAYX_E2E_*` que pide cada test (cuentas de prueba y `PAYX_E2E_URL`). El único test instrumentado es el de ejemplo de Android Studio.

## Próximos pasos

- Publicar un APK: falta el dominio https del backend y firmar el release.
- Panel de administración: hoy existe solo en la web.
- Tests instrumentados de las pantallas.

## Autor

Lautaro Pietrantonio. [LinkedIn](https://www.linkedin.com/in/lautaro-pietrantonio-803731365/)

Repositorios relacionados: [PayX-Frontend](https://github.com/lautiPietra/PayX-Frontend) (web) y PayX-backend (API).
