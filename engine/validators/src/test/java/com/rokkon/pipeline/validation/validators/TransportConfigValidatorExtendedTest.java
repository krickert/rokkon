package com.rokkon.pipeline.validation.validators;

import com.rokkon.pipeline.config.model.*;
import com.rokkon.pipeline.validation.ValidationResult;
import org.junit.jupiter.api.Test;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Extended tests for TransportConfigValidator to cover invalid configurations.
 */
@QuarkusTest
public class TransportConfigValidatorExtendedTest extends TransportConfigValidatorTestBase {
    
    @Inject
    TransportConfigValidator validator;
    
    @Override
    protected TransportConfigValidator getValidator() {
        return validator;
    }
    
    @Test
    void testKafkaInputWithNoTopics() {
        // KafkaInputDefinition constructor throws IllegalArgumentException for empty topics
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> {
            new KafkaInputDefinition(
                Collections.emptyList(), // No topics
                "test-consumer-group",
                null
            );
        });
    }
    
    @Test
    void testKafkaInputWithBlankTopic() {
        // KafkaInputDefinition constructor throws IllegalArgumentException for blank topics
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> {
            new KafkaInputDefinition(
                List.of(""), // Blank topic
                "test-consumer-group",
                null
            );
        });
    }
    
    @Test
    void testKafkaTransportWithMissingTopic() {
        KafkaTransportConfig kafkaTransport = new KafkaTransportConfig(
            null, // Missing topic
            null,
            null,
            null,
            null,
            null
        );
        
        PipelineStepConfig.OutputTarget output = new PipelineStepConfig.OutputTarget(
            "next-step",
            TransportType.KAFKA,
            null,
            kafkaTransport
        );
        
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "service", null
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "processor",
            StepType.PIPELINE,
            "Test step",
            null, null,
            Collections.emptyList(),
            Map.of("default", output),
            null, null, null, null, null,
            processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("step1", step)
        );
        
        ValidationResult result = getValidator().validate(config);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0)).contains("Topic is required");
    }
    
    @Test
    void testKafkaTransportWithBlankTopic() {
        KafkaTransportConfig kafkaTransport = new KafkaTransportConfig(
            "", // Blank topic
            null,
            null,
            null,
            null,
            null
        );
        
        PipelineStepConfig.OutputTarget output = new PipelineStepConfig.OutputTarget(
            "next-step",
            TransportType.KAFKA,
            null,
            kafkaTransport
        );
        
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "service", null
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "processor",
            StepType.PIPELINE,
            "Test step",
            null, null,
            Collections.emptyList(),
            Map.of("default", output),
            null, null, null, null, null,
            processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("step1", step)
        );
        
        ValidationResult result = getValidator().validate(config);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0)).contains("Topic is required");
    }
    
    @Test
    void testKafkaTransportWithNegativeBatchSize() {
        // Note: KafkaTransportConfig normalizes negative batch size to default (16384)
        KafkaTransportConfig kafkaTransport = new KafkaTransportConfig(
            "output-topic",
            null,
            null,
            -1, // Negative batch size gets normalized to 16384
            null,
            null
        );
        
        // Verify that negative batch size is normalized
        assertThat(kafkaTransport.batchSize()).isEqualTo(16384);
        
        PipelineStepConfig.OutputTarget output = new PipelineStepConfig.OutputTarget(
            "next-step",
            TransportType.KAFKA,
            null, // grpcTransport
            kafkaTransport
        );
        
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "service", null
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "processor",
            StepType.PIPELINE,
            "Test step",
            null, null,
            Collections.emptyList(),
            Map.of("default", output),
            null, null, null, null, null,
            processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("step1", step)
        );
        
        ValidationResult result = getValidator().validate(config);
        // Should be valid since batch size is normalized to 16384
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }
    
    @Test
    void testKafkaTransportWithNegativeLingerMs() {
        // Note: KafkaTransportConfig normalizes negative linger ms to default (10)
        KafkaTransportConfig kafkaTransport = new KafkaTransportConfig(
            "output-topic",
            null,
            null,
            null,
            -5, // Negative linger ms gets normalized to 10
            null
        );
        
        // Verify that negative linger ms is normalized
        assertThat(kafkaTransport.lingerMs()).isEqualTo(10);
        
        PipelineStepConfig.OutputTarget output = new PipelineStepConfig.OutputTarget(
            "next-step",
            TransportType.KAFKA,
            null,
            kafkaTransport
        );
        
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "service", null
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "processor",
            StepType.PIPELINE,
            "Test step",
            null, null,
            Collections.emptyList(),
            Map.of("default", output),
            null, null, null, null, null,
            processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("step1", step)
        );
        
        ValidationResult result = getValidator().validate(config);
        // Should be valid since linger ms is normalized to 10
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }
    
    @Test
    void testKafkaTransportWithInvalidCompressionType() {
        KafkaTransportConfig kafkaTransport = new KafkaTransportConfig(
            "output-topic",
            null,
            "invalid-compression", // Invalid compression type
            null,
            null,
            null
        );
        
        PipelineStepConfig.OutputTarget output = new PipelineStepConfig.OutputTarget(
            "next-step",
            TransportType.KAFKA,
            null,
            kafkaTransport
        );
        
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "service", null
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "processor",
            StepType.PIPELINE,
            "Test step",
            null, null,
            Collections.emptyList(),
            Map.of("default", output),
            null, null, null, null, null,
            processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("step1", step)
        );
        
        ValidationResult result = getValidator().validate(config);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0)).contains("Invalid compression type");
    }
    
    @Test
    void testGrpcTransportWithMissingServiceName() {
        GrpcTransportConfig grpcTransport = new GrpcTransportConfig(
            null, // Missing service name
            null
        );
        
        PipelineStepConfig.OutputTarget output = new PipelineStepConfig.OutputTarget(
            "processor",
            TransportType.GRPC,
            grpcTransport,
            null
        );
        
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "service", null
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "reader",
            StepType.INITIAL_PIPELINE,
            "Test step",
            null, null,
            Collections.emptyList(),
            Map.of("default", output),
            null, null, null, null, null,
            processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("step1", step)
        );
        
        ValidationResult result = getValidator().validate(config);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0)).contains("Service name is required");
    }
    
    @Test
    void testGrpcTransportWithBlankServiceName() {
        GrpcTransportConfig grpcTransport = new GrpcTransportConfig(
            "", // Blank service name
            null
        );
        
        PipelineStepConfig.OutputTarget output = new PipelineStepConfig.OutputTarget(
            "processor",
            TransportType.GRPC,
            grpcTransport,
            null
        );
        
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "service", null
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "reader",
            StepType.INITIAL_PIPELINE,
            "Test step",
            null, null,
            Collections.emptyList(),
            Map.of("default", output),
            null, null, null, null, null,
            processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("step1", step)
        );
        
        ValidationResult result = getValidator().validate(config);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0)).contains("Service name is required");
    }
    
    @Test
    void testGrpcTransportWithInvalidTimeout() {
        GrpcTransportConfig grpcTransport = new GrpcTransportConfig(
            "document-processor",
            Map.of("timeout", "not-a-number") // Invalid timeout
        );
        
        PipelineStepConfig.OutputTarget output = new PipelineStepConfig.OutputTarget(
            "processor",
            TransportType.GRPC,
            grpcTransport,
            null
        );
        
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "service", null
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "reader",
            StepType.INITIAL_PIPELINE,
            "Test step",
            null, null,
            Collections.emptyList(),
            Map.of("default", output),
            null, null, null, null, null,
            processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("step1", step)
        );
        
        ValidationResult result = getValidator().validate(config);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0)).contains("timeout must be a valid integer");
    }
    
    @Test
    void testGrpcTransportWithNegativeRetry() {
        GrpcTransportConfig grpcTransport = new GrpcTransportConfig(
            "document-processor",
            Map.of("retry", "-1") // Negative retry
        );
        
        PipelineStepConfig.OutputTarget output = new PipelineStepConfig.OutputTarget(
            "processor",
            TransportType.GRPC,
            grpcTransport,
            null
        );
        
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "service", null
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "reader",
            StepType.INITIAL_PIPELINE,
            "Test step",
            null, null,
            Collections.emptyList(),
            Map.of("default", output),
            null, null, null, null, null,
            processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("step1", step)
        );
        
        ValidationResult result = getValidator().validate(config);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0)).contains("retry count cannot be negative");
    }
}