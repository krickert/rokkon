package com.rokkon.pipeline.consul.config;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

/**
 * Test profile that disables Consul config to test local configuration values
 */
public class SimpleConfigTestProfile implements QuarkusTestProfile {
    
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
            // Disable Consul config to test local values
            "quarkus.consul-config.enabled", "false",
            // Ensure we use test values
            "rokkon.engine.grpc-port", "49000",
            "rokkon.engine.rest-port", "8080"
        );
    }
    
    @Override
    public String getConfigProfile() {
        return "test";
    }
}