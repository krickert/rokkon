package com.rokkon.pipeline.config.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Base test class for SchemaReference that contains all test logic.
 * Critical for schema registry integration and version management.
 */
public abstract class SchemaReferenceTestBase {

    protected abstract ObjectMapper getObjectMapper();

    @Test
    public void testSerialization() throws Exception {
        SchemaReference ref = new SchemaReference("test-schema", 2);
        String json = getObjectMapper().writeValueAsString(ref);
        
        assertThat(json).contains("\"subject\":\"test-schema\"");
        assertThat(json).contains("\"version\":2");
    }

    @Test
    public void testDeserialization() throws Exception {
        String json = "{\"subject\":\"test-schema\",\"version\":2}";
        SchemaReference ref = getObjectMapper().readValue(json, SchemaReference.class);
        
        assertThat(ref.subject()).isEqualTo("test-schema");
        assertThat(ref.version()).isEqualTo(2);
    }

    @Test
    public void testToIdentifier() {
        SchemaReference ref = new SchemaReference("my-schema", 5);
        assertThat(ref.toIdentifier()).isEqualTo("my-schema:5");
        
        // Test with complex subject names
        SchemaReference complexRef = new SchemaReference("com.rokkon.chunker-config-v2", 10);
        assertThat(complexRef.toIdentifier()).isEqualTo("com.rokkon.chunker-config-v2:10");
    }

    @Test
    public void testValidation() {
        // Valid cases
        assertDoesNotThrow(() -> new SchemaReference("valid-subject", 1));
        assertDoesNotThrow(() -> new SchemaReference("another-subject", 10));
        assertDoesNotThrow(() -> new SchemaReference("com.example.schema", 999));
        
        // Invalid subject cases
        assertThatThrownBy(() -> new SchemaReference(null, 1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("subject cannot be null or blank");
            
        assertThatThrownBy(() -> new SchemaReference("", 1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("subject cannot be null or blank");
            
        assertThatThrownBy(() -> new SchemaReference("   ", 1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("subject cannot be null or blank");
        
        // Invalid version cases
        assertThatThrownBy(() -> new SchemaReference("valid", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("version cannot be null and must be positive");
            
        assertThatThrownBy(() -> new SchemaReference("valid", 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("version cannot be null and must be positive");
            
        assertThatThrownBy(() -> new SchemaReference("valid", -1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("version cannot be null and must be positive");
    }

    @Test
    public void testRoundTrip() throws Exception {
        SchemaReference original = new SchemaReference("round-trip-test", 3);
        String json = getObjectMapper().writeValueAsString(original);
        SchemaReference deserialized = getObjectMapper().readValue(json, SchemaReference.class);
        
        assertThat(deserialized.subject()).isEqualTo(original.subject());
        assertThat(deserialized.version()).isEqualTo(original.version());
        assertThat(deserialized.toIdentifier()).isEqualTo(original.toIdentifier());
    }

    @Test
    public void testFieldOrdering() throws Exception {
        // Test that fields are consistently ordered
        SchemaReference ref = new SchemaReference("ordering-test", 42);
        String json = getObjectMapper().writeValueAsString(ref);
        
        // With our JsonOrderingCustomizer, fields should be alphabetical
        int subjectPos = json.indexOf("subject");
        int versionPos = json.indexOf("version");
        
        assertThat(subjectPos).isLessThan(versionPos);
    }

    @Test
    public void testTypicalSchemaRegistryUsage() throws Exception {
        // Simulate typical schema registry patterns
        String[] typicalSubjects = {
            "chunker-config",
            "embedder-config", 
            "com.rokkon.pipeline.chunker-v1",
            "pipeline-step-config-value",
            "pipeline-graph-config-value"
        };
        
        for (String subject : typicalSubjects) {
            SchemaReference ref = new SchemaReference(subject, 1);
            String json = getObjectMapper().writeValueAsString(ref);
            SchemaReference deserialized = getObjectMapper().readValue(json, SchemaReference.class);
            
            assertThat(deserialized.subject()).isEqualTo(subject);
            assertThat(deserialized.version()).isEqualTo(1);
            assertThat(deserialized.toIdentifier()).isEqualTo(subject + ":1");
        }
    }

    @Test
    public void testVersionEvolution() throws Exception {
        // Test schema evolution scenario
        String subject = "chunker-config";
        
        // Simulate version evolution
        for (int version = 1; version <= 5; version++) {
            SchemaReference ref = new SchemaReference(subject, version);
            String json = getObjectMapper().writeValueAsString(ref);
            
            assertThat(json).contains("\"subject\":\"" + subject + "\"");
            assertThat(json).contains("\"version\":" + version);
            
            SchemaReference deserialized = getObjectMapper().readValue(json, SchemaReference.class);
            assertThat(deserialized.toIdentifier()).isEqualTo(subject + ":" + version);
        }
    }
}