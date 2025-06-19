// Top of proto-definitions/build.gradle.kts
plugins {
    java
    // Apply the plugin by its ID. The version is resolved by this project's settings.gradle.kts (Step 2),
    // which in turn gets the version from this project's gradle.properties (Step 1).
    id("io.quarkus")
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

tasks.withType<Test> {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
}
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}
