// /home/krickert/IdeaProjects/gitlab-pipeines/rokkon-engine-fix-structure/rokkon-engine-workspace/proto-definitions-deployment/build.gradle.kts
plugins {
    java
    // No Quarkus plugin directly applied here for a basic deployment module
}

// Add repositories block if it's not inheriting from a parent build script
// that already defines it. For composite builds, each included build might
// need its own repository definitions or inherit them.
repositories {
    mavenCentral()
    mavenLocal() // Important for resolving locally published artifacts
}

// You'll need to define how to get quarkusPlatformVersion,
// or hardcode it if this module doesn't have access to the root project's properties easily.
// For now, let's assume you'll manage versions consistently.
// val quarkusPlatformVersion: String by project // Or use a direct string

dependencies {
    // Dependency on the runtime module using its Maven coordinates
    // The group, name, and version must match what 'proto-definitions' publishes.
    // From your proto-definitions/build.gradle.kts:
    // group = "com.rokkon.pipeline"
    // version = "1.0.0-SNAPSHOT"
    // publishing.publications.maven.artifactId = "proto-definitions"
    implementation("com.rokkon.pipeline:proto-definitions:1.0.0-SNAPSHOT")

    // These are typical for a deployment module
    // Make sure the version matches your project's Quarkus version
    implementation("io.quarkus:quarkus-core-deployment:3.8.3")
    implementation("io.quarkus:quarkus-grpc-deployment:3.8.3") // If you need gRPC specific build items
}

group = "com.rokkon.pipeline" // Match your runtime module
version = "1.0.0-SNAPSHOT"   // Match your runtime module

java {
    sourceCompatibility = JavaVersion.VERSION_17 // Or your project's standard
    targetCompatibility = JavaVersion.VERSION_17
}