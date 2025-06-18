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
 */
public class ConsulTestResource implements QuarkusTestResourceLifecycleManager, DevServicesContext.ContextAware {
    
    private static final String CONSUL_IMAGE = "hashicorp/consul:1.18";
    private ConsulContainer consul;
    private Optional<String> containerNetworkId;
    private static volatile Network sharedNetwork;
    
    @Override
    public void setIntegrationTestContext(DevServicesContext context) {
        containerNetworkId = context.containerNetworkId();
    }
    
    @Override
    public Map<String, String> start() {
        // Get or create the shared network
        sharedNetwork = SharedNetworkManager.getOrCreateNetwork();
        
        // If Quarkus provided a network ID, we could use it here
        // but for now we'll use our shared network for simplicity
        
        consul = new ConsulContainer(DockerImageName.parse(CONSUL_IMAGE))
                .withNetwork(sharedNetwork)
                .withNetworkAliases("consul");
        
        consul.start();
        
        // Return both external (for test) and internal (for containers) access information
        return Map.of(
            "consul.host", consul.getHost(),
            "consul.port", String.valueOf(consul.getMappedPort(8500)),
            "%test.consul.host", consul.getHost(),
            "%test.consul.port", String.valueOf(consul.getMappedPort(8500)),
            // Add container network information
            "consul.container.host", "consul",  // Network alias for container-to-container
            "consul.container.port", "8500",     // Internal port
            "test.network.id", sharedNetwork.getId()
        );
    }
    
    @Override
    public void stop() {
        if (consul != null) {
            consul.stop();
        }
    }
}