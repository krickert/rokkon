// /home/krickert/IdeaProjects/gitlab-pipeines/rokkon-engine-fix-structure/rokkon-engine-workspace/settings.gradle.kts
pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "rokkon-engine-workspace"

// Core libraries (at root level)
include("rokkon-protobuf")
include("rokkon-commons")
include("test-utilities")

// Main engine application (at root level)
include("rokkon-engine")

// Engine components
include("engine:models")
include("engine:validators")
include("engine:registration")
include("engine:consul")

// Modules
include("modules:echo")
include("modules:chunker")
include("modules:parser")
include("modules:embedder")
include("modules:test-module")
    