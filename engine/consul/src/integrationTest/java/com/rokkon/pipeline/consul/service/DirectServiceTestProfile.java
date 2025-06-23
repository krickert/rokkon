package com.rokkon.pipeline.consul.service;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

/**
 * Test profile for integration tests that need direct service access.
 * This configures the test to expose services via REST endpoints or other mechanisms.
 */
public class DirectServiceTestProfile implements QuarkusTestProfile {
    
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
            // Enable test mode for services
            "test.services.direct-access", "true",
            // Ensure Consul properties are set correctly for test
            "consul.host", "${test.consul.host:localhost}",
            "consul.port", "${test.consul.port:8500}"
        );
    }
    
    @Override
    public String getConfigProfile() {
        return "integration-test";
    }
}