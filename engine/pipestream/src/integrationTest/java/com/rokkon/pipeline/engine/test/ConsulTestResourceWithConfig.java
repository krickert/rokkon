package com.rokkon.pipeline.engine.test;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.consul.ConsulContainer;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.util.Map;

/**
 * Test resource that starts Consul container and seeds it with required configuration.
 * This enables the engine to start properly with its Consul configuration dependency.
 */
public class ConsulTestResourceWithConfig implements QuarkusTestResourceLifecycleManager {
    
    private static final Logger LOG = LoggerFactory.getLogger(ConsulTestResourceWithConfig.class);
    
    private ConsulContainer consulContainer;
    
    @Override
    public Map<String, String> start() {
        LOG.info("Starting Consul container with seeded configuration...");
        
        // Start Consul container with explicit port binding to avoid conflicts
        consulContainer = new ConsulContainer(DockerImageName.parse("hashicorp/consul:latest"));
        
        consulContainer.start();
        
        // Wait for Consul to be ready
        String consulUrl = "http://" + consulContainer.getHost() + ":" + consulContainer.getFirstMappedPort();
        LOG.info("Consul started at: {}", consulUrl);
        
        // Seed configuration using consul CLI in the container
        seedConfiguration();
        
        // Return configuration for Quarkus
        return Map.of(
            "quarkus.consul-config.agent.host-port", 
            consulContainer.getHost() + ":" + consulContainer.getFirstMappedPort(),
            "consul.host", consulContainer.getHost(),
            "consul.port", String.valueOf(consulContainer.getFirstMappedPort())
        );
    }
    
    @Override
    public void stop() {
        if (consulContainer != null) {
            LOG.info("Stopping Consul container");
            consulContainer.stop();
        }
    }
    
    private void seedConfiguration() {
        try {
            // Create basic application configuration
            String configData = """
                quarkus.application.name=rokkon-engine-test
                quarkus.grpc.server.port=0
                quarkus.http.port=0
                quarkus.consul-config.enabled=true
                quarkus.consul.devservices.enabled=false
                """;
            
            // Use consul kv put command to seed the configuration
            var result = consulContainer.execInContainer(
                "consul", "kv", "put", "config/application", configData
            );
            
            if (result.getExitCode() == 0) {
                LOG.info("Successfully seeded application configuration to Consul");
            } else {
                LOG.error("Failed to seed configuration: {}", result.getStderr());
            }
            
            // Also seed dev profile config
            String devConfigData = """
                quarkus.log.level=INFO
                quarkus.log.category."com.rokkon".level=DEBUG
                """;
            
            consulContainer.execInContainer(
                "consul", "kv", "put", "config/dev", devConfigData
            );
            
        } catch (IOException | InterruptedException e) {
            LOG.error("Error seeding Consul configuration", e);
            throw new RuntimeException("Failed to seed Consul configuration", e);
        }
    }
}