// Root build file for multi-module project
// Each module manages its own dependencies and build configuration

plugins {
    idea
}

allprojects {
    group = "com.rokkon.pipeline"
    version = "1.0.0-SNAPSHOT"
}

// Convenience task for dev mode
tasks.register("dev") {
    dependsOn(":engine:pipestream:quarkusDev")
    description = "Start the engine in dev mode with Consul"
    group = "development"
}

// Fix Quarkus task dependencies for all subprojects
subprojects {
    plugins.withId("io.quarkus") {
        tasks.named("compileJava") {
            dependsOn("compileQuarkusGeneratedSourcesJava")
        }
        tasks.named("compileTestJava") {
            dependsOn("compileQuarkusTestGeneratedSourcesJava")
        }
    }
}

// Configure IDEA plugin and download sources
idea {
    project {
        // Set JDK version
        jdkName = "21"
        languageLevel = org.gradle.plugins.ide.idea.model.IdeaLanguageLevel("21")
    }

    module {
        // Exclude build directories
        excludeDirs.addAll(listOf(
            file(".gradle"),
            file("build")
        ))

        // Download sources and javadocs
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}

// Configure all projects to download sources/javadoc
allprojects {
    apply(plugin = "idea")

    idea {
        module {
            isDownloadJavadoc = true
            isDownloadSources = true
        }
    }
}