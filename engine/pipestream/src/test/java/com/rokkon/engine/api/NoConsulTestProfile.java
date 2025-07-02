package com.rokkon.engine.api;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

/**
 * Test profile that disables Consul and related services for true unit testing.
 * This allows tests to run without any external dependencies.
 */
public class NoConsulTestProfile implements QuarkusTestProfile {
    
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
            // Disable Consul configuration
            "quarkus.consul-config.enabled", "false",
            
            // Disable Consul client
            "quarkus.consul.enabled", "false",
            
            // Set a default cluster name for tests
            "rokkon.cluster.name", "unit-test-cluster",
            
            // Disable any health checks that might try to connect to Consul
            "quarkus.health.extensions.enabled", "false",
            
            // Configure test validators - use empty validators by default for unit tests
            "test.validators.mode", "empty",
            "test.validators.use-real", "false",
            
            // Use mock implementations where possible
            "quarkus.arc.unremovable-types", "com.rokkon.pipeline.commons.model.GlobalModuleRegistryService,com.rokkon.pipeline.config.service.PipelineDefinitionService,com.rokkon.pipeline.validation.CompositeValidator"
        );
    }
    
    @Override
    public String getConfigProfile() {
        return "test-no-consul";
    }
}