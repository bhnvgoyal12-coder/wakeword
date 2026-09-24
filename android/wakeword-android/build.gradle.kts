// Android library: sherpa-onnx adapters + foreground service. Needs the Android SDK.
// Models are not committed: run ../fetch_models.sh once (downloads ~20 MB into src/main/assets/wakeword).
plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    namespace = "com.findmyphone.wakeword"
    compileSdk = 34
    defaultConfig {
        minSdk = 26
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") } // drop x86 .so from the AAR (~40 MB)
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    // ONNX models are already compressed; storing them uncompressed lets sherpa mmap them.
    androidResources { noCompress += listOf("onnx") }
}

dependencies {
    implementation("com.findmyphone.wakeword:wakeword-core:0.1.0") // includeBuild("wakeword-core")
    // sherpa-onnx AAR from https://github.com/k2-fsa/sherpa-onnx/releases, installed into
    // ../local-maven by fetch_models.sh (not on Maven Central). `api` so apps get the .so files.
    api("com.k2fsa.sherpa.onnx:sherpa-onnx:1.13.8")
}

val checkModels by tasks.registering {
    val dir = file("src/main/assets/wakeword")
    doLast {
        check(dir.resolve("kws/tokens.txt").exists() && dir.resolve("silero_vad.onnx").exists()) {
            "wake-word models missing: run android/fetch_models.sh"
        }
    }
}
tasks.named("preBuild") { dependsOn(checkModels) }
