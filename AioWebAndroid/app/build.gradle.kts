plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

import java.util.Properties

// Optional local keystore properties (for local release builds)
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
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "APP_URL", "\"https://v0-ai-image-and-text-generator.vercel.app\"")
    }

    signingConfigs {
        create("release") {
            // Resolution order: env vars (CI) → keystore.properties (local) → null (skip signing)
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
            // Only attach signingConfig if keystore is actually configured
            val rel = signingConfigs.getByName("release")
            if (rel.storeFile != null) {
                signingConfig = rel
            }
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
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
        viewBinding = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.webkit:webkit:1.11.0")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
}
