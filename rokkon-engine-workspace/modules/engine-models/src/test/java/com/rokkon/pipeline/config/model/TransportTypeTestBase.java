package com.rokkon.pipeline.config.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Base test class for TransportType enum serialization/deserialization.
 * Tests all transport mechanisms used between pipeline steps.
 */
public abstract class TransportTypeTestBase {

    protected abstract ObjectMapper getObjectMapper();

    @Test
    public void testSerializeKafka() throws Exception {
        String json = getObjectMapper().writeValueAsString(TransportType.KAFKA);
        assertThat(json).isEqualTo("\"KAFKA\"");
    }

    @Test
    public void testSerializeGrpc() throws Exception {
        String json = getObjectMapper().writeValueAsString(TransportType.GRPC);
        assertThat(json).isEqualTo("\"GRPC\"");
    }

    @Test
    public void testSerializeInternal() throws Exception {
        String json = getObjectMapper().writeValueAsString(TransportType.INTERNAL);
        assertThat(json).isEqualTo("\"INTERNAL\"");
    }

    @Test
    public void testDeserializeKafka() throws Exception {
        TransportType type = getObjectMapper().readValue("\"KAFKA\"", TransportType.class);
        assertThat(type).isEqualTo(TransportType.KAFKA);
    }

    @Test
    public void testDeserializeGrpc() throws Exception {
        TransportType type = getObjectMapper().readValue("\"GRPC\"", TransportType.class);
        assertThat(type).isEqualTo(TransportType.GRPC);
    }

    @Test
    public void testDeserializeInternal() throws Exception {
        TransportType type = getObjectMapper().readValue("\"INTERNAL\"", TransportType.class);
        assertThat(type).isEqualTo(TransportType.INTERNAL);
    }

    @Test
    public void testAllValues() {
        TransportType[] values = TransportType.values();
        assertThat(values).hasSize(3);
        assertThat(values).containsExactly(TransportType.KAFKA, TransportType.GRPC, TransportType.INTERNAL);
    }

    @Test
    public void testRoundTripAllValues() throws Exception {
        for (TransportType transportType : TransportType.values()) {
            String json = getObjectMapper().writeValueAsString(transportType);
            TransportType deserialized = getObjectMapper().readValue(json, TransportType.class);
            assertThat(deserialized).isEqualTo(transportType);
        }
    }

    @Test
    public void testInvalidDeserialization() {
        assertThatThrownBy(() -> getObjectMapper().readValue("\"INVALID_TRANSPORT\"", TransportType.class))
            .hasMessageContaining("not one of the values accepted");
            
        assertThatThrownBy(() -> getObjectMapper().readValue("\"\"", TransportType.class))
            .hasMessageContaining("Cannot coerce empty String");
    }

    @Test
    public void testCaseInsensitiveDeserialization() {
        // By default, Jackson is case-sensitive for enums
        assertThatThrownBy(() -> getObjectMapper().readValue("\"kafka\"", TransportType.class))
            .hasMessageContaining("not one of the values accepted");
            
        assertThatThrownBy(() -> getObjectMapper().readValue("\"gRPC\"", TransportType.class))
            .hasMessageContaining("not one of the values accepted");
    }

    @Test
    public void testNullHandling() throws Exception {
        String json = getObjectMapper().writeValueAsString(null);
        assertThat(json).isEqualTo("null");
        
        TransportType type = getObjectMapper().readValue("null", TransportType.class);
        assertThat(type).isNull();
    }

    @Test
    public void testEnumInObject() throws Exception {
        // Test enum serialization within a Map (avoid inner class deserialization issues)
        java.util.Map<String, Object> obj = new java.util.HashMap<>();
        obj.put("transport", TransportType.KAFKA);
        
        String json = getObjectMapper().writeValueAsString(obj);
        assertThat(json).contains("\"transport\":\"KAFKA\"");
        
        // Test deserialization from JSON string
        String testJson = "{\"transport\":\"KAFKA\"}";
        java.util.Map<String, Object> deserialized = getObjectMapper().readValue(
            testJson, 
            new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {}
        );
        assertThat(deserialized.get("transport")).isEqualTo("KAFKA");
    }

    @Test
    public void testValueOf() {
        // Test standard enum valueOf behavior
        assertThat(TransportType.valueOf("KAFKA")).isEqualTo(TransportType.KAFKA);
        assertThat(TransportType.valueOf("GRPC")).isEqualTo(TransportType.GRPC);
        assertThat(TransportType.valueOf("INTERNAL")).isEqualTo(TransportType.INTERNAL);
        
        assertThatThrownBy(() -> TransportType.valueOf("INVALID"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}