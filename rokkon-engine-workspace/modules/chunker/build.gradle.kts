plugins {
    java
    alias(libs.plugins.quarkus)
    `maven-publish`
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation("io.quarkus:quarkus-jackson")
    implementation("io.quarkus:quarkus-container-image-docker")
    implementation(enforcedPlatform(libs.quarkus.bom))
    implementation(libs.quarkus.grpc)
    implementation("io.quarkus:quarkus-config-yaml")
    implementation("io.quarkus:quarkus-arc")

    // Proto definitions from our proto project
    implementation("com.rokkon.pipeline:proto-definitions:1.0.0-SNAPSHOT")

    // OpenNLP dependencies for chunking and NLP analysis
    implementation("org.apache.opennlp:opennlp-tools:2.3.0")

    // Apache Commons for string utilities
    implementation("org.apache.commons:commons-lang3:3.12.0")

    testImplementation(libs.quarkus.junit5)
    testImplementation(libs.assertj)
    testImplementation("io.rest-assured:rest-assured")

    // Add test-utilities for testing with sample documents
    testImplementation("com.rokkon.pipeline:test-utilities:1.0.0-SNAPSHOT")
}

// Configure Quarkus to use Mutiny for gRPC code generation
quarkus {
    buildForkOptions {
        systemProperty("quarkus.grpc.codegen.type", "mutiny")
    }
}

group = "com.rokkon.pipeline"
version = "1.0.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<Test> {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
}

// Exclude integration tests from the regular test task
tasks.test {
    exclude("**/*IT.class")
}
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

// Extract proto files from jar for local stub generation
val extractProtos = tasks.register<Copy>("extractProtos") {
    from(zipTree(configurations.runtimeClasspath.get().filter { it.name.contains("proto-definitions") }.singleFile))
    include("**/*.proto")
    into("src/main/proto")
    includeEmptyDirs = false
}

tasks.named("quarkusGenerateCode") {
    dependsOn(extractProtos)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "chunker-module"
        }
    }
}
