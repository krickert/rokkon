// Configure plugin management - MUST BE FIRST
pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
    
    // Configure Quarkus plugin version
    val quarkusPluginVersion: String by settings
    plugins {
        id("io.quarkus") version quarkusPluginVersion
    }
}

rootProject.name = "rokkon-pristine"

// Enable build cache
buildCache {
    local {
        isEnabled = true
        directory = file("${rootDir}/.gradle/build-cache")
        removeUnusedEntriesAfterDays = 30
    }
}

// Include all subprojects
include(
    ":bom:base",
    ":bom:library", 
    ":bom:server",
    ":bom:cli",
    ":bom:module",
    ":rokkon-bom",
    ":commons:protobuf",
    ":commons:util",
    ":commons:interface",
    ":commons:data-util",
    ":engine:consul",
    ":engine:dynamic-grpc",
    ":engine:validators",
    ":engine:pipestream",
    ":modules:echo",
    ":modules:chunker",
    ":modules:parser",
    ":modules:embedder",
    ":modules:test-module",
    ":modules:proxy-module",
    ":modules:connectors:filesystem-crawler",
    ":cli:register-module",
    ":cli:seed-engine-consul-config",
    ":testing:util",
    ":testing:server-util",
    ":testing:integration"
)

// Enable Gradle features for better performance
enableFeaturePreview("STABLE_CONFIGURATION_CACHE")
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// Dependency resolution management
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        mavenLocal()
    }
}