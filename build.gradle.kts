// Root build file for multi-module project
// Each module manages its own dependencies and build configuration

plugins {
    id("io.quarkus") version "3.23.4" apply false
}

allprojects {
    group = "com.rokkon.pipeline"
    version = "1.0.0-SNAPSHOT"
    
    repositories {
        mavenLocal()
        mavenCentral()
    }
}

subprojects {
    // Apply Quarkus plugin to projects that need it
    if (name != "rokkon-protobuf" && name != "test-utilities") {
        apply(plugin = "io.quarkus")
    }
}