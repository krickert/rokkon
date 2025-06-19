// /home/krickert/IdeaProjects/gitlab-pipeines/rokkon-engine-fix-structure/rokkon-engine-workspace/settings.gradle.kts
pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "rokkon-engine-workspace"

// Core protobuf and common libraries
includeBuild("rokkon-protobuf")
includeBuild("rokkon-commons")

// Test utilities
includeBuild("test-utilities")
includeBuild("engine-models")
includeBuild("engine-validators")
includeBuild("engine-registration")
includeBuild("engine-consul")
includeBuild("modules/echo")
includeBuild("modules/chunker")
includeBuild("modules/parser")
includeBuild("modules/embedder")
includeBuild("modules/test-module")
includeBuild("rokkon-engine")
    