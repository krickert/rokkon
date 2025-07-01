package com.rokkon.engine.api;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.HashMap;
import java.util.Map;

/**
 * Test profile that uses real validators but still disables Consul.
 * This is useful for component tests that want to test validation logic
 * without external dependencies.
 */
public class RealValidatorsTestProfile implements QuarkusTestProfile {
    
    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> config = new HashMap<>();
        
        // Disable Consul
        config.put("quarkus.consul-config.enabled", "false");
        config.put("quarkus.consul.enabled", "false");
        
        // Basic test configuration
        config.put("rokkon.cluster.name", "component-test-cluster");
        config.put("quarkus.health.extensions.enabled", "false");
        
        // Use REAL validators for component testing
        config.put("test.validators.mode", "real");
        config.put("test.validators.use-real", "true");
        
        return config;
    }
    
    @Override
    public String getConfigProfile() {
        return "test-real-validators";
    }
}