plugins { kotlin("jvm") version "2.0.21" }

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

sourceSets.main {
    kotlin.srcDir("../wakeword-android/src/main/kotlin")
    kotlin.srcDir("../demo-app/src/main/kotlin")
}

dependencies {
    implementation("com.findmyphone.wakeword:wakeword-core:0.1.0")
    compileOnly(files("libs/android.jar", "libs/aar/classes.jar"))
}
