plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
}

android {
    namespace 'com.aioweb.app'
    compileSdk 34

    defaultConfig {
        applicationId "com.aioweb.app"
        minSdk 21
        targetSdk 34
        versionCode 1
        versionName "1.0"
    }

    buildTypes {
        release {
            minifyEnabled false
        }
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = '17'
    }
}

configurations.all {
    resolutionStrategy {
        // 🔥 FORCE SAME VERSION (fixes your crash)
        force "com.github.lagradost:NiceHttp:0.4.11"
    }
}

dependencies {

    implementation "org.jetbrains.kotlin:kotlin-stdlib:1.9.22"

    // ✅ FIXED NICEHTTP VERSION
    implementation "com.github.lagradost:NiceHttp:0.4.11"

    // (optional but recommended if using CloudStream core)
    implementation "com.github.recloudstream:cloudstream:pre-release"

}