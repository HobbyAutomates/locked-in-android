import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Machine-local config (gitignored): Supabase URL + anon key and the web API base for meal parsing.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) FileInputStream(f).use { load(it) }
}
val supabaseUrl: String = localProps.getProperty("SUPABASE_URL", "")
val supabaseAnonKey: String = localProps.getProperty("SUPABASE_ANON_KEY", "")
val apiBase: String = localProps.getProperty("API_BASE", "")
// Emails that see Profile → Admin (opens the web /admin panel, which does its own server-side check).
val adminEmails: String = localProps.getProperty("ADMIN_EMAILS", "")
// v2.10 beta usage events (bandlog.app_events). Default on for the closed beta; set
// BETA_ANALYTICS=false in local.properties to build an APK that sends nothing.
val betaAnalytics: Boolean = localProps.getProperty("BETA_ANALYTICS", "true").trim().lowercase() !in setOf("false", "0", "off", "no")

android {
    namespace = "com.sohum.bandlog"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.sohum.bandlog"
        minSdk = 31
        targetSdk = 34
        versionCode = 24
        versionName = "2.13"
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
        // The Railway web app; /api/parse-meal holds the Anthropic key server-side.
        buildConfigField("String", "API_BASE", "\"$apiBase\"")
        buildConfigField("String", "ADMIN_EMAILS", "\"$adminEmails\"")
        buildConfigField("boolean", "BETA_ANALYTICS", "$betaAnalytics")
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Personal sideload: sign release with the debug key so it installs over the existing app.
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)

    // v2.13 nutrition: live camera preview + capture in the scan frame (CameraX 1.3 = compileSdk 34),
    // and JVM unit tests for the pure maths (util/Adaptive, DietModes, WhatToEat, Micros).
    val cameraX = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraX")
    implementation("androidx.camera:camera-camera2:$cameraX")
    implementation("androidx.camera:camera-lifecycle:$cameraX")
    implementation("androidx.camera:camera-view:$cameraX")
    // ProcessCameraProvider.getInstance returns a Guava ListenableFuture; ML Kit pins the empty
    // listenablefuture stub, so the class comes from Guava itself (R8 strips the rest in release).
    implementation("com.google.guava:guava:33.3.1-android")
    testImplementation("junit:junit:4.13.2")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // HTTP client for Supabase REST/Auth and the meal-parse API
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Health Connect: steps + calories in, band sessions out (covers Google Fit and Samsung Health)
    implementation("androidx.health.connect:connect-client:1.1.0-alpha07")

    // On-device OCR for the label scanner. The Play-Services variant downloads the model on
    // demand instead of baking it into the APK (same com.google.mlkit API as the Reminders app).
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.1")
    // On-device EAN/UPC reading for the barcode scanner (same Play-Services delivery as the OCR model).
    implementation("com.google.android.gms:play-services-mlkit-barcode-scanning:18.3.1")

    // v2.13 platform: inbox polling every 15 min (no FCM project) + on app open.
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // v2.13 platform: plain JVM unit tests (Pro, Epley, muscle map, routines, protein nudge).
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
