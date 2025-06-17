package com.rokkon.pipeline.config.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Base test class for StepType enum serialization/deserialization.
 * Tests all step types used in pipeline configuration.
 */
public abstract class StepTypeTestBase {

    protected abstract ObjectMapper getObjectMapper();

    @Test
    public void testSerializePipeline() throws Exception {
        String json = getObjectMapper().writeValueAsString(StepType.PIPELINE);
        assertThat(json).isEqualTo("\"PIPELINE\"");
    }

    @Test
    public void testSerializeInitialPipeline() throws Exception {
        String json = getObjectMapper().writeValueAsString(StepType.INITIAL_PIPELINE);
        assertThat(json).isEqualTo("\"INITIAL_PIPELINE\"");
    }

    @Test
    public void testSerializeSink() throws Exception {
        String json = getObjectMapper().writeValueAsString(StepType.SINK);
        assertThat(json).isEqualTo("\"SINK\"");
    }

    @Test
    public void testDeserializePipeline() throws Exception {
        StepType type = getObjectMapper().readValue("\"PIPELINE\"", StepType.class);
        assertThat(type).isEqualTo(StepType.PIPELINE);
    }

    @Test
    public void testDeserializeInitialPipeline() throws Exception {
        StepType type = getObjectMapper().readValue("\"INITIAL_PIPELINE\"", StepType.class);
        assertThat(type).isEqualTo(StepType.INITIAL_PIPELINE);
    }

    @Test
    public void testDeserializeSink() throws Exception {
        StepType type = getObjectMapper().readValue("\"SINK\"", StepType.class);
        assertThat(type).isEqualTo(StepType.SINK);
    }

    @Test
    public void testAllValues() {
        StepType[] values = StepType.values();
        assertThat(values).hasSize(3);
        assertThat(values).containsExactly(StepType.PIPELINE, StepType.INITIAL_PIPELINE, StepType.SINK);
    }

    @Test
    public void testRoundTripAllValues() throws Exception {
        for (StepType stepType : StepType.values()) {
            String json = getObjectMapper().writeValueAsString(stepType);
            StepType deserialized = getObjectMapper().readValue(json, StepType.class);
            assertThat(deserialized).isEqualTo(stepType);
        }
    }

    @Test
    public void testInvalidDeserialization() {
        // Test invalid enum values
        assertThatThrownBy(() -> getObjectMapper().readValue("\"INVALID_TYPE\"", StepType.class))
            .hasMessageContaining("not one of the values accepted");
            
        assertThatThrownBy(() -> getObjectMapper().readValue("\"\"", StepType.class))
            .hasMessageContaining("Cannot coerce empty String");
    }

    @Test
    public void testCaseInsensitiveDeserialization() {
        // By default, Jackson is case-sensitive for enums
        assertThatThrownBy(() -> getObjectMapper().readValue("\"pipeline\"", StepType.class))
            .hasMessageContaining("not one of the values accepted");
            
        assertThatThrownBy(() -> getObjectMapper().readValue("\"Pipeline\"", StepType.class))
            .hasMessageContaining("not one of the values accepted");
    }

    @Test
    public void testNullHandling() throws Exception {
        String json = getObjectMapper().writeValueAsString(null);
        assertThat(json).isEqualTo("null");
        
        StepType type = getObjectMapper().readValue("null", StepType.class);
        assertThat(type).isNull();
    }

    @Test
    public void testEnumInObject() throws Exception {
        // Test enum serialization within a Map (avoid inner class deserialization issues)
        java.util.Map<String, Object> obj = new java.util.HashMap<>();
        obj.put("stepType", StepType.PIPELINE);
        
        String json = getObjectMapper().writeValueAsString(obj);
        assertThat(json).contains("\"stepType\":\"PIPELINE\"");
        
        // Test deserialization from JSON string
        String testJson = "{\"stepType\":\"PIPELINE\"}";
        java.util.Map<String, Object> deserialized = getObjectMapper().readValue(
            testJson, 
            new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {}
        );
        assertThat(deserialized.get("stepType")).isEqualTo("PIPELINE");
    }

    @Test
    public void testValueOf() {
        // Test standard enum valueOf behavior
        assertThat(StepType.valueOf("PIPELINE")).isEqualTo(StepType.PIPELINE);
        assertThat(StepType.valueOf("INITIAL_PIPELINE")).isEqualTo(StepType.INITIAL_PIPELINE);
        assertThat(StepType.valueOf("SINK")).isEqualTo(StepType.SINK);
        
        assertThatThrownBy(() -> StepType.valueOf("INVALID"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}