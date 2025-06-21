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
include("rokkon-protobuf")
include("rokkon-commons")

// Test utilities
include("test-utilities")
include("engine-models")
include("engine-validators")
include("engine-registration")
include("engine-consul")
include("modules:echo")
include("modules:chunker")
include("modules:parser")
include("modules:embedder")
include("modules:test-module")
include("rokkon-engine")
    