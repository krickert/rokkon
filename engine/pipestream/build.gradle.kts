plugins {
    java
    id("io.quarkus")
}

dependencies {
    // Server BOM provides all standard server dependencies
    implementation(platform(project(":bom:server")))

    // --- gRPC Services ---
    implementation("io.grpc:grpc-services")

    // For reading configuration from Consul's K/V store.
    implementation("io.quarkiverse.config:quarkus-config-consul")

    // --- Stork (Client-side Load Balancing) ---
    implementation("io.quarkus:quarkus-smallrye-stork")
    implementation("io.smallrye.stork:stork-service-discovery-consul")
    implementation("io.smallrye.stork:stork-service-registration-consul")
    // Annotation processor for generating Stork configuration at build time.
    implementation("io.smallrye.stork:stork-configuration-generator")

    // --- Consul Client Support ---
    implementation("io.vertx:vertx-consul-client")
    implementation("io.smallrye.reactive:smallrye-mutiny-vertx-consul-client")

    // --- Observability & Resilience ---
    implementation("io.quarkus:quarkus-smallrye-fault-tolerance")

    // --- Engine Modules ---
    implementation(project(":engine:consul")) // Now includes gRPC registration service
    implementation(project(":engine:validators"))

    // --- Shared Libraries ---
    implementation("com.networknt:json-schema-validator:1.5.7")

    // --- Testing ---
    testImplementation("io.quarkus:quarkus-junit5-mockito")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testImplementation("org.awaitility:awaitility:4.3.0")
    testImplementation("org.testcontainers:consul:1.19.3")
    testImplementation(project(":testing:util"))
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<Test> {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
    useJUnitPlatform()
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

// Custom tasks for development
tasks.register<Exec>("startConsul") {
    group = "development"
    description = "Start Consul in Docker for development"
    commandLine("bash", "-c", """
        if nc -z localhost 8500 2>/dev/null; then
            echo "Consul is already running"
        else
            echo "Starting Consul..."
            docker run -d --name rokkon-consul-dev -p 8500:8500 -p 8600:8600/udp hashicorp/consul:latest agent -dev -ui -client=0.0.0.0
            sleep 5
        fi
    """)
}

tasks.register<Exec>("stopConsul") {
    group = "development"
    description = "Stop Consul Docker container"
    commandLine("bash", "-c", """
        docker stop rokkon-consul-dev 2>/dev/null || true
        docker rm rokkon-consul-dev 2>/dev/null || true
    """)
}

tasks.register("devWithConsul") {
    group = "development"
    description = "Start Quarkus dev mode with Consul"
    dependsOn("startConsul")
    finalizedBy("quarkusDev")
    doLast {
        System.setProperty("quarkus.consul-config.enabled", "true")
    }
}