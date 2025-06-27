package com.rokkon.engine.api;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

/**
 * Test profile for tests that need Consul running.
 * This is typically used for integration tests that test the full stack.
 */
public class WithConsulTestProfile implements QuarkusTestProfile {
    
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
            // Ensure Consul is enabled
            "quarkus.consul-config.enabled", "true",
            "quarkus.consul.enabled", "true",
            
            // Use test-specific Consul paths
            "quarkus.consul-config.properties-value-keys[0]", "test/config/application",
            "quarkus.consul-config.properties-value-keys[1]", "test/${rokkon.cluster.name}/config/application",
            
            // Test cluster configuration
            "rokkon.cluster.name", "integration-test-cluster",
            "rokkon.cluster.auto-create", "true"
        );
    }
    
    @Override
    public String getConfigProfile() {
        return "test-with-consul";
    }
}