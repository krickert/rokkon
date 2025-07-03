# Dev Mode Working Solution - The Correct Sidecar Pattern

## The Key Insight

The production docker-compose uses the sidecar pattern with `network_mode: "service:xxx"`. For dev mode, we need to replicate this but with Quarkus on the host. The solution is to:

1. Create a proper bridge network
2. Start Consul server in that network
3. Start Consul agent in the same network WITH port mapping to host
4. Use `withExtraHost("host.docker.internal", "host-gateway")` so the agent can reach back to the host

## Working Implementation

### build.gradle.kts
```kotlin
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
        classpath("org.testcontainers:consul:1.20.4")
        classpath("org.testcontainers:testcontainers:1.20.4")
    }
}

tasks.register("startConsulDev") {
    doLast {
        // Check if port 8500 is available
        try {
            ServerSocket(8500).close()
        } catch (e: Exception) {
            throw GradleException("Port 8500 is already in use! Please stop any existing Consul processes.")
        }

        // Create a bridge network
        val network = Network.newNetwork()

        // 1. Start Consul server (exactly like docker-compose)
        val consulServer = ConsulContainer(DockerImageName.parse("hashicorp/consul:1.19.2"))
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
        
        val seedCommands = listOf(
            "consul kv put config/application/pipeline.engine.name pipeline-engine-dev",
            "consul kv put config/application/pipeline.engine.version 1.0.0-DEV",
            "consul kv put config/dev/quarkus.http.port 39001",
            "consul kv put config/dev/quarkus.grpc.server.port 49001",
            "consul kv put config/dev/pipeline.engine.dev-mode true"
        )

        seedCommands.forEach { cmd ->
            consulServer.execInContainer(*cmd.split(" ").toTypedArray())
        }
        logger.lifecycle("Configuration seeded")

        // 3. Start Consul agent (engine sidecar equivalent)
        // This is the critical part - agent must bridge Docker network and host
        val consulAgent = GenericContainer(DockerImageName.parse("hashicorp/consul:1.19.2"))
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

        // Map to standard port 8500 on host
        consulAgent.setPortBindings(listOf("8500:8500"))
        consulAgent.start()

        logger.lifecycle("Consul agent available at localhost:8500")
        logger.lifecycle("Dev mode ready - run your Quarkus application")

        // Store references to prevent GC
        project.extensions.extraProperties["consulServer"] = consulServer
        project.extensions.extraProperties["consulAgent"] = consulAgent
        project.extensions.extraProperties["network"] = network
    }
}

tasks.named("quarkusDev") {
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
```

### application.properties
```properties
# Base configuration
quarkus.consul-config.enabled=true
quarkus.consul-config.agent.host-port=localhost:8500
quarkus.consul-config.properties-value-keys=config/application

# Dev profile additions
%dev.quarkus.consul-config.properties-value-keys=config/application,config/dev
%dev.quarkus.consul-config.fail-on-missing-key=false

# Health checks
%dev.quarkus.smallrye-health.consul.enabled=true
```

## Why This Works

1. **Proper Network Setup**: Both containers are in the same bridge network
2. **Standard Port 8500**: Agent is mapped to localhost:8500 (not 38500)
3. **Host Connectivity**: `withExtraHost("host.docker.internal", "host-gateway")` allows the agent to reach services on the host
4. **No Host Networking**: Uses bridge networking with port mapping (cleaner, more reliable)
5. **Follows Production Pattern**: Mirrors the sidecar pattern from docker-compose.yml

## Key Differences from Failed Attempts

1. **Network Creation**: We create a proper bridge network instead of relying on default
2. **No Host Networking**: Bridge networking with port mapping works better
3. **Extra Host Entry**: The magic line that allows agent to reach back to host
4. **Standard Ports**: Using 8500 instead of 38500 keeps everything standard
5. **Proper Seeding**: Using execInContainer to seed after server starts

## What This Enables

- ✅ Quarkus connects to localhost:8500
- ✅ Agent can join the Consul cluster
- ✅ Agent can health check services on the host
- ✅ Service registration works
- ✅ KV configuration is accessible
- ✅ Mirrors production sidecar pattern

## Usage

```bash
# Start dev mode (automatically starts Consul)
./gradlew :engine:pipestream:quarkusDev

# Optional: Stop Consul containers
./gradlew :engine:pipestream:stopConsulDev
```

## The Magic Line

```kotlin
.withExtraHost("host.docker.internal", "host-gateway")
```

This allows the Consul agent (in Docker) to reach back to the host where Quarkus is running. Without this, health checks would fail because the agent couldn't reach the Quarkus application.