import org.testcontainers.consul.ConsulContainer
import org.testcontainers.utility.DockerImageName
import org.testcontainers.containers.Network
import org.testcontainers.containers.GenericContainer
import java.net.ServerSocket

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.testcontainers:consul:1.21.3")
        classpath("org.testcontainers:testcontainers:1.21.3")
    }
}

plugins {
    java
    id("io.quarkus")
}

dependencies {
    // Server BOM provides all standard server dependencies
    implementation(platform(project(":bom:server")))
    
    // Docker Client for Dev Mode only
    quarkusDev("io.quarkiverse.docker:quarkus-docker-client:0.0.4")
    
    // Testcontainers for Dev Mode Consul startup
    implementation("org.testcontainers:testcontainers:1.21.3")
    implementation("org.testcontainers:consul:1.21.3")
    
    // Quarkus deployment for accessing DevModeMain
    quarkusDev("io.quarkus:quarkus-core-deployment")

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
    
    // --- Caching for gRPC channels ---
    implementation("io.quarkus:quarkus-cache")
    
    // --- Engine Modules ---
    implementation(project(":engine:consul")) // Now includes gRPC registration service
    implementation(project(":engine:validators"))
    implementation(project(":engine:dynamic-grpc")) // Dynamic gRPC client discovery

    // --- Shared Libraries ---
    implementation("com.networknt:json-schema-validator:1.5.7")

    // --- Testing ---
    testImplementation("io.quarkus:quarkus-junit5-mockito")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testImplementation("org.awaitility:awaitility:4.3.0")
    testImplementation("org.testcontainers:consul:1.21.3")
    testImplementation(project(":testing:util"))
    testImplementation(project(":testing:server-util"))
    
    // --- Integration Testing ---
    integrationTestImplementation("com.orbitz.consul:consul-client:1.5.3")
    integrationTestImplementation("io.quarkus:quarkus-junit5-mockito")
    integrationTestImplementation("org.assertj:assertj-core:3.26.3")
    integrationTestImplementation("org.awaitility:awaitility:4.3.0")
    integrationTestImplementation("org.testcontainers:consul:1.21.3")
    integrationTestImplementation(project(":testing:util"))
    integrationTestImplementation(project(":engine:dynamic-grpc"))
    integrationTestImplementation("io.vertx:vertx-consul-client")
    integrationTestImplementation("io.smallrye.reactive:smallrye-mutiny-vertx-consul-client")
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


// Task to start Consul before dev mode
tasks.register("startConsulDev") {
    doLast {
        // Check if port 8501 is available
        try {
            ServerSocket(8501).close()
        } catch (e: Exception) {
            throw GradleException("Port 8501 is already in use! Please stop any existing Consul processes.")
        }

        // Create a bridge network
        val network = Network.newNetwork()

        // 1. Start Consul server (exactly like docker-compose)
        val consulServer = ConsulContainer(DockerImageName.parse("hashicorp/consul:1.21"))
            .withCommand(
                "agent", "-server", "-ui",
                "-client=0.0.0.0",
                "-bind=0.0.0.0",
                "-bootstrap-expect=1",
                "-dev"
            )
            .withNetwork(network)
            .withNetworkAliases("consul-server")
            .withEnv("CONSUL_BIND_INTERFACE", "eth0")

        consulServer.start()
        logger.lifecycle("Consul server started")

        // 2. Seed configuration (replaces seeder service)
        Thread.sleep(2000) // Wait for server to be ready
        
        // Seed with proper YAML format that Quarkus expects
        val applicationConfig = """
pipeline:
  engine:
    name: pipeline-engine-dev
    version: 1.0.0-DEV
""".trimIndent()

        val devConfig = """
quarkus:
  http:
    port: 39001
  grpc:
    server:
      port: 49001
pipeline:
  engine:
    dev-mode: true
""".trimIndent()

        consulServer.execInContainer("consul", "kv", "put", "config/application", applicationConfig)
        consulServer.execInContainer("consul", "kv", "put", "config/dev", devConfig)
        consulServer.execInContainer("consul", "kv", "put", "config/krick-local", devConfig)
        logger.lifecycle("Configuration seeded")

        // 3. Start Consul agent (engine sidecar equivalent)
        // This is the critical part - agent must bridge Docker network and host
        val consulAgent = GenericContainer(DockerImageName.parse("hashicorp/consul:1.21"))
            .withCommand(
                "agent",
                "-node=engine-sidecar-dev",
                "-client=0.0.0.0",
                "-bind=0.0.0.0",
                "-retry-join=consul-server",
                "-log-level=info"
            )
            .withNetwork(network)
            .withNetworkAliases("consul-agent-engine")
            .withEnv("CONSUL_BIND_INTERFACE", "eth0")
            .withExposedPorts(8500)
            // This is the magic - allows agent to reach host
            .withExtraHost("host.docker.internal", "host-gateway")

        // Map to port 8501 on host (engine sidecar port)
        consulAgent.setPortBindings(listOf("8501:8500"))
        consulAgent.start()

        logger.lifecycle("Consul agent available at localhost:8501")
        logger.lifecycle("Dev mode ready - run your Quarkus application")

        // Store references to prevent GC
        project.extensions.extraProperties["consulServer"] = consulServer
        project.extensions.extraProperties["consulAgent"] = consulAgent
        project.extensions.extraProperties["network"] = network
    }
}

// Make quarkusDev depend on our Consul starter
tasks.named<io.quarkus.gradle.tasks.QuarkusDev>("quarkusDev") {
    dependsOn("startConsulDev")
}

// Optional: Stop containers after dev mode
tasks.register("stopConsulDev") {
    doLast {
        try {
            (project.extensions.extraProperties["consulAgent"] as? GenericContainer<*>)?.stop()
            (project.extensions.extraProperties["consulServer"] as? ConsulContainer)?.stop()
            (project.extensions.extraProperties["network"] as? Network)?.close()
        } catch (e: Exception) {
            logger.debug("Error stopping containers: ${e.message}")
        }
    }
}

// Alternative dev task with better naming
tasks.register("dev") {
    group = "development"
    description = "Start Quarkus dev mode with Consul"
    dependsOn("quarkusDev")
}

