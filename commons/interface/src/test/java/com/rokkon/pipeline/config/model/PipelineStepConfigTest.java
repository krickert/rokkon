package com.rokkon.pipeline.config.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class PipelineStepConfigTest {

    @Inject
    ObjectMapper objectMapper;

    @Test
    public void testComplexSerializationWithAllFeatures() throws Exception {
        // Create custom JSON config
        JsonNode jsonConfig = JsonNodeFactory.instance.objectNode()
            .put("chunkSize", 1000)
            .put("overlap", 100)
            .put("splitOnSentences", true);
            
        PipelineStepConfig.JsonConfigOptions customConfig = new PipelineStepConfig.JsonConfigOptions(
            jsonConfig, 
            Map.of("timeout", "30s", "retries", "3")
        );

        // Create Kafka inputs
        KafkaInputDefinition kafkaInput = new KafkaInputDefinition(
            List.of("input-topic-1", "input-topic-2"),
            "chunker-consumer-group",
            Map.of("auto.offset.reset", "earliest")
        );

        // Create outputs
        GrpcTransportConfig grpcTransport = new GrpcTransportConfig(
            "embedder-service", 
            Map.of("timeout", "60s")
        );
        
        KafkaTransportConfig kafkaTransport = new KafkaTransportConfig(
            "chunks-output", 
            null, // use default partitionKeyField
            null, // use default compressionType
            null, // use default batchSize
            null, // use default lingerMs
            Map.of("acks", "all")
        );

        PipelineStepConfig.OutputTarget grpcOutput = new PipelineStepConfig.OutputTarget(
            "embedder-step", TransportType.GRPC, grpcTransport, null);
            
        PipelineStepConfig.OutputTarget kafkaOutput = new PipelineStepConfig.OutputTarget(
            "backup-step", TransportType.KAFKA, null, kafkaTransport);

        Map<String, PipelineStepConfig.OutputTarget> outputs = Map.of(
            "primary", grpcOutput,
            "backup", kafkaOutput
        );

        // Create processor info
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "chunker-service", null);

        // Create the complete step config
        PipelineStepConfig stepConfig = new PipelineStepConfig(
            "chunker-step",
            StepType.PIPELINE,
            "Text chunking step with multiple outputs",
            "chunker-schema-v1",
            customConfig,
            List.of(kafkaInput),
            outputs,
            3,      // maxRetries
            2000L,  // retryBackoffMs
            60000L, // maxRetryBackoffMs
            1.5,    // retryBackoffMultiplier
            30000L, // stepTimeoutMs
            processorInfo
        );

        String json = objectMapper.writeValueAsString(stepConfig);

        // Verify all major components are serialized
        assertTrue(json.contains("\"stepName\":\"chunker-step\""));
        assertTrue(json.contains("\"stepType\":\"PIPELINE\""));
        assertTrue(json.contains("\"chunkSize\":1000"));
        assertTrue(json.contains("\"input-topic-1\""));
        assertTrue(json.contains("\"chunker-consumer-group\""));
        assertTrue(json.contains("\"embedder-service\""));
        assertTrue(json.contains("\"chunks-output\""));
        assertTrue(json.contains("\"maxRetries\":3"));
        assertTrue(json.contains("\"grpcServiceName\":\"chunker-service\""));
    }

    @Test
    public void testDeserialization() throws Exception {
        String json = """
            {
                "stepName": "opensearch-sink",
                "stepType": "SINK",
                "description": "OpenSearch document sink",
                "customConfigSchemaId": "opensearch-schema-v2",
                "customConfig": {
                    "jsonConfig": {
                        "indexName": "documents",
                        "batchSize": 100
                    },
                    "configParams": {
                        "timeout": "30s"
                    }
                },
                "kafkaInputs": [
                    {
                        "listenTopics": ["embeddings-output"],
                        "consumerGroupId": "opensearch-consumer",
                        "kafkaConsumerProperties": {
                            "fetch.min.bytes": "1024"
                        }
                    }
                ],
                "outputs": {},
                "maxRetries": 5,
                "retryBackoffMs": 1000,
                "maxRetryBackoffMs": 30000,
                "retryBackoffMultiplier": 2.0,
                "stepTimeoutMs": 60000,
                "processorInfo": {
                    "grpcServiceName": "opensearch-sink-service"
                }
            }
            """;

        PipelineStepConfig stepConfig = objectMapper.readValue(json, PipelineStepConfig.class);

        assertEquals("opensearch-sink", stepConfig.stepName());
        assertEquals(StepType.SINK, stepConfig.stepType());
        assertEquals("OpenSearch document sink", stepConfig.description());
        assertEquals("opensearch-schema-v2", stepConfig.customConfigSchemaId());
        
        // Check custom config
        assertNotNull(stepConfig.customConfig());
        assertEquals("documents", stepConfig.customConfig().jsonConfig().get("indexName").asText());
        assertEquals(100, stepConfig.customConfig().jsonConfig().get("batchSize").asInt());
        assertEquals("30s", stepConfig.customConfig().configParams().get("timeout"));
        
        // Check Kafka inputs
        assertEquals(1, stepConfig.kafkaInputs().size());
        KafkaInputDefinition kafkaInput = stepConfig.kafkaInputs().get(0);
        assertEquals(List.of("embeddings-output"), kafkaInput.listenTopics());
        assertEquals("opensearch-consumer", kafkaInput.consumerGroupId());
        
        // Check processor info
        assertEquals("opensearch-sink-service", stepConfig.processorInfo().grpcServiceName());
        assertNull(stepConfig.processorInfo().internalProcessorBeanName());
        
        // Check retry settings
        assertEquals(5, stepConfig.maxRetries());
        assertEquals(1000L, stepConfig.retryBackoffMs());
        assertEquals(30000L, stepConfig.maxRetryBackoffMs());
        assertEquals(2.0, stepConfig.retryBackoffMultiplier());
        assertEquals(60000L, stepConfig.stepTimeoutMs());
    }

    @Test
    public void testMinimalConfiguration() throws Exception {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "simple-service", null);

        PipelineStepConfig stepConfig = new PipelineStepConfig(
            "simple-step", StepType.PIPELINE, processorInfo);

        String json = objectMapper.writeValueAsString(stepConfig);
        assertTrue(json.contains("\"stepName\":\"simple-step\""));
        assertTrue(json.contains("\"grpcServiceName\":\"simple-service\""));

        // Test round trip
        PipelineStepConfig deserialized = objectMapper.readValue(json, PipelineStepConfig.class);
        assertEquals("simple-step", deserialized.stepName());
        assertEquals(StepType.PIPELINE, deserialized.stepType());
        assertEquals("simple-service", deserialized.processorInfo().grpcServiceName());
    }

    @Test
    public void testOutputTargetValidation() {
        GrpcTransportConfig grpcConfig = new GrpcTransportConfig("test-service", Map.of());
        KafkaTransportConfig kafkaConfig = new KafkaTransportConfig("test-topic", null, null, null, null, Map.of());

        // Valid GRPC output
        assertDoesNotThrow(() -> new PipelineStepConfig.OutputTarget(
            "grpc-target", TransportType.GRPC, grpcConfig, null));

        // Valid Kafka output
        assertDoesNotThrow(() -> new PipelineStepConfig.OutputTarget(
            "kafka-target", TransportType.KAFKA, null, kafkaConfig));

        // Invalid: GRPC type but no GRPC config
        assertThrows(IllegalArgumentException.class, () -> new PipelineStepConfig.OutputTarget(
            "invalid-target", TransportType.GRPC, null, null));

        // Invalid: Kafka type but no Kafka config
        assertThrows(IllegalArgumentException.class, () -> new PipelineStepConfig.OutputTarget(
            "invalid-target", TransportType.KAFKA, null, null));

        // Invalid: GRPC type but has Kafka config
        assertThrows(IllegalArgumentException.class, () -> new PipelineStepConfig.OutputTarget(
            "invalid-target", TransportType.GRPC, null, kafkaConfig));
    }

    @Test
    public void testProcessorInfoValidation() {
        // Valid: only gRPC service name
        assertDoesNotThrow(() -> new PipelineStepConfig.ProcessorInfo("grpc-service", null));
        assertDoesNotThrow(() -> new PipelineStepConfig.ProcessorInfo("grpc-service", ""));

        // Valid: only internal bean name
        assertDoesNotThrow(() -> new PipelineStepConfig.ProcessorInfo(null, "internal-bean"));
        assertDoesNotThrow(() -> new PipelineStepConfig.ProcessorInfo("", "internal-bean"));

        // Invalid: both set
        assertThrows(IllegalArgumentException.class, () -> new PipelineStepConfig.ProcessorInfo(
            "grpc-service", "internal-bean"));

        // Invalid: neither set
        assertThrows(IllegalArgumentException.class, () -> new PipelineStepConfig.ProcessorInfo(
            null, null));
        assertThrows(IllegalArgumentException.class, () -> new PipelineStepConfig.ProcessorInfo(
            "", ""));
    }

    @Test
    public void testRoundTripComplexConfiguration() throws Exception {
        // Create a complex configuration
        JsonNode jsonConfig = JsonNodeFactory.instance.objectNode()
            .put("setting1", "value1")
            .put("setting2", 42);

        PipelineStepConfig.JsonConfigOptions customConfig = new PipelineStepConfig.JsonConfigOptions(
            jsonConfig, Map.of("param1", "paramValue1"));

        KafkaInputDefinition kafkaInput = new KafkaInputDefinition(
            List.of("topic1", "topic2"), "group1", Map.of("key", "value"));

        GrpcTransportConfig grpcTransport = new GrpcTransportConfig("service1", Map.of("timeout", "30s"));
        PipelineStepConfig.OutputTarget output = new PipelineStepConfig.OutputTarget(
            "next-step", TransportType.GRPC, grpcTransport, null);

        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "test-service", null);

        PipelineStepConfig original = new PipelineStepConfig(
            "test-step",
            StepType.PIPELINE,
            "Test step description",
            "test-schema",
            customConfig,
            List.of(kafkaInput),
            Map.of("output1", output),
            2, 1500L, 45000L, 1.8, 25000L,
            processorInfo
        );

        // Serialize and deserialize
        String json = objectMapper.writeValueAsString(original);
        PipelineStepConfig deserialized = objectMapper.readValue(json, PipelineStepConfig.class);

        // Verify all fields match
        assertEquals(original.stepName(), deserialized.stepName());
        assertEquals(original.stepType(), deserialized.stepType());
        assertEquals(original.description(), deserialized.description());
        assertEquals(original.customConfigSchemaId(), deserialized.customConfigSchemaId());
        assertEquals(original.maxRetries(), deserialized.maxRetries());
        assertEquals(original.retryBackoffMs(), deserialized.retryBackoffMs());
        assertEquals(original.maxRetryBackoffMs(), deserialized.maxRetryBackoffMs());
        assertEquals(original.retryBackoffMultiplier(), deserialized.retryBackoffMultiplier());
        assertEquals(original.stepTimeoutMs(), deserialized.stepTimeoutMs());

        // Verify nested objects
        assertEquals(original.processorInfo().grpcServiceName(), 
                    deserialized.processorInfo().grpcServiceName());
        assertEquals(original.kafkaInputs().size(), deserialized.kafkaInputs().size());
        assertEquals(original.outputs().size(), deserialized.outputs().size());
    }
}