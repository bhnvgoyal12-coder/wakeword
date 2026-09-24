// Pure Kotlin/JVM: the platform-independent half of the engine. Builds and tests
// without the Android SDK; consumed by :wakeword-android via includeBuild.
plugins {
    kotlin("jvm") version "2.0.21"
}

group = "com.findmyphone.wakeword"
version = "0.1.0"


// JVM 17 bytecode (what Android consumers need) from whatever JDK runs Gradle.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    systemProperty("wakeword.shared", rootDir.resolve("../../shared").absolutePath)
}
