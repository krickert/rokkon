package com.rokkon.pipeline.consul.test;

import com.rokkon.test.containers.ModuleContainerResource;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.jboss.logging.Logger;

import java.util.Map;

/**
 * Mock implementation of the TestModuleContainerResource that doesn't actually
 * start a Docker container. This is used to prevent Docker image building
 * while still allowing tests to run.
 */
public class MockTestModuleContainerResource implements QuarkusTestResourceLifecycleManager {
    
    private static final Logger LOG = Logger.getLogger(MockTestModuleContainerResource.class);
    
    @Override
    public Map<String, String> start() {
        LOG.info("Starting mock test module container resource");
        LOG.info("Docker image building is disabled for the engine/consul project");
        
        // Return mock configuration that would normally be provided by the container
        return Map.of(
            "test.module.container.host", "localhost",
            "test.module.container.grpc.port", "9090",
            "test.module.container.http.port", "8080",
            "test.module.container.internal.grpc.port", "9090",
            "test.module.container.internal.http.port", "8080",
            "test.module.container.id", "mock-container-id",
            "test.module.container.name", "mock-container-name",
            "test.module.container.image", "rokkon/test-module:1.0.0-SNAPSHOT",
            "test.module.container.network.alias", "test-module",
            "test.module.container.network.host", "test-module"
        );
    }
    
    @Override
    public void stop() {
        LOG.info("Stopping mock test module container resource");
    }
}