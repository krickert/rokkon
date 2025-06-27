package com.rokkon.pipeline.validation.validators;

import com.rokkon.pipeline.config.model.*;
import com.rokkon.pipeline.validation.DELET_ME_I_SHOULD_USE_INTERFACE_OR_MOCK_OR_DEFAULT_ValidationResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class TransportConfigValidatorTestBase {
    
    protected abstract TransportConfigValidator getValidator();
    
    @Test
    void testValidatorPriorityAndName() {
        TransportConfigValidator validator = getValidator();
        assertThat(validator.getPriority()).isEqualTo(350);
        assertThat(validator.getValidatorName()).isEqualTo("TransportConfigValidator");
    }
    
    @Test
    void testNullPipelineConfiguration() {
        DELET_ME_I_SHOULD_USE_INTERFACE_OR_MOCK_OR_DEFAULT_ValidationResult result = getValidator().validate(null);
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
        assertThat(result.warnings()).isEmpty();
    }
    
    @Test
    void testValidKafkaInput() {
        KafkaInputDefinition kafkaInput = new KafkaInputDefinition(
            List.of("test-topic"),
            "test-consumer-group",
            null
        );
        
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "service", null
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "processor",
            StepType.PIPELINE,
            "Test step",
            null, null,
            List.of(kafkaInput),
            Collections.emptyMap(),
            null, null, null, null, null,
            processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("step1", step)
        );
        
        DELET_ME_I_SHOULD_USE_INTERFACE_OR_MOCK_OR_DEFAULT_ValidationResult result = getValidator().validate(config);
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
        assertThat(result.warnings()).isEmpty();
    }
    
    @Test
    void testKafkaInputWithBlankConsumerGroup() {
        KafkaInputDefinition kafkaInput = new KafkaInputDefinition(
            List.of("test-topic"),
            "", // Blank consumer group
            null
        );
        
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "service", null
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "processor",
            StepType.PIPELINE,
            "Test step",
            null, null,
            List.of(kafkaInput),
            Collections.emptyMap(),
            null, null, null, null, null,
            processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("step1", step)
        );
        
        DELET_ME_I_SHOULD_USE_INTERFACE_OR_MOCK_OR_DEFAULT_ValidationResult result = getValidator().validate(config);
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
        assertThat(result.warnings()).hasSize(1);
        assertThat(result.warnings().get(0)).contains("Consumer group ID is blank");
    }
    
    @Test
    void testKafkaInputManyTopicsWarning() {
        List<String> manyTopics = List.of(
            "topic1", "topic2", "topic3", "topic4", "topic5",
            "topic6", "topic7", "topic8", "topic9", "topic10", "topic11"
        );
        
        KafkaInputDefinition kafkaInput = new KafkaInputDefinition(
            manyTopics,
            "test-consumer-group",
            null
        );
        
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "service", null
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "processor",
            StepType.PIPELINE,
            "Test step",
            null, null,
            List.of(kafkaInput),
            Collections.emptyMap(),
            null, null, null, null, null,
            processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("step1", step)
        );
        
        DELET_ME_I_SHOULD_USE_INTERFACE_OR_MOCK_OR_DEFAULT_ValidationResult result = getValidator().validate(config);
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
        assertThat(result.warnings()).hasSize(1);
        assertThat(result.warnings().get(0)).contains("Subscribing to many topics");
    }
    
    @Test
    void testValidKafkaTransport() {
        KafkaTransportConfig kafkaTransport = new KafkaTransportConfig(
            "output-topic",
            "pipedocId", // partitionKeyField
            "snappy", // compressionType
            16384, // batchSize
            10, // lingerMs
            null // kafkaProducerProperties
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
        
        DELET_ME_I_SHOULD_USE_INTERFACE_OR_MOCK_OR_DEFAULT_ValidationResult result = getValidator().validate(config);
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
        assertThat(result.warnings()).isEmpty();
    }
    
    @Test
    void testKafkaTransportLargeBatchSize() {
        KafkaTransportConfig kafkaTransport = new KafkaTransportConfig(
            "output-topic",
            null,
            null,
            2000000, // Very large batch size (2MB)
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
        
        DELET_ME_I_SHOULD_USE_INTERFACE_OR_MOCK_OR_DEFAULT_ValidationResult result = getValidator().validate(config);
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
        assertThat(result.warnings()).hasSize(1);
        assertThat(result.warnings().get(0)).contains("Very large batch size");
    }
    
    @Test
    void testKafkaTransportHighLingerMs() {
        KafkaTransportConfig kafkaTransport = new KafkaTransportConfig(
            "output-topic",
            null,
            null,
            null,
            2000, // High linger ms
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
        
        DELET_ME_I_SHOULD_USE_INTERFACE_OR_MOCK_OR_DEFAULT_ValidationResult result = getValidator().validate(config);
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
        assertThat(result.warnings()).hasSize(1);
        assertThat(result.warnings().get(0)).contains("High linger ms");
    }
    
    @Test
    void testValidGrpcTransport() {
        GrpcTransportConfig grpcTransport = new GrpcTransportConfig(
            "document-processor",
            Map.of("timeout", "5000") // 5 second timeout
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
        
        DELET_ME_I_SHOULD_USE_INTERFACE_OR_MOCK_OR_DEFAULT_ValidationResult result = getValidator().validate(config);
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
        assertThat(result.warnings()).isEmpty();
    }
    
    @Test
    void testGrpcTransportShortTimeout() {
        GrpcTransportConfig grpcTransport = new GrpcTransportConfig(
            "document-processor",
            Map.of("timeout", "50") // Very short
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
        
        DELET_ME_I_SHOULD_USE_INTERFACE_OR_MOCK_OR_DEFAULT_ValidationResult result = getValidator().validate(config);
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
        assertThat(result.warnings()).hasSize(1);
        assertThat(result.warnings().get(0)).contains("Very short timeout");
    }
    
    // Note: Transport type mismatch test removed as the model enforces this validation
    // The OutputTarget constructor throws IllegalArgumentException if transport type doesn't match config
    
    @Test
    void testKafkaConfigValidation() {
        Map<String, String> kafkaConsumerProps = Map.of(
            "max.poll.records", "5001", // Very high max poll records
            "session.timeout.ms", "3000" // Very short session timeout
        );
        
        KafkaInputDefinition kafkaInput = new KafkaInputDefinition(
            List.of("test-topic"),
            "consumer-group",
            kafkaConsumerProps
        );
        
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "service", null
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "processor",
            StepType.PIPELINE,
            "Test step",
            null, null,
            List.of(kafkaInput),
            Collections.emptyMap(),
            null, null, null, null, null,
            processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("step1", step)
        );
        
        DELET_ME_I_SHOULD_USE_INTERFACE_OR_MOCK_OR_DEFAULT_ValidationResult result = getValidator().validate(config);
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
        assertThat(result.warnings()).hasSize(2);
        assertThat(result.warnings()).anyMatch(w -> w.contains("Very high max.poll.records"));
        assertThat(result.warnings()).anyMatch(w -> w.contains("session.timeout.ms less than 6 seconds"));
    }
}