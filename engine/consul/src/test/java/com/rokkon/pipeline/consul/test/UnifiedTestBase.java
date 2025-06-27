package com.rokkon.pipeline.consul.test;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/**
 * Base class for tests using the UnifiedTestProfile.
 * Provides convenient methods to configure test features.
 * 
 * Tests can either:
 * 1. Use annotations like @RequiresConsul, @RequiresScheduler
 * 2. Override configureProfile() for programmatic configuration
 * 3. Rely on naming conventions (e.g., *UnitTest gets no-consul config)
 */
public abstract class UnifiedTestBase {
    
    @BeforeEach
    protected void setupTestProfile() {
        // Let UnifiedTestProfile configure based on the test class
        UnifiedTestProfile.configureFor(this.getClass());
        
        // Allow test to override with custom configuration
        UnifiedTestProfile.TestConfiguration customConfig = createCustomConfiguration();
        if (customConfig != null) {
            UnifiedTestProfile.configure(customConfig);
        }
    }
    
    @AfterEach
    protected void cleanupTestProfile() {
        // Clean up configuration after test
        UnifiedTestProfile.reset();
    }
    
    /**
     * Override this method to provide custom configuration.
     * Return null to use default configuration based on annotations/conventions.
     * 
     * Example:
     * <pre>
     * protected UnifiedTestProfile.TestConfiguration createCustomConfiguration() {
     *     return new UnifiedTestProfile.TestConfiguration()
     *         .withConsul()
     *         .withScheduler()
     *         .withConfig("custom.property", "value");
     * }
     * </pre>
     */
    protected UnifiedTestProfile.TestConfiguration createCustomConfiguration() {
        return null;
    }
}