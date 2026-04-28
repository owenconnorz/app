plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.23"
}

android {
    namespace = "com.example.aioweb"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.aioweb"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.11"
    }
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")

    // Jetpack Compose
    implementation(platform("androidx.compose:compose-bom:2024.05.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // ViewModel + coroutines (fixes viewModelScope)
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")

    // Coil (fixes AsyncImage, ImageRequest, ImageLoader)
    implementation("io.coil-kt:coil-compose:2.6.0")

    // OkHttp (fixes okhttp3 references)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Jsoup (fixes Document, Jsoup references)
    implementation("org.jsoup:jsoup:1.17.2")

    // kotlinx.serialization (fixes serialization references)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Jackson (fixes ObjectMapper in MainActivity)
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.0")

    // Media3 (fixes media3, Player references)
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    implementation("androidx.media3:media3-common:1.3.1")

    // Palette (fixes AlbumArtThemeBus)
    implementation("androidx.palette:palette-ktx:1.0.0")
}