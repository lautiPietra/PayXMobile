plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.payxmobile"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.payxmobile"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    // URL del backend (BuildConfig.API_BASE_URL), una por tipo de build. El código no tiene ninguna escrita.
    buildTypes {
        debug {
            // Backend local por http: el emulador ve la PC en 10.0.2.2. Teléfono físico: definí payxApiBaseUrl
            // en gradle.properties (o pasá -PpayxApiBaseUrl=...) con la IP de la PC en la misma Wi-Fi.
            val urlDebug = (project.findProperty("payxApiBaseUrl") as String?) ?: "http://10.0.2.2:8080/"
            buildConfigField("String", "API_BASE_URL", "\"$urlDebug\"")
        }
        release {
            // URL pública del backend, SIEMPRE https (el release no permite http: network_security_config).
            // TODO: todavía no hay dominio. Reemplazá el placeholder o pasá -PpayxApiBaseUrlRelease=https://...
            // El dominio ".invalid" no existe: un release armado sin cambiarlo no le pega a ningún servidor.
            val urlRelease = (project.findProperty("payxApiBaseUrlRelease") as String?)
                ?: "https://cambiar-por-el-dominio-del-backend.invalid/"
            require(urlRelease.startsWith("https://") && urlRelease.endsWith("/")) {
                "La URL del backend para release tiene que ser https y terminar en /: $urlRelease"
            }
            buildConfigField("String", "API_BASE_URL", "\"$urlRelease\"")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        // java.time (OffsetDateTime) en minSdk 24
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    // ViewPager2
    implementation("androidx.viewpager2:viewpager2:1.1.0")

    // Pull-to-refresh del Home
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    // Retrofit + OkHttp
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Google Sign-In (Credential Manager)
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Foto de perfil: imágenes remotas + rotación EXIF al preparar la subida
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("androidx.exifinterface:exifinterface:1.4.1")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    testImplementation(libs.junit)
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}