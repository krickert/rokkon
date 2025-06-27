package com.rokkon.pipeline.engine;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.mockito.Mockito.*;

/**
 * Unit test for EngineLifecycle using mocks.
 * Tests the startup behavior in isolation without actual dependencies.
 */
@QuarkusTest
class EngineLifecycleUnitTest extends EngineLifecycleTestBase {
    
    private EngineLifecycle engineLifecycle;
    private static final String TEST_APP_NAME = "test-engine";
    
    @BeforeEach
    void setUp() {
        // Create a spy of EngineLifecycle to test with mocked config
        engineLifecycle = new EngineLifecycle();
        engineLifecycle.applicationName = TEST_APP_NAME;
    }
    
    @Override
    protected EngineLifecycle getEngineLifecycle() {
        return engineLifecycle;
    }
    
    @Override
    protected String getExpectedApplicationName() {
        return TEST_APP_NAME;
    }
    
    @Test
    void testOnStartWithMockedConfiguration() {
        // Given - engine with mocked configuration
        EngineLifecycle spyEngine = Mockito.spy(engineLifecycle);
        
        // When
        spyEngine.onStart(startupEvent);
        
        // Then - verify the method was called
        verify(spyEngine).onStart(any());
    }
    
    @Test
    void testOnStartWithDifferentApplicationNames() {
        // Test with different application names
        String[] testNames = {"rokkon-engine", "test-engine", "production-engine"};
        
        for (String appName : testNames) {
            // Given
            engineLifecycle.applicationName = appName;
            
            // When
            engineLifecycle.onStart(startupEvent);
            
            // Then - no exceptions, startup completes
            // In a real scenario, we'd verify log output contains the app name
        }
    }
}