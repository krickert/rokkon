package com.rokkon.pipeline.seed.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for the ConfigurationModel class.
 * Tests the methods for converting to and from different formats.
 */
public class ConfigurationModelTest {

    private ConfigurationModel testConfig;

    @BeforeEach
    void setUp() {
        // Create a test configuration
        Map<String, String> configData = new HashMap<>();
        configData.put("rokkon.engine.name", "test-engine");
        configData.put("rokkon.engine.grpc-port", "9000");
        configData.put("rokkon.engine.rest-port", "9001");
        configData.put("rokkon.consul.cleanup.enabled", "true");
        configData.put("rokkon.consul.cleanup.interval", "PT10M");
        configData.put("rokkon.modules.service-prefix", "test-module-");
        configData.put("rokkon.default-cluster.name", "test-cluster");
        
        testConfig = new ConfigurationModel(configData);
    }

    @Test
    void testToFormattedString() {
        // Get formatted string
        String formatted = testConfig.toFormattedString();
        
        // Verify it contains expected content
        assertThat(formatted).contains("rokkon.engine.name=test-engine");
        assertThat(formatted).contains("rokkon.engine.grpc-port=9000");
        assertThat(formatted).contains("# rokkon settings");
    }

    @Test
    void testToJsonString() throws JsonProcessingException {
        // Get JSON string
        String json = testConfig.toJsonString();
        
        // Verify it contains expected content
        assertThat(json).contains("\"engine\" : {");
        assertThat(json).contains("\"name\" : \"test-engine\"");
        assertThat(json).contains("\"grpc-port\" : \"9000\"");
    }

    @Test
    void testToYamlString() throws JsonProcessingException {
        // Get YAML string
        String yaml = testConfig.toYamlString();
        
        // Verify it contains expected content
        assertThat(yaml).contains("rokkon:");
        assertThat(yaml).contains("  engine:");
        assertThat(yaml).contains("    name: \"test-engine\"");
        assertThat(yaml).contains("    grpc-port: \"9000\"");
    }

    @Test
    void testGetAndSet() {
        // Test get
        assertThat(testConfig.get("rokkon.engine.name")).isEqualTo("test-engine");
        
        // Test set
        testConfig.set("rokkon.engine.name", "updated-engine");
        assertThat(testConfig.get("rokkon.engine.name")).isEqualTo("updated-engine");
        
        // Test set with invalid key
        assertThrows(IllegalArgumentException.class, () -> {
            testConfig.set("invalid key with spaces", "value");
        });
    }

    @Test
    void testRemove() {
        // Test remove
        testConfig.remove("rokkon.engine.name");
        assertThat(testConfig.get("rokkon.engine.name")).isNull();
    }

    @Test
    void testGetAll() {
        // Test getAll
        Map<String, String> all = testConfig.getAll();
        assertThat(all).containsEntry("rokkon.engine.name", "test-engine");
        assertThat(all).containsEntry("rokkon.engine.grpc-port", "9000");
        
        // Verify that modifying the returned map doesn't affect the original
        all.put("new.key", "new-value");
        assertThat(testConfig.get("new.key")).isNull();
    }

    @Test
    void testToProperties() {
        // Test toProperties
        java.util.Properties props = testConfig.toProperties();
        assertThat(props.getProperty("rokkon.engine.name")).isEqualTo("test-engine");
        assertThat(props.getProperty("rokkon.engine.grpc-port")).isEqualTo("9000");
    }

    @Test
    void testEqualsAndHashCode() {
        // Create an identical config
        Map<String, String> sameData = new HashMap<>();
        sameData.put("rokkon.engine.name", "test-engine");
        sameData.put("rokkon.engine.grpc-port", "9000");
        sameData.put("rokkon.engine.rest-port", "9001");
        sameData.put("rokkon.consul.cleanup.enabled", "true");
        sameData.put("rokkon.consul.cleanup.interval", "PT10M");
        sameData.put("rokkon.modules.service-prefix", "test-module-");
        sameData.put("rokkon.default-cluster.name", "test-cluster");
        ConfigurationModel sameConfig = new ConfigurationModel(sameData);
        
        // Create a different config
        Map<String, String> differentData = new HashMap<>();
        differentData.put("rokkon.engine.name", "different-engine");
        ConfigurationModel differentConfig = new ConfigurationModel(differentData);
        
        // Test equals
        assertThat(testConfig).isEqualTo(sameConfig);
        assertThat(testConfig).isNotEqualTo(differentConfig);
        
        // Test hashCode
        assertThat(testConfig.hashCode()).isEqualTo(sameConfig.hashCode());
        assertThat(testConfig.hashCode()).isNotEqualTo(differentConfig.hashCode());
    }
}