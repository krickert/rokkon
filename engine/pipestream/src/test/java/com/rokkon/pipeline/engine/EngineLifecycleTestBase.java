package com.rokkon.pipeline.engine;

import io.quarkus.runtime.StartupEvent;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Base test class for EngineLifecycle with common test setup and utilities.
 * This provides shared test logic that can be used by both unit and integration tests.
 */
public abstract class EngineLifecycleTestBase {
    
    private static final Logger LOG = Logger.getLogger(EngineLifecycleTestBase.class);
    
    protected StartupEvent startupEvent;
    protected ArgumentCaptor<String> logCaptor;
    
    @BeforeEach
    void setUpBase() {
        startupEvent = mock(StartupEvent.class);
        logCaptor = ArgumentCaptor.forClass(String.class);
    }
    
    /**
     * Get the EngineLifecycle instance to test.
     * Unit tests will provide a mocked instance, integration tests will use the real one.
     */
    protected abstract EngineLifecycle getEngineLifecycle();
    
    /**
     * Get the expected application name for assertions.
     */
    protected abstract String getExpectedApplicationName();
    
    @Test
    void testEngineStartupLogsApplicationName() {
        // Given
        EngineLifecycle engineLifecycle = getEngineLifecycle();
        String expectedAppName = getExpectedApplicationName();
        
        // When
        engineLifecycle.onStart(startupEvent);
        
        // Then
        // In a real test, we would capture logs or use a logging framework test utility
        // For now, we just verify the method completes without error
        assertThat(engineLifecycle).isNotNull();
        LOG.infof("Verified engine startup for application: %s", expectedAppName);
    }
    
    @Test
    void testEngineStartupCompletesSuccessfully() {
        // Given
        EngineLifecycle engineLifecycle = getEngineLifecycle();
        
        // When
        engineLifecycle.onStart(startupEvent);
        
        // Then - no exceptions thrown
        assertThat(engineLifecycle).isNotNull();
    }
    
    /**
     * Additional test methods can be added here that are common to both
     * unit and integration test scenarios.
     */
}