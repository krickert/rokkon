package com.rokkon.pipeline.consul;

import com.rokkon.pipeline.consul.test.ConsulTestResource;
import com.rokkon.pipeline.consul.test.IsolatedConsulKvTestBase;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test demonstrating how to use IsolatedConsulKvTestBase
 * for consul-config tests with isolated namespaces.
 */
@QuarkusTest
@QuarkusTestResource(ConsulTestResource.class)
@io.quarkus.test.junit.TestProfile(ConsulConfigIsolatedIT.IsolatedConfigProfile.class)
public class ConsulConfigIsolatedIT extends IsolatedConsulKvTestBase {
    
    @Test
    void testIsolatedConfigWrites() {
        // Write configuration to isolated namespace
        String yamlConfig = """
            rokkon:
              test:
                isolated: true
                environment: test
                features:
                  - feature1
                  - feature2
            """;
        
        writeValue("config/application", yamlConfig);
        
        // Read it back
        String readConfig = readValue("config/application");
        assertThat(readConfig).isEqualTo(yamlConfig);
        
        System.out.println("✓ Isolated config written to: " + getFullKey("config/application"));
    }
    
    @Test
    void testMultipleConfigFiles() {
        // Write multiple config files
        writeValue("config/database", """
            database:
              url: jdbc:postgresql://localhost:5432/test
              pool-size: 10
            """);
        
        writeValue("config/messaging", """
            messaging:
              kafka:
                bootstrap-servers: localhost:9092
                topic: test-topic
            """);
        
        writeValue("config/security", """
            security:
              jwt:
                issuer: test-issuer
                secret: test-secret
            """);
        
        // Verify all exist
        assertThat(keyExists("config/database")).isTrue();
        assertThat(keyExists("config/messaging")).isTrue();
        assertThat(keyExists("config/security")).isTrue();
        
        System.out.println("✓ Multiple config files written successfully");
    }
    
    @Test
    void testConfigUpdateScenario() {
        String configKey = "config/service";
        
        // Initial config
        String v1Config = """
            service:
              version: 1.0.0
              enabled: false
            """;
        writeValue(configKey, v1Config);
        
        // Update config
        String v2Config = """
            service:
              version: 2.0.0
              enabled: true
              new-feature: active
            """;
        writeValue(configKey, v2Config);
        
        // Verify update
        String currentConfig = readValue(configKey);
        assertThat(currentConfig).isEqualTo(v2Config);
        
        System.out.println("✓ Config update successful");
    }
    
    @Test
    void testConfigDeletion() {
        String configKey = "config/temporary";
        
        // Write config
        writeValue(configKey, "temporary: config");
        assertThat(keyExists(configKey)).isTrue();
        
        // Delete config
        deleteValue(configKey);
        assertThat(keyExists(configKey)).isFalse();
        
        System.out.println("✓ Config deletion successful");
    }
    
    @Override
    protected void onSetup() {
        System.out.println("Setting up ConsulConfigIsolatedIT with namespace: " + testNamespace);
        
        // Pre-populate some default config for all tests
        writeValue("config/defaults", """
            defaults:
              timeout: 30s
              retry-count: 3
            """);
    }
    
    @Override
    protected void onCleanup() {
        System.out.println("Cleaning up ConsulConfigIsolatedIT");
        // Any test-specific cleanup can go here
    }
    
    /**
     * Profile that configures consul-config to use isolated namespaces.
     * This prevents tests from interfering with each other.
     */
    public static class IsolatedConfigProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            // Generate unique namespace for this test run
            String testId = UUID.randomUUID().toString().substring(0, 8);
            return Map.of(
                // Each test class gets unique config paths
                "quarkus.consul-config.properties-value-keys[0]", "test/ConsulConfigIsolatedIT/" + testId + "/config/application",
                "quarkus.consul-config.properties-value-keys[1]", "test/ConsulConfigIsolatedIT/" + testId + "/config/test",
                "quarkus.consul-config.fail-on-missing-key", "false",
                "quarkus.consul-config.watch", "false",
                // Disable validation for test
                "smallrye.config.mapping.validate-unknown", "false"
            );
        }
    }
}