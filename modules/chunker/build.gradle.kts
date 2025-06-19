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

    // Updated dependencies for new protobuf structure
    implementation("com.rokkon.pipeline:rokkon-protobuf:1.0.0-SNAPSHOT")
    implementation("com.rokkon.pipeline:rokkon-commons:1.0.0-SNAPSHOT")

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

group = "com.rokkon.pipeline"
version = "1.0.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

// Configure Quarkus to use Mutiny for gRPC code generation
quarkus {
    buildForkOptions {
        systemProperty("quarkus.grpc.codegen.type", "mutiny")
    }
}

tasks.withType<Test> {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
    maxHeapSize = "2g"
}

// Exclude integration tests from the regular test task
tasks.test {
    exclude("**/*IT.class")
}
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "chunker-module"
        }
    }
}
