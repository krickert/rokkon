package com.rokkon.pipeline.validation.validators;

import com.rokkon.pipeline.config.model.*;
import com.rokkon.pipeline.validation.DELET_ME_I_SHOULD_USE_INTERFACE_OR_MOCK_OR_DEFAULT_ValidationResult;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class StepReferenceValidatorTestBase {
    
    protected abstract StepReferenceValidator getValidator();
    
    @Test
    void testValidatorPriorityAndName() {
        StepReferenceValidator validator = getValidator();
        assertThat(validator.getPriority()).isEqualTo(400);
        assertThat(validator.getValidatorName()).isEqualTo("StepReferenceValidator");
    }
    
    @Test
    void testNullPipelineConfiguration() {
        DELET_ME_I_SHOULD_USE_INTERFACE_OR_MOCK_OR_DEFAULT_ValidationResult result = getValidator().validate(null);
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
        assertThat(result.warnings()).isEmpty();
    }
    
    @Test
    void testEmptyPipelineSteps() {
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Collections.emptyMap()
        );
        
        DELET_ME_I_SHOULD_USE_INTERFACE_OR_MOCK_OR_DEFAULT_ValidationResult result = getValidator().validate(config);
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
        assertThat(result.warnings()).isEmpty();
    }
    
    @Test
    void testValidInternalGrpcReferences() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "service",
            null
        );
        
        // Create steps with internal gRPC references
        GrpcTransportConfig grpcTransport = new GrpcTransportConfig(
            "step2", // References another step in the pipeline
            null
        );
        
        PipelineStepConfig.OutputTarget grpcOutput = new PipelineStepConfig.OutputTarget(
            "step2",
            TransportType.GRPC,
            grpcTransport,
            null
        );
        
        PipelineStepConfig step1 = new PipelineStepConfig(
            "processor1",
            StepType.PIPELINE,
            "First processor",
            null, null,
            Collections.emptyList(),
            Map.of("default", grpcOutput),
            null, null, null, null, null,
            processorInfo
        );
        
        PipelineStepConfig step2 = new PipelineStepConfig(
            "processor2",
            StepType.PIPELINE,
            "Second processor",
            null, null,
            Collections.emptyList(),
            Collections.emptyMap(),
            null, null, null, null, null,
            processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("step1", step1, "step2", step2)
        );
        
        DELET_ME_I_SHOULD_USE_INTERFACE_OR_MOCK_OR_DEFAULT_ValidationResult result = getValidator().validate(config);
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }
    
    @Test
    void testInvalidInternalGrpcReference() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "service",
            null
        );
        
        // Create step with reference to non-existent step
        GrpcTransportConfig grpcTransport = new GrpcTransportConfig(
            "nonexistent", // References a step that doesn't exist
            null
        );
        
        PipelineStepConfig.OutputTarget grpcOutput = new PipelineStepConfig.OutputTarget(
            "nonexistent",
            TransportType.GRPC,
            grpcTransport,
            null
        );
        
        PipelineStepConfig step1 = new PipelineStepConfig(
            "processor1",
            StepType.PIPELINE,
            "Processor with bad reference",
            null, null,
            Collections.emptyList(),
            Map.of("default", grpcOutput),
            null, null, null, null, null,
            processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("step1", step1)
        );
        
        DELET_ME_I_SHOULD_USE_INTERFACE_OR_MOCK_OR_DEFAULT_ValidationResult result = getValidator().validate(config);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0)).contains("Step 'step1' output 'default' references non-existent target step 'nonexistent'");
    }
    
    @Test
    void testExternalGrpcReferenceIgnored() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "service",
            null
        );
        
        // Create step with external gRPC reference (contains dots, so it's a FQDN)
        GrpcTransportConfig grpcTransport = new GrpcTransportConfig(
            "external.service.com", // External service - should be ignored
            null
        );
        
        PipelineStepConfig.OutputTarget grpcOutput = new PipelineStepConfig.OutputTarget(
            "external-service",
            TransportType.GRPC,
            grpcTransport,
            null
        );
        
        PipelineStepConfig step1 = new PipelineStepConfig(
            "processor1",
            StepType.PIPELINE,
            "Processor with external reference",
            null, null,
            Collections.emptyList(),
            Map.of("default", grpcOutput),
            null, null, null, null, null,
            processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("step1", step1)
        );
        
        DELET_ME_I_SHOULD_USE_INTERFACE_OR_MOCK_OR_DEFAULT_ValidationResult result = getValidator().validate(config);
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }
    
    @Test
    void testDuplicateStepNames() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "service",
            null
        );
        
        // Create two steps with the same stepName
        PipelineStepConfig step1 = new PipelineStepConfig(
            "duplicate-name", // Same step name
            StepType.PIPELINE,
            "First processor",
            null, null,
            Collections.emptyList(),
            Collections.emptyMap(),
            null, null, null, null, null,
            processorInfo
        );
        
        PipelineStepConfig step2 = new PipelineStepConfig(
            "duplicate-name", // Same step name
            StepType.PIPELINE,
            "Second processor",
            null, null,
            Collections.emptyList(),
            Collections.emptyMap(),
            null, null, null, null, null,
            processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("step1", step1, "step2", step2)
        );
        
        DELET_ME_I_SHOULD_USE_INTERFACE_OR_MOCK_OR_DEFAULT_ValidationResult result = getValidator().validate(config);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0)).contains("Duplicate step name found: duplicate-name");
    }
    
    @Test
    void testKafkaTransportIgnored() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "service",
            null
        );
        
        // Create step with Kafka transport (should be ignored by this validator)
        KafkaTransportConfig kafkaTransport = new KafkaTransportConfig(
            "some.topic",
            null, null, null, null, null
        );
        
        PipelineStepConfig.OutputTarget kafkaOutput = new PipelineStepConfig.OutputTarget(
            "kafka-target",
            TransportType.KAFKA,
            null,
            kafkaTransport
        );
        
        PipelineStepConfig step1 = new PipelineStepConfig(
            "processor1",
            StepType.PIPELINE,
            "Processor with Kafka output",
            null, null,
            Collections.emptyList(),
            Map.of("default", kafkaOutput),
            null, null, null, null, null,
            processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("step1", step1)
        );
        
        DELET_ME_I_SHOULD_USE_INTERFACE_OR_MOCK_OR_DEFAULT_ValidationResult result = getValidator().validate(config);
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }
    
    @Test
    void testMultipleOutputsWithMixedReferences() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "service",
            null
        );
        
        // Create valid target step
        PipelineStepConfig targetStep = new PipelineStepConfig(
            "target",
            StepType.PIPELINE,
            "Target processor",
            null, null,
            Collections.emptyList(),
            Collections.emptyMap(),
            null, null, null, null, null,
            processorInfo
        );
        
        // Create step with multiple outputs
        GrpcTransportConfig validGrpc = new GrpcTransportConfig("target", null);
        GrpcTransportConfig invalidGrpc = new GrpcTransportConfig("invalid", null);
        GrpcTransportConfig externalGrpc = new GrpcTransportConfig("external.service.com", null);
        
        Map<String, PipelineStepConfig.OutputTarget> outputs = Map.of(
            "valid", new PipelineStepConfig.OutputTarget("target", TransportType.GRPC, validGrpc, null),
            "invalid", new PipelineStepConfig.OutputTarget("invalid", TransportType.GRPC, invalidGrpc, null),
            "external", new PipelineStepConfig.OutputTarget("external", TransportType.GRPC, externalGrpc, null)
        );
        
        PipelineStepConfig step1 = new PipelineStepConfig(
            "processor1",
            StepType.PIPELINE,
            "Processor with multiple outputs",
            null, null,
            Collections.emptyList(),
            outputs,
            null, null, null, null, null,
            processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("step1", step1, "target", targetStep)
        );
        
        DELET_ME_I_SHOULD_USE_INTERFACE_OR_MOCK_OR_DEFAULT_ValidationResult result = getValidator().validate(config);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0)).contains("Step 'step1' output 'invalid' references non-existent target step 'invalid'");
    }
}