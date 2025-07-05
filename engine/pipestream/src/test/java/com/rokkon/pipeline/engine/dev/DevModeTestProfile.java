package com.rokkon.pipeline.engine.dev;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Test profile that activates dev mode for testing dev-specific components
 */
public class DevModeTestProfile implements QuarkusTestProfile {
    
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
            "quarkus.profile", "dev",
            "pipeline.dev.auto-start", "false" // Don't auto-start in tests
        );
    }
    
    @Override
    public String getConfigProfile() {
        return "dev";
    }
}