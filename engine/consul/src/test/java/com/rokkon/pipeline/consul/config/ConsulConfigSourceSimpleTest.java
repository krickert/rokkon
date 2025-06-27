package com.rokkon.pipeline.consul.config;

import com.rokkon.pipeline.consul.test.UnifiedTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Simple tests for configuration properties.
 * These test that our configuration structure is properly defined
 * and that default values are loaded from application.yml.
 */
@QuarkusTest
@TestProfile(UnifiedTestProfile.class)
@DisplayName("Configuration Property Tests")
@Tag("config-tests") // Tag for running separately if needed
class ConsulConfigSourceSimpleTest {
    
    // Test individual properties to verify they're loaded
    @ConfigProperty(name = "rokkon.engine.grpc-port")
    int grpcPort;
    
    @ConfigProperty(name = "rokkon.engine.rest-port")
    int restPort;
    
    @ConfigProperty(name = "rokkon.consul.cleanup.enabled")
    boolean cleanupEnabled;
    
    @ConfigProperty(name = "rokkon.consul.cleanup.interval")
    Duration cleanupInterval;
    
    @ConfigProperty(name = "rokkon.modules.service-prefix")
    String servicePrefix;
    
    @ConfigProperty(name = "rokkon.default-cluster.name")
    String defaultClusterName;
    
    @Test
    @DisplayName("Should load engine configuration properties")
    void testEngineProperties() {
        assertThat(grpcPort).isEqualTo(49000);
        assertThat(restPort).isEqualTo(8080);
    }
    
    @Test
    @DisplayName("Should load consul cleanup configuration")
    void testConsulCleanupProperties() {
        assertThat(cleanupEnabled).isTrue();
        assertThat(cleanupInterval).isEqualTo(Duration.ofMinutes(5));
    }
    
    @Test
    @DisplayName("Should load module configuration")
    void testModuleProperties() {
        assertThat(servicePrefix).isEqualTo("module-");
    }
    
    @Test
    @DisplayName("Should load default cluster configuration")
    void testDefaultClusterProperties() {
        assertThat(defaultClusterName).isEqualTo("default");
    }
}