import org.testcontainers.consul.ConsulContainer
import org.testcontainers.utility.DockerImageName
import org.testcontainers.containers.Network
import org.testcontainers.containers.GenericContainer
import org.testcontainers.DockerClientFactory
import java.net.ServerSocket
import java.net.URL
import java.net.URI
import java.net.HttpURLConnection

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
        var consulServerContainer: ConsulContainer? = null
        var consulAgentContainer: GenericContainer<*>? = null
        var network: Network? = null
        
        // Check if Consul agent is already running on port 8501
        val isConsulAgentRunning = try {
            val url = URI("http://localhost:8501/v1/agent/self").toURL()
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 1000
            connection.readTimeout = 1000
            val responseCode = connection.responseCode
            connection.disconnect()
            responseCode == 200
        } catch (e: Exception) {
            false
        }
        
        if (isConsulAgentRunning) {
            logger.lifecycle("✓ Consul agent already running on port 8501, reusing existing instance")
            
            // Check if configuration exists
            val configExists = try {
                val checkUrl = URI("http://localhost:8501/v1/kv/config/application").toURL()
                val conn = checkUrl.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 1000
                val exists = conn.responseCode == 200
                conn.disconnect()
                exists
            } catch (e: Exception) {
                false
            }
            
            if (!configExists) {
                logger.lifecycle("⚠ Configuration missing, attempting to seed...")
                // Try to find server container to seed config
                val docker = DockerClientFactory.instance().client()
                val consulContainers = docker.listContainersCmd()
                    .withShowAll(false)
                    .exec()
                    .filter { it.image?.contains("consul") == true && it.state == "running" }
                
                val serverContainer = consulContainers.find { 
                    it.command?.contains("-server") == true 
                }
                
                if (serverContainer != null) {
                    // Seed using docker exec
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
                    
                    docker.execCreateCmd(serverContainer.id)
                        .withCmd("consul", "kv", "put", "config/application", applicationConfig)
                        .exec()
                    docker.execCreateCmd(serverContainer.id)
                        .withCmd("consul", "kv", "put", "config/dev", devConfig)
                        .exec()
                    logger.lifecycle("✓ Configuration seeded successfully")
                }
            } else {
                logger.lifecycle("✓ Configuration already exists")
            }
        } else {
            // No agent running, need to start Consul infrastructure
            logger.lifecycle("Starting Consul infrastructure...")
            
            // Check if we can find existing Consul server container
            val docker = DockerClientFactory.instance().client()
            val consulContainers = docker.listContainersCmd()
                .withShowAll(false)
                .exec()
                .filter { container -> 
                    container.image?.contains("consul") == true && 
                    container.state == "running"
                }
            
            val existingServer = consulContainers.find { container ->
                container.command?.contains("-server") == true
            }
            
            if (existingServer != null) {
                logger.lifecycle("✓ Found existing Consul server container: ${existingServer.id.take(12)}")
                // Get the network from existing server
                val networkId = existingServer.networkSettings?.networks?.keys?.firstOrNull()
                if (networkId != null) {
                    // Just reuse the existing network by creating a new one
                    // Testcontainers will handle joining the right network
                    network = Network.newNetwork()
                }
            } else {
                // Create new network and server
                logger.lifecycle("→ Starting new Consul server...")
                network = Network.newNetwork()
                
                consulServerContainer = ConsulContainer(DockerImageName.parse("hashicorp/consul:1.21"))
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
                
                consulServerContainer.start()
                logger.lifecycle("✓ Consul server started")
                
                // Seed configuration
                logger.lifecycle("→ Seeding configuration...")
                Thread.sleep(2000) // Wait for server to be ready
                
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

                consulServerContainer.execInContainer("consul", "kv", "put", "config/application", applicationConfig)
                consulServerContainer.execInContainer("consul", "kv", "put", "config/dev", devConfig)
                logger.lifecycle("✓ Configuration seeded")
            }
            
            // Start agent
            logger.lifecycle("→ Starting Consul agent on port 8501...")
            consulAgentContainer = GenericContainer(DockerImageName.parse("hashicorp/consul:1.21"))
                .withCommand(
                    "agent",
                    "-node=engine-sidecar-dev",
                    "-client=0.0.0.0",
                    "-bind=0.0.0.0",
                    "-retry-join=consul-server",
                    "-log-level=info"
                )
                .withNetwork(network!!)
                .withNetworkAliases("consul-agent-engine")
                .withEnv("CONSUL_BIND_INTERFACE", "eth0")
                .withExposedPorts(8500)
                .withExtraHost("host.docker.internal", "host-gateway")
            
            consulAgentContainer.setPortBindings(listOf("8501:8500"))
            consulAgentContainer.start()
            logger.lifecycle("✓ Consul agent available at localhost:8501")
        }
        
        logger.lifecycle("✓ Dev mode ready - starting Quarkus application")
        
        // Store references if we created new containers
        consulServerContainer?.let { 
            project.extensions.extraProperties["consulServer"] = it 
        }
        consulAgentContainer?.let { 
            project.extensions.extraProperties["consulAgent"] = it 
        }
        network?.let { 
            project.extensions.extraProperties["network"] = it 
        }
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

