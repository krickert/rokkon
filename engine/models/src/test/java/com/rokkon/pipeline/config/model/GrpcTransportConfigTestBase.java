package com.rokkon.pipeline.config.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base test class for GrpcTransportConfig serialization/deserialization.
 * Tests gRPC transport configuration with Consul service discovery.
 */
public abstract class GrpcTransportConfigTestBase {

    protected abstract ObjectMapper getObjectMapper();

    @Test
    public void testBasicConfiguration() {
        GrpcTransportConfig config = new GrpcTransportConfig(
                "document-processor-service",
                Map.of("timeout", "30000", "retry", "3")
        );
        
        assertThat(config.serviceName()).isEqualTo("document-processor-service");
        assertThat(config.grpcClientProperties()).containsEntry("timeout", "30000");
        assertThat(config.grpcClientProperties()).containsEntry("retry", "3");
    }

    @Test
    public void testEmptyProperties() {
        GrpcTransportConfig config = new GrpcTransportConfig(
                "simple-service",
                Map.of()
        );
        
        assertThat(config.serviceName()).isEqualTo("simple-service");
        assertThat(config.grpcClientProperties()).isEmpty();
    }

    @Test
    public void testNullProperties() {
        GrpcTransportConfig config = new GrpcTransportConfig(
                "null-props-service",
                null
        );
        
        assertThat(config.serviceName()).isEqualTo("null-props-service");
        assertThat(config.grpcClientProperties()).isEmpty();
    }

    @Test
    public void testSerializationDeserialization() throws Exception {
        GrpcTransportConfig original = new GrpcTransportConfig(
                "metadata-extractor-service",
                Map.of(
                    "timeout", "60000",
                    "retry", "5",
                    "loadBalancingPolicy", "round_robin",
                    "maxInboundMessageSize", "4194304"
                )
        );
        
        String json = getObjectMapper().writeValueAsString(original);
        
        // Verify JSON structure
        assertThat(json).contains("\"serviceName\":\"metadata-extractor-service\"");
        assertThat(json).contains("\"timeout\":\"60000\"");
        assertThat(json).contains("\"retry\":\"5\"");
        assertThat(json).contains("\"loadBalancingPolicy\":\"round_robin\"");
        
        // Deserialize
        GrpcTransportConfig deserialized = getObjectMapper().readValue(json, GrpcTransportConfig.class);
        
        assertThat(deserialized.serviceName()).isEqualTo(original.serviceName());
        assertThat(deserialized.grpcClientProperties()).isEqualTo(original.grpcClientProperties());
    }

    @Test
    public void testMinimalSerialization() throws Exception {
        String json = """
            {
                "serviceName": "minimal-service"
            }
            """;
        
        GrpcTransportConfig config = getObjectMapper().readValue(json, GrpcTransportConfig.class);
        
        assertThat(config.serviceName()).isEqualTo("minimal-service");
        assertThat(config.grpcClientProperties()).isEmpty();
    }

    @Test
    public void testConsulServiceDiscoveryConfig() {
        // Test typical Consul service name patterns
        GrpcTransportConfig config = new GrpcTransportConfig(
                "rokkon-chunker-v2",
                Map.of(
                    "consul.healthCheck", "true",
                    "consul.tags", "production,v2"
                )
        );
        
        assertThat(config.serviceName()).isEqualTo("rokkon-chunker-v2");
        assertThat(config.grpcClientProperties())
            .containsEntry("consul.healthCheck", "true")
            .containsEntry("consul.tags", "production,v2");
    }

    @Test
    public void testImmutability() {
        Map<String, String> mutableProps = new java.util.HashMap<>();
        mutableProps.put("timeout", "5000");
        
        GrpcTransportConfig config = new GrpcTransportConfig(
                "immutable-test-service",
                mutableProps
        );
        
        // Try to modify original map
        mutableProps.put("retry", "10");
        
        // Config should not be affected
        assertThat(config.grpcClientProperties()).hasSize(1);
        assertThat(config.grpcClientProperties()).containsOnlyKeys("timeout");
        
        // Try to modify returned map
        assertThat(config.grpcClientProperties()).isUnmodifiable();
    }

    @Test
    public void testRealWorldConfiguration() throws Exception {
        // Test a production-like configuration
        String json = """
            {
                "serviceName": "rokkon-document-processor",
                "grpcClientProperties": {
                    "timeout": "120000",
                    "retry": "3",
                    "retryBackoff": "exponential",
                    "initialBackoff": "1000",
                    "maxBackoff": "30000",
                    "backoffMultiplier": "2.0",
                    "loadBalancingPolicy": "least_request",
                    "maxInboundMessageSize": "10485760",
                    "keepAliveTime": "30000",
                    "keepAliveTimeout": "10000",
                    "consul.healthCheck": "true",
                    "consul.tags": "production,grpc,v3"
                }
            }
            """;
        
        GrpcTransportConfig config = getObjectMapper().readValue(json, GrpcTransportConfig.class);
        
        assertThat(config.serviceName()).isEqualTo("rokkon-document-processor");
        assertThat(config.grpcClientProperties()).hasSize(12);
        
        // Verify important properties
        assertThat(config.grpcClientProperties())
            .containsEntry("timeout", "120000")
            .containsEntry("retry", "3")
            .containsEntry("loadBalancingPolicy", "least_request")
            .containsEntry("maxInboundMessageSize", "10485760")
            .containsEntry("consul.healthCheck", "true");
    }

    @Test
    public void testNullServiceName() {
        // Service name can be null - validation is done by OutputTarget
        GrpcTransportConfig config = new GrpcTransportConfig(null, Map.of());
        assertThat(config.serviceName()).isNull();
        assertThat(config.grpcClientProperties()).isEmpty();
    }
}