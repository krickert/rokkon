package com.rokkon.pipeline.engine.grpc;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

/**
 * Test profile for integration tests with Consul.
 * Enables Consul but uses invalid default connection settings so it doesn't
 * connect at startup. The test will update the connection to the test container.
 */
public class IntegrationTestProfile implements QuarkusTestProfile {
    
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
            // Enable Consul but with invalid host so it doesn't connect at startup
            "consul.enabled", "true",
            "consul.host", "invalid-consul-host",
            "consul.port", "8500",
            // Disable Consul config
            "quarkus.consul-config.enabled", "false"
        );
    }
}