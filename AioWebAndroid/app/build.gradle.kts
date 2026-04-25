plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.24"
}

import java.util.Properties

val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) load(f.inputStream())
}

android {
    namespace = "com.aioweb.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.aioweb.app"
        minSdk = 24
        targetSdk = 34
        // Use CI run number as versionCode so each successive build is treated as an update.
        // Falls back to 1 for local builds.
        versionCode = (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1)
        versionName = "1.0.${System.getenv("GITHUB_RUN_NUMBER") ?: "0"}"

        // Backend base URL (override in Settings screen at runtime via DataStore)
        buildConfigField(
            "String", "DEFAULT_BACKEND_URL",
            "\"https://aio-android-port.preview.emergentagent.com\""
        )
        // TMDB v3 API key (free public dev key – users can override in Settings)
        buildConfigField("String", "TMDB_API_KEY", "\"8265bd1679663a7ea12ac168da84d2e8\"")
    }

    signingConfigs {
        create("release") {
            val ksPath = System.getenv("KEYSTORE_PATH") ?: keystoreProps["storeFile"]?.toString()
            val ksPassword = System.getenv("KEYSTORE_PASSWORD") ?: keystoreProps["storePassword"]?.toString()
            val ksKeyAlias = System.getenv("KEY_ALIAS") ?: keystoreProps["keyAlias"]?.toString()
            val ksKeyPassword = System.getenv("KEY_PASSWORD") ?: keystoreProps["keyPassword"]?.toString()

            if (ksPath != null && file(ksPath).exists() && ksPassword != null && ksKeyAlias != null && ksKeyPassword != null) {
                storeFile = file(ksPath)
                storePassword = ksPassword
                keyAlias = ksKeyAlias
                keyPassword = ksKeyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val rel = signingConfigs.getByName("release")
            if (rel.storeFile != null) signingConfig = rel
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            // Use the release keystore for debug too when available, so successive CI debug builds
            // can update each other on the device (otherwise random debug.keystore per CI run breaks updates).
            val rel = signingConfigs.findByName("release")
            if (rel != null && rel.storeFile != null) {
                signingConfig = rel
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-opt-in=kotlin.RequiresOptIn")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE*"
            excludes += "META-INF/NOTICE*"
        }
    }
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")

    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.0")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Kotlin Coroutines + Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Networking (Retrofit + OkHttp + official Kotlinx serialization converter)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Coil image loading
    implementation("io.coil-kt:coil-compose:2.7.0")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Media3 ExoPlayer (audio + video)
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")
    implementation("androidx.media3:media3-common:1.4.1")

    // Torrent / P2P streaming (libtorrent4j + tiny embedded HTTP server feeding ExoPlayer)
    implementation("org.libtorrent4j:libtorrent4j:2.1.0-32")
    implementation("org.libtorrent4j:libtorrent4j-android-arm64:2.1.0-32")
    implementation("org.libtorrent4j:libtorrent4j-android-arm:2.1.0-32")
    implementation("org.libtorrent4j:libtorrent4j-android-x86_64:2.1.0-32")
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    // NewPipe Extractor (YouTube music/videos without API keys)
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.24.2")
    implementation("org.jsoup:jsoup:1.17.2")

    // WorkManager (download queue)
    implementation("androidx.work:work-runtime-ktx:2.9.1")
}
