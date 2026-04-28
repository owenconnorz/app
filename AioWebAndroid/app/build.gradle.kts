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
        release { minifyEnabled false }
        debug { minifyEnabled false }
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
        force "com.github.LagradOst:NiceHttp:0.4.11"
    }
}

dependencies {
    implementation "org.jetbrains.kotlin:kotlin-stdlib:1.9.22"

    implementation "com.github.LagradOst:NiceHttp:0.4.11"
    implementation "com.github.recloudstream:cloudstream:4.4.0"

    implementation 'androidx.core:core-ktx:1.12.0'
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.11.0'
}