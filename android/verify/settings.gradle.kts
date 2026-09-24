// Type-checks wakeword-android's Kotlin against android.jar (API 34) + the sherpa-onnx AAR's
// classes.jar WITHOUT the Android SDK/AGP, for environments where dl.google.com is unreachable.
// It proves the code compiles against the real APIs; it does not produce an APK/AAR.
//   ./prepare.sh && gradle compileKotlin
pluginManagement {
    repositories {
        maven("https://maven-central.storage-download.googleapis.com/maven2")
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        maven("https://maven-central.storage-download.googleapis.com/maven2")
        mavenCentral()
    }
}
rootProject.name = "wakeword-android-verify"
includeBuild("../wakeword-core")
