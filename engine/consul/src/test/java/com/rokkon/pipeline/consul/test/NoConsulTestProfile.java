package com.rokkon.pipeline.consul.test;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

/**
 * Test profile that disables Consul for true unit tests.
 * Unit tests should not depend on external services.
 */
public class NoConsulTestProfile implements QuarkusTestProfile {
    
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
            // Disable Consul completely
            "quarkus.consul-config.enabled", "false",
            "quarkus.consul.enabled", "false",
            
            // Set a default cluster name for tests
            "rokkon.cluster.name", "unit-test-cluster",
            
            // Disable health checks that might connect to Consul
            "quarkus.health.extensions.enabled", "false",
            
            // Use test KV prefix for isolation
            "pipeline.consul.kv-prefix", "test",
            
            // Configure test validators
            "test.validators.mode", "empty",
            "test.validators.use-real", "false"
        );
    }
    
    @Override
    public String getConfigProfile() {
        return "test-no-consul";
    }
}