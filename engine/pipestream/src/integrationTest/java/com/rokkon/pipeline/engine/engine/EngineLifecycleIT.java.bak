package com.rokkon.pipeline.engine;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for EngineLifecycle.
 * Tests the actual startup behavior with real dependencies.
 */
@QuarkusIntegrationTest
class EngineLifecycleIT extends EngineLifecycleTestBase {

    @Inject
    EngineLifecycle engineLifecycle;
    
    @ConfigProperty(name = "quarkus.application.name")
    String applicationName;
    
    @Override
    protected EngineLifecycle getEngineLifecycle() {
        return engineLifecycle;
    }
    
    @Override
    protected String getExpectedApplicationName() {
        return applicationName;
    }

    @Test
    void testEngineLifecycleInjection() {
        // Verify that the engine lifecycle bean is properly injected
        assertThat(engineLifecycle).isNotNull();
        assertThat(engineLifecycle.applicationName).isNotNull();
        assertThat(engineLifecycle.applicationName).isEqualTo(applicationName);
    }
    
    @Test
    void testRealStartupEvent() {
        // In integration test, we test with the real startup event flow
        // The engine should already be started by Quarkus
        assertThat(engineLifecycle).isNotNull();
        
        // We can manually trigger onStart to verify it doesn't throw exceptions
        engineLifecycle.onStart(startupEvent);
    }
}
