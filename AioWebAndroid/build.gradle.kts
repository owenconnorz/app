// Root build.gradle.kts

buildscript {
    repositories {
        google()
        mavenCentral()
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        // ❌ DO NOT ADD JITPACK HERE
    }
}