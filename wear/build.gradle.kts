// v2.18 E3: the Wear OS tile (calories left + day streak). Same applicationId as the phone app so the
// Data Layer pairs them; the phone publishes "/li/today" (ui/social/WearSync.kt), the tile reads it.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.sohum.bandlog.wear"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.sohum.bandlog"
        minSdk = 30
        targetSdk = 34
        versionCode = 27
        versionName = "2.16"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.wear.tiles:tiles:1.4.1")
    implementation("androidx.wear.protolayout:protolayout:1.2.1")
    implementation("androidx.wear.protolayout:protolayout-expression:1.2.1")
    implementation("com.google.android.gms:play-services-wearable:18.2.0")
    implementation("com.google.guava:guava:33.3.1-android")
}
