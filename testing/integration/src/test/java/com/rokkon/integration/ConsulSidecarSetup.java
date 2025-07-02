package com.rokkon.integration;

import com.rokkon.test.containers.SharedNetworkManager;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.consul.ConsulContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Simplified setup that uses the custom sidecar container resources.
 * This provides a cleaner implementation of the Consul sidecar pattern.
 */
public class ConsulSidecarSetup implements QuarkusTestResourceLifecycleManager {
    
    private static final Logger LOG = Logger.getLogger(ConsulSidecarSetup.class.getName());
    
    private Network network;
    private ConsulContainer consulServer;
    private SidecarEngineContainerResource engineResource;
    private SidecarModuleContainerResource testModuleResource;
    
    @Override
    public Map<String, String> start() {
        Map<String, String> config = new HashMap<>();
        
        try {
            // Use SharedNetworkManager for consistent network usage
            network = SharedNetworkManager.getNetwork();
            LOG.info("Using shared network: " + network.getId());
            
            // Start Consul server with proper network configuration
            consulServer = new ConsulContainer(DockerImageName.parse("hashicorp/consul:latest"))
                    .withNetwork(network)
                    .withNetworkAliases("consul-server")
                    .withExposedPorts(8300, 8301, 8302, 8500, 8600) // Expose all Consul ports
                    .withEnv("CONSUL_BIND_INTERFACE", "eth0") // Bind to the Docker network interface
                    .withEnv("CONSUL_CLIENT_INTERFACE", "eth0")
                    .withCommand("agent", "-dev", "-server", "-bootstrap-expect=1", 
                               "-client=0.0.0.0", "-ui", 
                               "-datacenter=dc1", "-node=consul-server",
                               "-log-level=info")
                    .withLogConsumer(frame -> LOG.info("[CONSUL-SERVER] " + frame.getUtf8String()));
            consulServer.start();
            LOG.info("Consul server started at: http://localhost:" + consulServer.getMappedPort(8500));
            
            // Wait for Consul to be fully ready - check the leader endpoint
            LOG.info("Waiting for Consul server to elect leader...");
            Thread.sleep(3000); // Initial wait for startup
            
            // Check if Consul has a leader
            int attempts = 0;
            while (attempts < 30) {
                try {
                    java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
                    java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                            .uri(java.net.URI.create("http://localhost:" + consulServer.getMappedPort(8500) + "/v1/status/leader"))
                            .build();
                    java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                    
                    if (response.statusCode() == 200 && !response.body().isEmpty() && !response.body().equals("\"\"")) {
                        LOG.info("Consul leader elected: " + response.body());
                        break;
                    }
                } catch (Exception e) {
                    // Ignore and retry
                }
                attempts++;
                Thread.sleep(1000);
            }
            
            if (attempts >= 30) {
                throw new RuntimeException("Consul server failed to elect leader after 30 seconds");
            }
            
            // Get the project version
            String projectVersion = System.getProperty("project.version", "1.0.0-SNAPSHOT");
            LOG.info("Using project version: " + projectVersion);
            
            // Start Engine with its Consul sidecar
            engineResource = new SidecarEngineContainerResource("rokkon/rokkon-engine:" + projectVersion, "consul-server");
            Map<String, String> engineConfig = engineResource.start();
            config.putAll(engineConfig);
            
            // Start Test Module with its Consul sidecar
            testModuleResource = new SidecarModuleContainerResource("rokkon/test-module:" + projectVersion, "test-module", "consul-server");
            Map<String, String> moduleConfig = testModuleResource.start();
            config.putAll(moduleConfig);
            
            // Add Consul server configuration
            config.put("consul.host", "localhost");
            config.put("consul.port", String.valueOf(consulServer.getMappedPort(8500)));
            config.put("consul.url", "http://localhost:" + consulServer.getMappedPort(8500));
            
            // For @QuarkusIntegrationTest, we need to set system properties
            System.setProperty("consul.host", "localhost");
            System.setProperty("consul.port", String.valueOf(consulServer.getMappedPort(8500)));
            System.setProperty("consul.url", "http://localhost:" + consulServer.getMappedPort(8500));
            System.setProperty("rokkon.engine.url", "http://localhost:" + engineConfig.get("rokkon.engine.http.port"));
            System.setProperty("rokkon.engine.grpc.port", engineConfig.get("rokkon.engine.grpc.port"));
            System.setProperty("test.module.port", moduleConfig.get("test.module.container.grpc.port"));
            System.setProperty("test.module.http.port", moduleConfig.get("test.module.container.http.port"));
            
            // Wait for everything to stabilize
            Thread.sleep(3000);
            
            LOG.info("=== Integration Test Environment Ready ===");
            LOG.info("Consul UI: http://localhost:" + consulServer.getMappedPort(8500));
            LOG.info("Engine Dashboard: http://localhost:" + engineConfig.get("rokkon.engine.http.port"));
            LOG.info("Test Module HTTP: http://localhost:" + moduleConfig.get("test.module.container.http.port"));
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
        // Stop in reverse order
        if (testModuleResource != null) {
            testModuleResource.stop();
        }
        if (engineResource != null) {
            engineResource.stop();
        }
        if (consulServer != null && consulServer.isRunning()) {
            consulServer.stop();
            LOG.info("Consul server stopped");
        }
        // SharedNetworkManager will handle network cleanup
    }
}