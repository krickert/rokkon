package com.rokkon.engine.grpc;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.consul.ConsulContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

/**
 * Test resource that starts a Consul container for integration tests.
 * This provides a real Consul instance for testing the full application stack.
 */
public class ConsulTestResource implements QuarkusTestResourceLifecycleManager {
    
    private ConsulContainer consul;
    
    @Override
    public Map<String, String> start() {
        consul = new ConsulContainer(DockerImageName.parse("hashicorp/consul:latest"));
        consul.start();
        
        // Configure the application to use the test Consul instance
        return Map.of(
            "consul.host", consul.getHost(),
            "consul.port", String.valueOf(consul.getFirstMappedPort()),
            "consul.enabled", "true",
            "quarkus.consul-config.enabled", "true",
            "quarkus.consul-config.agent.host", consul.getHost(),
            "quarkus.consul-config.agent.port", String.valueOf(consul.getFirstMappedPort())
        );
    }
    
    @Override
    public void stop() {
        if (consul != null) {
            consul.stop();
        }
    }
}