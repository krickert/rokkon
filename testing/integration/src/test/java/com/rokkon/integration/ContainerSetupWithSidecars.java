package com.rokkon.integration;

import com.rokkon.test.containers.SharedNetworkManager;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.consul.ConsulContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Sets up a full integration test environment with:
 * - Consul server
 * - Rokkon Engine with Consul sidecar
 * - Test Module with Consul sidecar
 * 
 * Each application container has its own Consul agent sidecar, allowing
 * them to access Consul via localhost:8500 within their container.
 * 
 * This implementation uses container utilities from testing/util module.
 */
public class ContainerSetupWithSidecars implements QuarkusTestResourceLifecycleManager {

    private static final Logger LOG = Logger.getLogger(ContainerSetupWithSidecars.class.getName());

    private Network network;
    private ConsulContainer consulServer;
    private ConsulContainer consulAgentForEngine;
    private ConsulContainer consulAgentForModule;
    private GenericContainer<?> engineContainer;
    private GenericContainer<?> testModuleContainer;

    @Override
    public Map<String, String> start() {
        Map<String, String> config = new HashMap<>();

        try {
            // Use SharedNetworkManager for consistent network usage
            network = SharedNetworkManager.getNetwork();
            LOG.info("Using shared network: " + network.getId());

            // Start Consul server using the proper ConsulContainer
            consulServer = new ConsulContainer(DockerImageName.parse("hashicorp/consul:latest"))
                    .withNetwork(network)
                    .withNetworkAliases("consul-server")
                    .withCommand("agent", "-dev", "-server", "-bootstrap-expect=1", 
                               "-client=0.0.0.0", "-ui", 
                               "-datacenter=dc1", "-node=consul-server",
                               "-log-level=info")
                    .withLogConsumer(frame -> LOG.info("[CONSUL-SERVER] " + frame.getUtf8String()));
            consulServer.start();
            LOG.info("Consul server started at: http://localhost:" + consulServer.getMappedPort(8500));

            // Wait for Consul to be ready
            Thread.sleep(5000);

            // Start Consul agent sidecar for Engine
            consulAgentForEngine = new ConsulContainer(DockerImageName.parse("hashicorp/consul:latest"))
                    .withNetwork(network)
                    .withNetworkAliases("consul-agent-engine", "engine-consul")
                    .withCommand("agent", "-retry-join=consul-server", 
                               "-client=0.0.0.0",
                               "-datacenter=dc1", "-node=engine-consul",
                               "-log-level=info")
                    .withLogConsumer(frame -> LOG.info("[ENGINE-CONSUL] " + frame.getUtf8String()));
            consulAgentForEngine.start();
            LOG.info("Consul agent for engine started");

            // Start Consul agent sidecar for Module
            consulAgentForModule = new ConsulContainer(DockerImageName.parse("hashicorp/consul:latest"))
                    .withNetwork(network)
                    .withNetworkAliases("consul-agent-module", "test-module-consul", "test-module-consul-agent")
                    .withCommand("agent", "-retry-join=consul-server",
                               "-client=0.0.0.0",
                               "-datacenter=dc1", "-node=test-module-consul-agent", // Match the node name in logs
                               "-log-level=info")
                    .withLogConsumer(frame -> LOG.info("[TEST-MODULE-CONSUL] " + frame.getUtf8String()));
            consulAgentForModule.start();
            LOG.info("Consul agent for module started");

            // Start Engine container
            engineContainer = new GenericContainer<>(DockerImageName.parse("rokkon/rokkon-engine:latest"))
                    .withNetwork(network)
                    .withNetworkAliases("rokkon-engine", "engine") // Add "engine" alias to match what module expects
                    .withExposedPorts(8080, 49000)
                    .withLogConsumer(frame -> LOG.info("[ENGINE] " + frame.getUtf8String()))
                    // Engine connects to its sidecar Consul agent
                    .withEnv("CONSUL_HOST", "engine-consul") // Use the correct Consul host name
                    .withEnv("CONSUL_PORT", "8500")
                    .withEnv("QUARKUS_HTTP_PORT", "8080")
                    .withEnv("QUARKUS_HTTP_HOST", "0.0.0.0")
                    .withEnv("QUARKUS_GRPC_SERVER_PORT", "49000")
                    .withEnv("QUARKUS_GRPC_SERVER_HOST", "0.0.0.0")
                    // For module registration - external host that modules can reach
                    .withEnv("EXTERNAL_MODULE_HOST", "host.docker.internal")
                    // Add startup logging to help debug gRPC server startup
                    .withEnv("QUARKUS_LOG_CATEGORY__IO_QUARKUS_GRPC_RUNTIME_GRPCSERVER__LEVEL", "DEBUG")
                    .withEnv("QUARKUS_LOG_CATEGORY__IO_GRPC__LEVEL", "DEBUG")
                    .dependsOn(consulAgentForEngine);
            engineContainer.start();
            LOG.info("Engine container started");

            // Start Test Module container
            testModuleContainer = new GenericContainer<>(DockerImageName.parse("rokkon/test-module:latest"))
                    .withNetwork(network)
                    .withNetworkAliases("test-module")
                    .withExposedPorts(39095, 49095)
                    .withLogConsumer(frame -> LOG.info("[TEST-MODULE] " + frame.getUtf8String()))
                    // Module connects to its sidecar Consul agent
                    .withEnv("CONSUL_HOST", "test-module-consul") // Use the correct Consul host name
                    .withEnv("CONSUL_PORT", "8500")
                    .withEnv("MODULE_PORT", "49095")
                    .withEnv("QUARKUS_HTTP_PORT", "39095")
                    .withEnv("ENGINE_HOST", "engine") // Use the engine network alias
                    .withEnv("ENGINE_PORT", "49000")
                    // Add ROKKON_ENGINE_ADDRESS to match what module-entrypoint.sh expects
                    .withEnv("ROKKON_ENGINE_ADDRESS", "engine")
                    // Disable self-registration - engine will register it
                    .withEnv("MODULE_REGISTRATION_ENABLED", "false")
                    .dependsOn(consulAgentForModule, engineContainer);
            testModuleContainer.start();
            LOG.info("Test module container started");

            // Configure test properties
            config.put("consul.host", "localhost");
            config.put("consul.port", String.valueOf(consulServer.getMappedPort(8500)));
            config.put("consul.url", "http://localhost:" + consulServer.getMappedPort(8500));
            config.put("rokkon.engine.url", "http://localhost:" + engineContainer.getMappedPort(8080));
            config.put("rokkon.engine.grpc.port", String.valueOf(engineContainer.getMappedPort(49000)));
            config.put("test.module.port", String.valueOf(testModuleContainer.getMappedPort(49095)));
            config.put("test.module.http.port", String.valueOf(testModuleContainer.getMappedPort(39095)));

            // Wait longer for everything to stabilize, especially for the engine's gRPC server to be ready
            LOG.info("Waiting for containers to stabilize and services to be ready...");
            Thread.sleep(20000); // Increase wait time to 20 seconds

            LOG.info("=== Integration Test Environment Ready ===");
            LOG.info("Consul UI: http://localhost:" + consulServer.getMappedPort(8500));
            LOG.info("Engine Dashboard: http://localhost:" + engineContainer.getMappedPort(8080));
            LOG.info("Engine gRPC: localhost:" + engineContainer.getMappedPort(49000));
            LOG.info("Test Module HTTP: http://localhost:" + testModuleContainer.getMappedPort(39095));
            LOG.info("Test Module gRPC: localhost:" + testModuleContainer.getMappedPort(49095));
            LOG.info("========================================");

        } catch (Exception e) {
            LOG.severe("Failed to start integration test environment: " + e.getMessage());
            stop();
            throw new RuntimeException("Failed to start containers", e);
        }

        return config;
    }

    @Override
    public void stop() {
        // Stop containers in reverse order
        stopContainer(testModuleContainer, "Test Module");
        stopContainer(engineContainer, "Engine");
        stopContainer(consulAgentForModule, "Consul Agent Module");
        stopContainer(consulAgentForEngine, "Consul Agent Engine");
        stopContainer(consulServer, "Consul Server");

        if (network != null) {
            try {
                network.close();
                LOG.info("Network closed");
            } catch (Exception e) {
                LOG.severe("Failed to close network: " + e.getMessage());
            }
        }
    }

    private void stopContainer(GenericContainer<?> container, String name) {
        if (container != null && container.isRunning()) {
            try {
                container.stop();
                LOG.info(name + " stopped");
            } catch (Exception e) {
                LOG.severe("Failed to stop " + name + ": " + e.getMessage());
            }
        }
    }
}
