pluginManagement {
    val quarkusPluginVersion: String by settings
    val quarkusPluginId: String by settings
    repositories {
        mavenCentral()
        gradlePluginPortal() // CRITICAL: This is where the plugin is hosted
        mavenLocal()
    }
    plugins {
        // This uses the properties to tell Gradle about the plugin
        id(quarkusPluginId) version quarkusPluginVersion
    }
}
rootProject.name="proto-definitions"
    