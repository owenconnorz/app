plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
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

        versionCode = (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1)
        versionName = "1.0.${System.getenv("GITHUB_RUN_NUMBER") ?: "0"}"

        buildConfigField(
            "String",
            "DEFAULT_BACKEND_URL",
            "\"https://aio-android-port.preview.emergentagent.com\""
        )

        buildConfigField(
            "String",
            "TMDB_API_KEY",
            "\"8265bd1679663a7ea12ac168da84d2e8\""
        )

        val ghRepository = System.getenv("GITHUB_REPOSITORY") ?: "owenconnorz/AioWeb"
        val ghOwner = ghRepository.substringBefore('/')
        val ghName = ghRepository.substringAfter('/')

        buildConfigField("String", "GITHUB_OWNER", "\"$ghOwner\"")
        buildConfigField("String", "GITHUB_REPO", "\"$ghName\"")
    }

    signingConfigs {
        val fallbackKs = rootProject.file("streamcloud-debug.jks")

        create("release") {
            val ksPathEnv = System.getenv("KEYSTORE_PATH")
                ?: keystoreProps["storeFile"]?.toString()

            val ksPath = ksPathEnv?.let { file(it) }?.takeIf { it.exists() } ?: fallbackKs
            val realKey = ksPathEnv != null && ksPath.absolutePath == ksPathEnv

            storeFile = ksPath
            storePassword = if (realKey)
                System.getenv("KEYSTORE_PASSWORD") ?: keystoreProps["storePassword"]?.toString()
            else "streamcloud"

            keyAlias = if (realKey)
                System.getenv("KEY_ALIAS") ?: keystoreProps["keyAlias"]?.toString()
            else "streamcloud-debug"

            keyPassword = if (realKey)
                System.getenv("KEY_PASSWORD") ?: keystoreProps["keyPassword"]?.toString()
            else "streamcloud"

            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }

        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            signingConfig = signingConfigs.getByName("release")
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

    // Core
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.9.2")

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.0")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Coroutines + Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Coil (images / posters)
    implementation("io.coil-kt:coil-compose:2.7.0")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Media3 (player)
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")
    implementation("androidx.media3:media3-common:1.4.1")
    implementation("androidx.media3:media3-datasource:1.4.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.4.1")

    // Torrent
    implementation("org.libtorrent4j:libtorrent4j:2.1.0-32")
    implementation("org.libtorrent4j:libtorrent4j-android-arm64:2.1.0-32")
    implementation("org.libtorrent4j:libtorrent4j-android-arm:2.1.0-32")
    implementation("org.libtorrent4j:libtorrent4j-android-x86_64:2.1.0-32")
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    // NewPipe (YouTube extraction)
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.0") {
        exclude(group = "org.mozilla", module = "rhino")
    }

    implementation("org.jsoup:jsoup:1.17.2")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Room (database)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // UI helpers
    implementation("androidx.palette:palette-ktx:1.0.0")

    // JS engine (needed for plugins)
    implementation("io.github.dokar3:quickjs-kt-android:1.0.0-alpha09")

    // JSON
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")
    implementation("com.google.code.gson:gson:2.10.1")

    // Testing
    implementation("org.mockito:mockito-core:5.7.0")
}