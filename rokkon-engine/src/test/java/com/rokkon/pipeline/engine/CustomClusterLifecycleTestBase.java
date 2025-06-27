package com.rokkon.pipeline.engine;

import com.rokkon.pipeline.config.service.ClusterService;
import io.quarkus.runtime.StartupEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Base test class for testing custom cluster lifecycle scenarios.
 * Provides shared test logic for both unit and integration tests.
 */
public abstract class CustomClusterLifecycleTestBase {
    
    protected StartupEvent startupEvent;
    protected String customClusterName;
    
    @BeforeEach
    void setUpBase() {
        startupEvent = mock(StartupEvent.class);
        customClusterName = getCustomClusterName();
    }
    
    /**
     * Get the EngineLifecycle instance to test.
     */
    protected abstract EngineLifecycle getEngineLifecycle();
    
    /**
     * Get the ClusterService instance (mock for unit tests, real for integration).
     */
    protected abstract ClusterService getClusterService();
    
    /**
     * Get the custom cluster name configured for the test.
     */
    protected abstract String getCustomClusterName();
    
    @Test
    void testCustomClusterConfiguration() {
        // Verify custom cluster name is properly configured
        assertThat(customClusterName).isNotNull();
        assertThat(customClusterName).isNotEqualTo("default-cluster");
    }
    
    @Test
    void testEngineStartsWithCustomCluster() {
        // Given
        EngineLifecycle engineLifecycle = getEngineLifecycle();
        
        // When
        engineLifecycle.onStart(startupEvent);
        
        // Then - no exceptions thrown
        assertThat(engineLifecycle).isNotNull();
    }
}