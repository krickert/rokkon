package com.rokkon.pipeline.engine.ui;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Test profile that enables dev mode features for UI testing
 */
public class DevModeTestProfile implements QuarkusTestProfile {
    
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
            "quarkus.profile", "dev",
            // Ensure dev services don't interfere
            "quarkus.devservices.enabled", "false",
            // Make sure we're using the test ports
            "quarkus.http.test-port", "39001",
            // Enable dev mode features
            "quarkus.live-reload.enabled", "false"
        );
    }
    
    @Override
    public String getConfigProfile() {
        return "dev";
    }
}