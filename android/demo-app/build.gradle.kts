// Standalone test app for the wake-word engine. Install on a phone to try it:
//   ../fetch_models.sh && ../gradlew :demo-app:installDebug
plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "com.findmyphone.wakeword.demo"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.findmyphone.wakeword.demo"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    androidResources { noCompress += listOf("onnx") }
}

dependencies {
    implementation(project(":wakeword-android"))
    implementation("com.findmyphone.wakeword:wakeword-core:0.1.0")
}
