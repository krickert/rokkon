plugins {
    java
    id("io.quarkus")
}

repositories {
    mavenCentral()
    mavenLocal()
}

val quarkusPlatformGroupId: String by project
val quarkusPlatformArtifactId: String by project
val quarkusPlatformVersion: String by project
dependencies {
    // The Quarkus BOM must be the first entry to manage dependency versions correctly.
    implementation(enforcedPlatform("${quarkusPlatformGroupId}:${quarkusPlatformArtifactId}:${quarkusPlatformVersion}"))

    // --- Quarkus Core & Web ---
    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-vertx")
    implementation("io.quarkus:quarkus-jackson")
    implementation("io.quarkus:quarkus-rest-jackson")
    implementation("io.quarkus:quarkus-rest-client-jackson")
    implementation("io.quarkus:quarkus-hibernate-validator")
    implementation("io.quarkus:quarkus-config-yaml")

    // --- gRPC ---
    implementation("io.quarkus:quarkus-grpc")
    implementation("io.grpc:grpc-services")

    // For reading configuration from Consul's K/V store.
    implementation("io.quarkiverse.config:quarkus-config-consul")

    // --- Stork (Client-side Load Balancing) ---
    implementation("io.quarkus:quarkus-smallrye-stork")
    implementation("io.smallrye.stork:stork-service-discovery-consul")
    // Annotation processor for generating Stork configuration at build time.
    implementation("io.smallrye.stork:stork-configuration-generator")

    // --- Consul Client Support ---
    implementation("io.vertx:vertx-consul-client")
    implementation("io.smallrye.reactive:smallrye-mutiny-vertx-consul-client")

    // --- Observability & Resilience ---
    implementation("io.quarkus:quarkus-smallrye-health")
    implementation("io.quarkus:quarkus-micrometer")
    implementation("io.quarkus:quarkus-smallrye-fault-tolerance")
    implementation("io.quarkus:quarkus-smallrye-openapi")

    // --- Build & Deployment ---
    implementation("io.quarkus:quarkus-container-image-docker")

    // --- Engine Modules ---
    implementation(project(":engine:consul")) // Now includes gRPC registration service
    implementation(project(":engine:validators"))
    implementation(project(":engine:models"))

    // --- Shared Libraries ---
    implementation(project(":rokkon-protobuf"))
    implementation(project(":rokkon-commons"))
    implementation("com.networknt:json-schema-validator:1.5.7")

    // --- Testing ---
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.quarkus:quarkus-junit5-mockito")
    testImplementation("io.rest-assured:rest-assured")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testImplementation("org.awaitility:awaitility:4.3.0")
    testImplementation("org.testcontainers:testcontainers:1.19.8")
    testImplementation("org.testcontainers:junit-jupiter:1.19.8")
    testImplementation("org.testcontainers:consul:1.19.8") {
        exclude(group = "org.apache.commons", module = "commons-compress")
    }
    testImplementation(project(":test-utilities"))
}

group = "com.rokkon.pipeline"
version = "1.0.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<Test> {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
    useJUnitPlatform()
}

// Exclude integration tests from regular test task
tasks.test {
    exclude("**/*IT.class")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

// Quarkus configuration
quarkus {
    buildForkOptions {
        systemProperty("quarkus.grpc.codegen.type", "mutiny")
    }
}

// Integration test source set is already configured by Quarkus
// Just ensure the dependencies are available
configurations {
    getByName("integrationTestImplementation").extendsFrom(configurations.testImplementation.get())
    getByName("integrationTestRuntimeOnly").extendsFrom(configurations.testRuntimeOnly.get())
}
