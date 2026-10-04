@file:Suppress("UnstableApiUsage")

val bundleID = "com.example.lmsnowplaying"

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
}

android {
    namespace = bundleID
    compileSdk = 34

    defaultConfig {

        applicationId = bundleID
        minSdk = 23
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        manifestPlaceholders["redirectSchemeName"] =  bundleID
        manifestPlaceholders["redirectHostName"] =  "com.example.callback"

    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            //isDebuggable = true
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
        getByName("debug") {
            isDebuggable = true
        }

    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {


    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.androidx.splashScreen)

    implementation(libs.bundles.androidx.compose.bom)
    implementation(libs.bundles.androidx.tv.compose)

    implementation(libs.bundles.okhttp)
    implementation(libs.bundles.retrofit)

    implementation(libs.bundles.material3)

    implementation(libs.bundles.coil)
    implementation("androidx.palette:palette-ktx:1.0.0")

    implementation(libs.runtime.livedata)

    implementation(libs.kotlin.stdlib)

    implementation(libs.jellyfin.sdk)
    implementation(libs.slf4j.nop)

    implementation(libs.datastore.preferences)

}
