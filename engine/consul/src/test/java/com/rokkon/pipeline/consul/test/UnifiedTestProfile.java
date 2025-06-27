package com.rokkon.pipeline.consul.test;

import com.rokkon.pipeline.consul.service.MockClusterService;
import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Unified test profile that acts as a wrapper for all test configurations.
 * This prevents Quarkus profile conflicts by having a single profile that
 * internally delegates to different configurations based on test needs.
 * 
 * Tests can configure their needs via annotations or static configuration.
 */
public class UnifiedTestProfile implements QuarkusTestProfile {
    
    // Thread-local storage for test-specific configuration
    private static final ThreadLocal<TestConfiguration> testConfig = new ThreadLocal<>();
    
    /**
     * Configuration options for tests
     */
    public static class TestConfiguration {
        // Consul is DISABLED by default for all tests
        public boolean consulEnabled = false;
        public boolean consulConfigEnabled = false;
        public boolean schedulerEnabled = false;
        public boolean healthEnabled = false;
        public boolean realValidators = false;
        public boolean mockClusterService = false;
        public boolean failOnMissingConfig = false;
        public String testClassName = null;
        public Map<String, String> additionalConfig = new HashMap<>();
        
        // Fluent builder methods
        public TestConfiguration withConsul() {
            this.consulEnabled = true;
            return this;
        }
        
        public TestConfiguration withConsulConfig() {
            this.consulConfigEnabled = true;
            return this;
        }
        
        public TestConfiguration withScheduler() {
            this.schedulerEnabled = true;
            return this;
        }
        
        public TestConfiguration withHealth() {
            this.healthEnabled = true;
            return this;
        }
        
        public TestConfiguration withRealValidators() {
            this.realValidators = true;
            return this;
        }
        
        public TestConfiguration withMockClusterService() {
            this.mockClusterService = true;
            return this;
        }
        
        public TestConfiguration withFailOnMissingConfig() {
            this.failOnMissingConfig = true;
            return this;
        }
        
        public TestConfiguration withConfig(String key, String value) {
            this.additionalConfig.put(key, value);
            return this;
        }
    }
    
    /**
     * Configure the profile for a specific test class
     */
    public static void configureFor(Class<?> testClass) {
        TestConfiguration config = createConfigurationFor(testClass);
        testConfig.set(config);
    }
    
    /**
     * Configure the profile with a custom configuration
     */
    public static void configure(TestConfiguration config) {
        testConfig.set(config);
    }
    
    /**
     * Reset configuration (called after test)
     */
    public static void reset() {
        testConfig.remove();
    }
    
    /**
     * Create configuration based on test class annotations or conventions
     */
    private static TestConfiguration createConfigurationFor(Class<?> testClass) {
        TestConfiguration config = new TestConfiguration();
        config.testClassName = testClass.getSimpleName();
        
        // Check for custom annotations (we could create these)
        if (testClass.isAnnotationPresent(RequiresConsul.class)) {
            config.withConsul();
        }
        if (testClass.isAnnotationPresent(RequiresScheduler.class)) {
            config.withScheduler();
        }
        if (testClass.isAnnotationPresent(RequiresMockCluster.class)) {
            config.withMockClusterService();
        }
        
        // Apply naming conventions
        String className = testClass.getSimpleName();
        if (className.contains("ConsulConfig")) {
            if (className.contains("Simple") || className.equals("ConsulConfigSourceSimpleTest")) {
                // SimpleConfigTestProfile behavior
                config.consulConfigEnabled = false;
                config.additionalConfig.put("rokkon.engine.grpc-port", "49000");
                config.additionalConfig.put("rokkon.engine.rest-port", "8080");
                config.additionalConfig.put("rokkon.consul.cleanup.enabled", "true");
                config.additionalConfig.put("rokkon.consul.cleanup.interval", "5m");
                config.additionalConfig.put("rokkon.modules.service-prefix", "module-");
                config.additionalConfig.put("rokkon.default-cluster.name", "default");
            } else if (className.contains("FailOnMissing")) {
                // FailOnMissingProfile behavior
                config.withFailOnMissingConfig();
            }
        }
        
        // Default for most unit tests
        if (className.endsWith("UnitTest") || className.endsWith("Test")) {
            // NoConsulTestProfile behavior - disable all Consul-related features
            config.consulEnabled = false;
            config.consulConfigEnabled = false;
            config.schedulerEnabled = false;
            config.healthEnabled = false;
            config.realValidators = false;
        }
        
        return config;
    }
    
    @Override
    public Map<String, String> getConfigOverrides() {
        TestConfiguration config = testConfig.get();
        if (config == null) {
            // Default configuration if none specified
            config = new TestConfiguration();
        }
        
        Map<String, String> overrides = new HashMap<>();
        
        // ALWAYS disable consul by default - must be explicitly enabled
        overrides.put("quarkus.consul.enabled", "false");
        overrides.put("quarkus.consul-config.enabled", "false");
        overrides.put("quarkus.consul.devservices.enabled", "false");
        overrides.put("quarkus.devservices.enabled", "false");
        
        // Base configuration
        overrides.put("rokkon.cluster.name", "unit-test-cluster");
        
        // Determine KV prefix based on test type to isolate tests
        String kvPrefix = "test";
        if (config.testClassName != null) {
            if (config.testClassName.contains("Service") && !config.testClassName.contains("Mock")) {
                kvPrefix = "service-test/" + config.testClassName;
                // Service unit tests should NOT need consul - they should use mocks
                // Only integration tests (IT suffix) should use real Consul
                if (config.testClassName.endsWith("IT")) {
                    config.consulEnabled = true;
                }
            } else if (config.testClassName.contains("ConsulConfigSource")) {
                kvPrefix = "config-test/" + config.testClassName;
                // ConsulConfigSource tests need special handling
                if (config.testClassName.equals("ConsulConfigSourceSimpleTest")) {
                    // This test needs to be converted to use @QuarkusTestResource
                    config.consulEnabled = false;
                    config.consulConfigEnabled = false;
                }
            } else {
                kvPrefix = "test/" + config.testClassName;
            }
        }
        overrides.put("rokkon.consul.kv-prefix", kvPrefix);
        
        // Apply configuration based on current test needs
        overrides.put("quarkus.consul.enabled", String.valueOf(config.consulEnabled));
        overrides.put("quarkus.consul-config.enabled", String.valueOf(config.consulConfigEnabled));
        overrides.put("quarkus.health.extensions.enabled", String.valueOf(config.healthEnabled));
        overrides.put("quarkus.scheduler.enabled", String.valueOf(config.schedulerEnabled));
        
        // Disable Consul Dev Services for unit tests
        if (!config.consulEnabled) {
            overrides.put("quarkus.consul.devservices.enabled", "false");
            overrides.put("quarkus.devservices.enabled", "false");
        }
        
        // Validators
        overrides.put("test.validators.mode", config.realValidators ? "real" : "empty");
        overrides.put("test.validators.use-real", String.valueOf(config.realValidators));
        
        // Fail on missing config
        if (config.failOnMissingConfig) {
            overrides.put("quarkus.config.fail-on-missing-member", "true");
        }
        
        // Apply any additional configuration
        overrides.putAll(config.additionalConfig);
        
        return overrides;
    }
    
    @Override
    public Set<Class<?>> getEnabledAlternatives() {
        TestConfiguration config = testConfig.get();
        if (config == null) {
            return Set.of();
        }
        
        Set<Class<?>> alternatives = new HashSet<>();
        if (config.mockClusterService) {
            alternatives.add(MockClusterService.class);
        }
        return alternatives;
    }
    
    @Override
    public String getConfigProfile() {
        // Always return the same profile name to avoid conflicts
        return "test-unified";
    }
}