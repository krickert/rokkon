// Root build file for multi-module project
// Each module manages its own dependencies and build configuration

plugins {
    id("io.quarkus") version "3.23.4" apply false
    idea
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
    // Apply Quarkus plugin only to actual Quarkus projects
    val quarkusProjects = setOf(
        "rokkon-engine",
        "rokkon-commons",
        "models", "validators", "registration", "consul", // engine subprojects
        "echo", "chunker", "parser", "embedder", "test-module" // module subprojects
    )
    
    if (name in quarkusProjects) {
        apply(plugin = "io.quarkus")
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