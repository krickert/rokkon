package com.rokkon.pipeline.validation.validators;

import com.rokkon.pipeline.config.model.*;
import com.rokkon.pipeline.validation.ValidationResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Abstract base test class for KafkaTopicNamingValidator.
 * Contains all test logic that is shared between unit tests (@QuarkusTest) 
 * and integration tests (@QuarkusIntegrationTest).
 */
public abstract class KafkaTopicNamingValidatorTestBase {
    
    protected abstract KafkaTopicNamingValidator getValidator();
    
    @Test
    void testValidTopicNames() {
        // Create processor info
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "parser-service",
            null
        );
        
        // Create Kafka transport config
        KafkaTransportConfig kafkaTransport = new KafkaTransportConfig(
            "document-processing.parser.output",
            null, // partitionKeyField - will default to pipedocId
            null, // compressionType - will default to snappy
            null, // batchSize - will use default
            null, // lingerMs - will use default
            Map.of()
        );
        
        // Create output target
        PipelineStepConfig.OutputTarget output = new PipelineStepConfig.OutputTarget(
            "next-step",
            TransportType.KAFKA,
            null, // grpcTransport
            kafkaTransport
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "parser-step",
            StepType.PIPELINE,
            "Parsing documents",
            null, // customConfigSchemaId
            null, // customConfig
            null, // kafkaInputs
            Map.of("default", output),
            3,
            1000L,
            30000L,
            2.0,
            60000L,
            processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "document-processing",
            Map.of("parser", step)
        );
        
        ValidationResult result = getValidator().validate(config);
        
        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
        assertTrue(result.warnings().isEmpty());
    }
    
    @Test
    void testInvalidTopicCharacters() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "parser-service", null
        );
        
        KafkaTransportConfig kafkaTransport = new KafkaTransportConfig(
            "invalid topic name!",  // Invalid characters
            null, null, null, null, Map.of()
        );
        
        PipelineStepConfig.OutputTarget output = new PipelineStepConfig.OutputTarget(
            "next-step", TransportType.KAFKA, null, kafkaTransport
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "parser", StepType.PIPELINE, "com.rokkon.parser", null, null, null,
            Map.of("default", output), null, null, null, null, null, processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("parser", step)
        );
        
        ValidationResult result = getValidator().validate(config);
        
        assertFalse(result.valid());
        assertEquals(1, result.errors().size());
        assertTrue(result.errors().get(0).contains("can only contain letters, numbers, dots, underscores, and hyphens"));
    }
    
    @Test
    void testTopicNameTooLong() {
        String longTopicName = "a".repeat(250); // Exceeds 249 character limit
        
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "parser-service", null
        );
        
        KafkaTransportConfig kafkaTransport = new KafkaTransportConfig(
            longTopicName, null, null, null, null, Map.of()
        );
        
        PipelineStepConfig.OutputTarget output = new PipelineStepConfig.OutputTarget(
            "next-step", TransportType.KAFKA, null, kafkaTransport
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "parser", StepType.PIPELINE, "com.rokkon.parser", null, null, null,
            Map.of("default", output), null, null, null, null, null, processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("parser", step)
        );
        
        ValidationResult result = getValidator().validate(config);
        
        assertFalse(result.valid());
        assertEquals(1, result.errors().size());
        assertTrue(result.errors().get(0).contains("cannot exceed 249 characters"));
    }
    
    @Test
    void testInvalidDotAndDotDotTopicNames() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "service", null
        );
        
        // Create step with "." topic
        KafkaTransportConfig kafka1 = new KafkaTransportConfig(
            ".", null, null, null, null, Map.of()
        );
        PipelineStepConfig.OutputTarget output1 = new PipelineStepConfig.OutputTarget(
            "target1", TransportType.KAFKA, null, kafka1
        );
        PipelineStepConfig step1 = new PipelineStepConfig(
            "step1", StepType.PIPELINE, "desc", null, null, null,
            Map.of("default", output1), null, null, null, null, null, processorInfo
        );
        
        // Create step with ".." topic
        KafkaTransportConfig kafka2 = new KafkaTransportConfig(
            "..", null, null, null, null, Map.of()
        );
        PipelineStepConfig.OutputTarget output2 = new PipelineStepConfig.OutputTarget(
            "target2", TransportType.KAFKA, null, kafka2
        );
        PipelineStepConfig step2 = new PipelineStepConfig(
            "step2", StepType.PIPELINE, "desc", null, null, null,
            Map.of("default", output2), null, null, null, null, null, processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("step1", step1, "step2", step2)
        );
        
        ValidationResult result = getValidator().validate(config);
        
        assertFalse(result.valid());
        assertEquals(2, result.errors().size());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("Topic name cannot be '.' or '..'")));
    }
    
    @Test
    void testEmptyTopicName() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "parser-service", null
        );
        
        KafkaTransportConfig kafkaTransport = new KafkaTransportConfig(
            "", null, null, null, null, Map.of()
        );
        
        PipelineStepConfig.OutputTarget output = new PipelineStepConfig.OutputTarget(
            "next-step", TransportType.KAFKA, null, kafkaTransport
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "parser", StepType.PIPELINE, "desc", null, null, null,
            Map.of("default", output), null, null, null, null, null, processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("parser", step)
        );
        
        ValidationResult result = getValidator().validate(config);
        
        assertFalse(result.valid());
        assertEquals(1, result.errors().size());
        assertTrue(result.errors().get(0).contains("Topic name cannot be empty"));
    }
    
    @Test
    void testNamingConventionWarnings() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "parser-service", null
        );
        
        KafkaTransportConfig kafkaTransport = new KafkaTransportConfig(
            "simpletopic", // No dots, doesn't follow convention
            null, null, null, null, Map.of()
        );
        
        PipelineStepConfig.OutputTarget output = new PipelineStepConfig.OutputTarget(
            "next-step", TransportType.KAFKA, null, kafkaTransport
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "parser", StepType.PIPELINE, "desc", null, null, null,
            Map.of("default", output), null, null, null, null, null, processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("parser", step)
        );
        
        ValidationResult result = getValidator().validate(config);
        
        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
        assertEquals(1, result.warnings().size()); // Naming convention only (DLQ is auto-generated correctly)
        assertTrue(result.warnings().get(0).contains("should follow pattern '{pipeline-name}.{step-name}.{input/output}'"));
    }
    
    @Test
    void testDlqTopicPatternWarning() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "parser-service", null
        );
        
        // KafkaTransportConfig automatically generates DLQ topic as topic + ".dlq"
        // So this should NOT generate a warning
        KafkaTransportConfig kafkaTransport = new KafkaTransportConfig(
            "pipeline.parser.output", null, null, null, null, Map.of()
        );
        
        PipelineStepConfig.OutputTarget output = new PipelineStepConfig.OutputTarget(
            "next-step", TransportType.KAFKA, null, kafkaTransport
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "parser", StepType.PIPELINE, "desc", null, null, null,
            Map.of("default", output), null, null, null, null, null, processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("parser", step)
        );
        
        ValidationResult result = getValidator().validate(config);
        
        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
        // Should have no warnings since DLQ follows correct pattern
        assertTrue(result.warnings().isEmpty());
    }
    
    @Test
    void testKafkaInputValidation() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "aggregator-service", null
        );
        
        KafkaInputDefinition kafkaInput = new KafkaInputDefinition(
            List.of(
                "valid.topic.name",
                "invalid topic!"  // Invalid - removed empty string since KafkaInputDefinition doesn't allow it
            ),
            "aggregator-group",
            Map.of()
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "aggregator", StepType.PIPELINE, "desc", null, null,
            List.of(kafkaInput),
            null, null, null, null, null, null, processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("aggregator", step)
        );
        
        ValidationResult result = getValidator().validate(config);
        
        assertFalse(result.valid());
        assertEquals(1, result.errors().size()); // Invalid chars only (removed empty test)
        assertFalse(result.warnings().isEmpty()); // Naming convention warning
    }
    
    @Test
    void testMultipleOutputsValidation() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "router-service", null
        );
        
        // Create multiple outputs
        KafkaTransportConfig kafka1 = new KafkaTransportConfig(
            "pipeline.router.success", null, null, null, null, Map.of()
        );
        PipelineStepConfig.OutputTarget output1 = new PipelineStepConfig.OutputTarget(
            "success-handler", TransportType.KAFKA, null, kafka1
        );
        
        KafkaTransportConfig kafka2 = new KafkaTransportConfig(
            "pipeline.router.error", null, null, null, null, Map.of()
        );
        PipelineStepConfig.OutputTarget output2 = new PipelineStepConfig.OutputTarget(
            "error-handler", TransportType.KAFKA, null, kafka2
        );
        
        KafkaTransportConfig kafka3 = new KafkaTransportConfig(
            "pipeline.router.retry", null, null, null, null, Map.of()
        );
        PipelineStepConfig.OutputTarget output3 = new PipelineStepConfig.OutputTarget(
            "retry-handler", TransportType.KAFKA, null, kafka3
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "router", StepType.PIPELINE, "desc", null, null, null,
            Map.of(
                "success", output1,
                "error", output2,
                "retry", output3
            ),
            null, null, null, null, null, processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("router", step)
        );
        
        ValidationResult result = getValidator().validate(config);
        
        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
        // No DLQ warnings since getDlqTopic() automatically follows pattern
        assertTrue(result.warnings().isEmpty());
    }
    
    @Test
    void testNonKafkaTransportIgnored() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "grpc-service", null
        );
        
        // Create gRPC transport
        GrpcTransportConfig grpcTransport = new GrpcTransportConfig(
            "com.rokkon.service", Map.of()
        );
        
        PipelineStepConfig.OutputTarget output = new PipelineStepConfig.OutputTarget(
            "grpc-target", TransportType.GRPC, grpcTransport, null
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "grpc-step", StepType.PIPELINE, "desc", null, null, null,
            Map.of("default", output), null, null, null, null, null, processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("grpc-step", step)
        );
        
        ValidationResult result = getValidator().validate(config);
        
        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
        assertTrue(result.warnings().isEmpty());
    }
    
    @Test
    void testNullPipelineSteps() {
        PipelineConfig config = new PipelineConfig("test-pipeline", null);
        
        ValidationResult result = getValidator().validate(config);
        
        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
        assertTrue(result.warnings().isEmpty());
    }
    
    @Test
    void testPriorityAndName() {
        assertEquals(50, getValidator().getPriority());
        assertEquals("KafkaTopicNamingValidator", getValidator().getValidatorName());
    }
}