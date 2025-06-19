// /home/krickert/IdeaProjects/gitlab-pipeines/rokkon-engine-fix-structure/rokkon-engine-workspace/settings.gradle.kts
pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "rokkon-engine-workspace"

includeBuild("proto-definitions") // This build uses its OWN settings.gradle.kts
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
    