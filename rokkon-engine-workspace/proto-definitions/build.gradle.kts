// Top of proto-definitions/build.gradle.kts
plugins {
    java
    // Apply the plugin by its ID. The version is now resolved by this project's settings.gradle.kts
    id("io.quarkus.extension.gradle.plugin")
    `maven-publish`
}


repositories {
    mavenCentral()
    mavenLocal()
}

// It's good practice to align Quarkus version management, e.g., using a BOM from libs.versions.toml
// For now, using your existing properties:
val quarkusPlatformGroupId: String by project
val quarkusPlatformArtifactId: String by project
val quarkusPlatformVersion: String by project

dependencies {
    // Enforced platform ensures consistent Quarkus dependency versions
    implementation(enforcedPlatform("${quarkusPlatformGroupId}:${quarkusPlatformArtifactId}:${quarkusPlatformVersion}"))

    // quarkus-core is fundamental for runtime modules of extensions
    implementation("io.quarkus:quarkus-core")
    // quarkus-grpc will trigger code generation from .proto files in src/main/proto
    // and provide runtime support for gRPC.
    implementation("io.quarkus:quarkus-grpc")
    // quarkus-arc is commonly needed for CDI integration, which gRPC services often use.
    implementation("io.quarkus:quarkus-arc")

    testImplementation("io.quarkus:quarkus-junit5")
}

group = "com.rokkon.pipeline"
version = "1.0.0-SNAPSHOT"

java {
    // Consider aligning with Java 21 used in other modules for consistency,
    // though Java 17 will work.
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// This block configures this module as the runtime part of a Quarkus extension.
quarkusExtension {
    // The name and description will be used in the generated META-INF/quarkus-extension.yaml
    // extensionName.set("Rokkon Proto Definitions") // You can set metadata here
    // description.set("Provides pre-compiled gRPC Mutiny stubs and proto definitions for Rokkon.")

    // You will need to create a deployment module, e.g., 'proto-definitions-deployment'.
    // This tells Quarkus where to find the build-time logic for this runtime artifact.
    // The exact path depends on your module structure in settings.gradle.kts.
    // If 'proto-definitions-deployment' is a sibling project/directory:
    deploymentModule.set("proto-definitions-deployment")
    // If it's a subproject like 'proto-definitions:proto-definitions-deployment':
    // deploymentModule.set(":proto-definitions-deployment")
}

tasks.withType<Test> {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
}
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

// Include .proto files in the JAR. This is useful for consumers who might
// want to inspect them or for tools that require them.
tasks.named<org.gradle.api.tasks.Copy>("processResources") {
    from("src/main/proto") {
        into("META-INF/protos") // Standard practice to put them in META-INF/protos
        include("**/*.proto")
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            // This artifactId will be the runtime JAR.
            artifactId = "proto-definitions"
        }
    }
}

// IMPORTANT FOR MUTINY STUB GENERATION:
// To ensure this 'proto-definitions' module generates Mutiny gRPC stubs during its build:
// 1. Create the file: src/main/resources/application.properties
//    (within this 'proto-definitions' module)
// 2. Add the following line to that application.properties file:
//    quarkus.grpc.codegen.type=mutiny
//
// This configuration will instruct the Quarkus gRPC code generator to produce
// Mutiny-compatible stubs when it processes the .proto files in this module.
// These generated Java files will then be compiled and included in this module's JAR.