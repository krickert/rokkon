plugins {
    java
    id("io.quarkus")
    `maven-publish`
    idea
}



dependencies {
    // Library BOM provides all standard library dependencies
    implementation(platform(project(":bom:library")))

    // Quarkus extensions for Consul functionality
    implementation("io.quarkiverse.config:quarkus-config-consul") // For configuration properties
    implementation("io.quarkus:quarkus-hibernate-validator")
    implementation("io.quarkus:quarkus-smallrye-openapi")
    implementation("io.quarkus:quarkus-smallrye-health") // For health endpoints
    implementation("io.quarkus:quarkus-rest-jackson")
    implementation("io.quarkus:quarkus-config-yaml")
    implementation("io.quarkus:quarkus-rest")
    implementation("io.quarkus:quarkus-scheduler")
    implementation("io.quarkus:quarkus-grpc") // For gRPC services
    implementation("io.quarkus:quarkus-smallrye-context-propagation") // Required for Mutiny context propagation

    // Stork for Consul service discovery
    implementation("io.smallrye.stork:stork-service-discovery-consul")

    // Vertx Consul client for service registration
    implementation("io.smallrye.reactive:smallrye-mutiny-vertx-consul-client")

    // YAML processing for config persistence
    implementation("org.yaml:snakeyaml")

    // Our modules - rokkon-commons and rokkon-protobuf come from BOM
    implementation(project(":commons:interface"))
    implementation(project(":commons:util"))
    implementation(project(":engine:validators"))
    implementation("com.networknt:json-schema-validator")

    // Test dependencies (versions from BOM where available)
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.quarkus:quarkus-junit5-mockito") // For @InjectMock support
    testImplementation("io.rest-assured:rest-assured")
    testImplementation("org.assertj:assertj-core")
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:consul")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.awaitility:awaitility:4.2.0")
    testImplementation(project(":testing:util")) // Provides quarkus-docker-client and other test utilities

    // Integration tests are handled by Quarkus automatically
    // It automatically gets testImplementation dependencies, so no need to add them twice
}

group = "com.rokkon.pipeline"
version = "1.0.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    withSourcesJar()
    withJavadocJar()
}

// Fix duplicate entries in sources jar
tasks.withType<Jar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// Fix sourcesJar dependency on generated sources
tasks.named<Jar>("sourcesJar") {
    dependsOn("compileQuarkusGeneratedSourcesJava")
}

// Configure Quarkus to use Mutiny for gRPC code generation
quarkus {
    buildForkOptions {
        systemProperty("quarkus.grpc.codegen.type", "mutiny")
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-parameters", "--enable-preview"))
    options.release = 21
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

// Suppress the enforced platform validation
tasks.withType<GenerateModuleMetadata> {
    suppressedValidationErrors.add("enforced-platform")
}

// Configure idea to download sources and javadocs
idea {
    module {
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}
