pluginManagement {
    repositories {
        // Google's Maven Central mirror first: repo.maven.apache.org / repo1 rate-limit
        // (HTTP 429) some cloud/CI egress IPs. Same artifacts, same checksums.
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
rootProject.name = "wakeword-core"
