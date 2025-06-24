// /home/krickert/IdeaProjects/rokkon/rokkon-engine/rokkon-engine-fix-structure-branch/settings.gradle.kts

pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
}

// Set the root project name for THIS multi-project build
rootProject.name = "rokkon-pristine"

// Include only the modules we've migrated
include("rokkon-bom")
include("rokkon-commons")
include("rokkon-protobuf")
include("test-utilities")
include("rokkon-engine")

// Engine submodules
include("engine:consul")
include("engine:models")
include("engine:validators")
include("engine:seed-config")
include("engine:cli-register")
// include("engine:registration") - Merged into engine:consul

// Module subprojects
include("modules:test-module")
include("modules:proxy-module")
include("modules:chunker")
include("modules:echo")
include("modules:parser")
include("modules:embedder")


// Ensure dependencyResolutionManagement is present, especially if you plan to use version catalogs later.
// For now, let's assume it's here to prevent potential future issues.
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        mavenLocal()
    }
    // If you plan to use libs.versions.toml here, this is where you'd define it
    // versionCatalogs {
    //     create("libs") {
    //         from(files("gradle/libs.versions.toml"))
    //     }
    // }
}
