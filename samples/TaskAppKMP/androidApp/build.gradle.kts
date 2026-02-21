plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.compose")
}

android {
    namespace = "io.stateproof.sample"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.stateproof.sample"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.8.0-alpha02"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":composeApp"))
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("io.github.fshamim:stateproof-core:0.8.0-alpha02")
}
