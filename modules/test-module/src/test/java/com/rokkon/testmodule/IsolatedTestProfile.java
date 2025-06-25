package com.rokkon.testmodule;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;
import java.util.UUID;

/**
 * Test profile that ensures each test gets isolated ports
 */
public class IsolatedTestProfile implements QuarkusTestProfile {
    
    private static final String INSTANCE_ID = UUID.randomUUID().toString().substring(0, 8);
    
    @Override
    public Map<String, String> getConfigOverrides() {
        // Each test instance gets unique application name to avoid conflicts
        return Map.of(
            "quarkus.application.name", "test-module-" + INSTANCE_ID,
            // Ensure we're using the test profile
            "quarkus.profile", "test"
        );
    }
}