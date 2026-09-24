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
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
            // CI emulator only (./gradlew ... -PemulatorAbi): phones never need x86.
            if (project.hasProperty("emulatorAbi")) abiFilters += "x86_64"
        }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("junit:junit:4.13.2")
}
