package com.rokkon.search.config.pipeline.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class GrpcTransportConfigTest {

    @Inject
    ObjectMapper objectMapper;

    @Test
    public void testSerializationWithAllFields() throws Exception {
        Map<String, String> properties = Map.of(
            "timeout", "30s",
            "loadBalancingPolicy", "round_robin"
        );
        GrpcTransportConfig config = new GrpcTransportConfig("test-service", properties);
        
        String json = objectMapper.writeValueAsString(config);
        
        assertTrue(json.contains("\"serviceName\":\"test-service\""));
        assertTrue(json.contains("\"grpcClientProperties\""));
        assertTrue(json.contains("\"timeout\":\"30s\""));
        assertTrue(json.contains("\"loadBalancingPolicy\":\"round_robin\""));
    }

    @Test
    public void testSerializationWithNullProperties() throws Exception {
        GrpcTransportConfig config = new GrpcTransportConfig("test-service", null);
        
        String json = objectMapper.writeValueAsString(config);
        
        assertTrue(json.contains("\"serviceName\":\"test-service\""));
        // Empty map should either not appear or appear as empty object
        assertTrue(json.contains("\"grpcClientProperties\":{}") || !json.contains("grpcClientProperties"));
    }

    @Test
    public void testDeserialization() throws Exception {
        String json = """
            {
                "serviceName": "chunker-service",
                "grpcClientProperties": {
                    "timeout": "60s",
                    "maxRetries": "3"
                }
            }
            """;
        
        GrpcTransportConfig config = objectMapper.readValue(json, GrpcTransportConfig.class);
        
        assertEquals("chunker-service", config.serviceName());
        assertEquals(2, config.grpcClientProperties().size());
        assertEquals("60s", config.grpcClientProperties().get("timeout"));
        assertEquals("3", config.grpcClientProperties().get("maxRetries"));
    }

    @Test
    public void testDeserializationWithMissingProperties() throws Exception {
        String json = """
            {
                "serviceName": "embedder-service"
            }
            """;
        
        GrpcTransportConfig config = objectMapper.readValue(json, GrpcTransportConfig.class);
        
        assertEquals("embedder-service", config.serviceName());
        assertTrue(config.grpcClientProperties().isEmpty());
    }

    @Test
    public void testImmutability() {
        Map<String, String> properties = Map.of("key", "value");
        GrpcTransportConfig config = new GrpcTransportConfig("service", properties);
        
        // Properties map should be immutable
        assertThrows(UnsupportedOperationException.class, 
            () -> config.grpcClientProperties().put("new", "value"));
    }

    @Test
    public void testRoundTrip() throws Exception {
        Map<String, String> properties = Map.of(
            "connection.timeout", "10s",
            "retry.policy", "exponential"
        );
        GrpcTransportConfig original = new GrpcTransportConfig("my-service", properties);
        
        String json = objectMapper.writeValueAsString(original);
        GrpcTransportConfig deserialized = objectMapper.readValue(json, GrpcTransportConfig.class);
        
        assertEquals(original.serviceName(), deserialized.serviceName());
        assertEquals(original.grpcClientProperties(), deserialized.grpcClientProperties());
    }
}