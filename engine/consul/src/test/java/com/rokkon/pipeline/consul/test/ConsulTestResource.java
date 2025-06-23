package com.rokkon.pipeline.consul.test;

import io.quarkus.test.common.DevServicesContext;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.consul.ConsulContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;
import com.rokkon.test.containers.SharedNetworkManager;

import java.util.Map;
import java.util.Optional;

/**
 * Testcontainers resource that starts Consul for integration tests.
 * Implements DevServicesContext.ContextAware to participate in Quarkus network sharing.
 * Uses a singleton pattern to share a single Consul container across all tests.
 */
public class ConsulTestResource implements QuarkusTestResourceLifecycleManager, DevServicesContext.ContextAware {
    
    private static final String CONSUL_IMAGE = "hashicorp/consul:1.18";
    private static volatile ConsulContainer sharedConsul;
    private static volatile boolean containerStarted = false;
    private Optional<String> containerNetworkId;
    private static volatile Network sharedNetwork;
    
    @Override
    public void setIntegrationTestContext(DevServicesContext context) {
        containerNetworkId = context.containerNetworkId();
    }
    
    @Override
    public Map<String, String> start() {
        // Use double-checked locking to ensure only one container is started
        if (!containerStarted) {
            synchronized (ConsulTestResource.class) {
                if (!containerStarted) {
                    // Get or create the shared network
                    sharedNetwork = SharedNetworkManager.getOrCreateNetwork();
                    
                    // Create and configure the container with reuse enabled
                    sharedConsul = new ConsulContainer(DockerImageName.parse(CONSUL_IMAGE))
                            .withNetwork(sharedNetwork)
                            .withNetworkAliases("consul")
                            .withReuse(true);
                    
                    sharedConsul.start();
                    containerStarted = true;
                }
            }
        }
        
        // Return both external (for test) and internal (for containers) access information
        return Map.of(
            "consul.host", sharedConsul.getHost(),
            "consul.port", String.valueOf(sharedConsul.getMappedPort(8500)),
            "%test.consul.host", sharedConsul.getHost(),
            "%test.consul.port", String.valueOf(sharedConsul.getMappedPort(8500)),
            // Add container network information
            "consul.container.host", "consul",  // Network alias for container-to-container
            "consul.container.port", "8500",     // Internal port
            "test.network.id", sharedNetwork.getId()
        );
    }
    
    @Override
    public void stop() {
        // Don't stop the shared container - let it be reused across test runs
        // The container will be cleaned up when the JVM exits or when
        // Testcontainers Ryuk reaper removes it after inactivity
    }
}