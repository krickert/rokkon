pluginManagement {
    val quarkusPluginVersion: String by settings
    val quarkusPluginId: String by settings
    repositories {
        mavenCentral()
        gradlePluginPortal()
        mavenLocal()
    }
    plugins {
        id(quarkusPluginId) version quarkusPluginVersion
    }
}
rootProject.name="rokkon-engine"

// Include modules
include("modules:echo-service")
include("modules:tika-parser")
include("modules:chunker")
include("modules:embedder")
