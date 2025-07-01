// /home/krickert/IdeaProjects/rokkon/rokkon-engine/rokkon-engine-fix-structure-branch/settings.gradle.kts

pluginManagement {
    val quarkusPluginVersion: String by settings
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("io.quarkus") version quarkusPluginVersion
    }
}

// Set the root project name for THIS multi-project build
rootProject.name = "rokkon-pristine"

// Include only the modules we've migrated
// include("rokkon-bom") // Original BOM - removed as all projects migrated

// New BOMs
include("bom:base")
include("bom:cli")
include("bom:module")
include("bom:library")
include("bom:server")

include("cli:register-module")
include("cli:seed-engine-consul-config")
include("commons:protobuf")
include("commons:interface")
include("commons:util")
include("commons:data-util")
include("testing:util")
include("testing:server-util")
include("engine:pipestream")

// Engine submodules
include("engine:consul")
include("engine:validators")
// include("engine:seed-config") - Moved to cli:seed-engine-consul-config
// include("engine:registration") - Merged into engine:consul

// Module subprojects
include("modules:test-module")
// include("modules:proxy-module") // Excluded from build as per requirements
include("modules:chunker")
include("modules:echo")
include("modules:parser")
include("modules:embedder")
include("modules:connectors:filesystem-crawler")

// New Architecture - Mock Engine
//include("rokkon-engine-new:pipestream-mock")


// Ensure dependencyResolutionManagement is present, especially if you plan to use version catalogs later.
// For now, let's assume it's here to prevent potential future issues.
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        mavenLocal()
    }
////     If you plan to use libs.versions.toml here, this is where you'd define it
//     versionCatalogs {
//         create("libs") {
//             from(files("gradle/libs.versions.toml"))
//         }
//     }
}
