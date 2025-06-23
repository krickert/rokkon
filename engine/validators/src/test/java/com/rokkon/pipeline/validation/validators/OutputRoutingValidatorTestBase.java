package com.rokkon.pipeline.validation.validators;

import com.rokkon.pipeline.config.model.*;
import com.rokkon.pipeline.validation.ValidationResult;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public abstract class OutputRoutingValidatorTestBase {

    protected abstract OutputRoutingValidator getValidator();

    @Test
    void testNullPipelineConfiguration() {
        ValidationResult result = getValidator().validate(null);
        
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).containsExactly("Pipeline configuration or steps cannot be null");
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void testValidOutputRouting() {
        PipelineStepConfig.ProcessorInfo processorInfo1 = new PipelineStepConfig.ProcessorInfo(
            "test-service", null
        );
        
        PipelineStepConfig.ProcessorInfo processorInfo2 = new PipelineStepConfig.ProcessorInfo(
            "another-service", null
        );
        
        KafkaTransportConfig kafkaTransport = new KafkaTransportConfig(
            "test.topic", null, null, null, null, Map.of()
        );
        
        PipelineStepConfig.OutputTarget output = new PipelineStepConfig.OutputTarget(
            "step2", TransportType.KAFKA, null, kafkaTransport
        );
        
        PipelineStepConfig step1 = new PipelineStepConfig(
            "step1", 
            StepType.PIPELINE, 
            "First step",
            null, null, null,
            Map.of("default", output),
            null, null, null, null, null,
            processorInfo1
        );
        
        PipelineStepConfig step2 = new PipelineStepConfig(
            "step2", 
            StepType.SINK, 
            "Sink step",
            null, null, null,
            Map.of(),  // SINK has no outputs
            null, null, null, null, null,
            processorInfo2
        );

        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("step1", step1, "step2", step2)
        );

        ValidationResult result = getValidator().validate(config);
        
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void testTargetStepDoesNotExist() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "test-service", null
        );
        
        KafkaTransportConfig kafkaTransport = new KafkaTransportConfig(
            "test.topic", null, null, null, null, Map.of()
        );
        
        PipelineStepConfig.OutputTarget output = new PipelineStepConfig.OutputTarget(
            "non-existent-step", TransportType.KAFKA, null, kafkaTransport
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "test-step", 
            StepType.PIPELINE, 
            "Test step",
            null, null, null,
            Map.of("default", output),
            null, null, null, null, null,
            processorInfo
        );

        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("test-step", step)
        );

        ValidationResult result = getValidator().validate(config);
        
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).containsExactly(
            "Step 'test-step' output 'default': Target step 'non-existent-step' does not exist in pipeline"
        );
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void testSinkStepWithOutputs() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "test-service", null
        );
        
        KafkaTransportConfig kafkaTransport = new KafkaTransportConfig(
            "test.topic", null, null, null, null, Map.of()
        );
        
        PipelineStepConfig.OutputTarget output = new PipelineStepConfig.OutputTarget(
            "another-step", TransportType.KAFKA, null, kafkaTransport
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "sink-step", 
            StepType.SINK, 
            "Sink step",
            null, null, null,
            Map.of("default", output),  // SINK should not have outputs
            null, null, null, null, null,
            processorInfo
        );

        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("sink-step", step)
        );

        ValidationResult result = getValidator().validate(config);
        
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).containsExactlyInAnyOrder(
            "Step 'sink-step': SINK steps should not have outputs",
            "Step 'sink-step' output 'default': Target step 'another-step' does not exist in pipeline"
        );
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void testNonSinkStepWithoutOutputs() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "test-service", null
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "test-step", 
            StepType.PIPELINE, 
            "Test step",
            null, null, null,
            Map.of(),  // No outputs for non-SINK step
            null, null, null, null, null,
            processorInfo
        );

        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("test-step", step)
        );

        ValidationResult result = getValidator().validate(config);
        
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
        assertThat(result.warnings()).containsExactly(
            "Step 'test-step': No outputs defined for non-SINK step"
        );
    }

    @Test
    void testKafkaTransportMissingConfig() {
        // This test can't be done because OutputTarget constructor validates transport config
        // The model prevents creation of invalid configs (KAFKA type without kafka config)
        // OutputTarget constructor throws IllegalArgumentException:
        // "KafkaTransportConfig must be provided when transportType is KAFKA"
        
        // Test that the model validation works as expected
        assertThatThrownBy(() -> new PipelineStepConfig.OutputTarget(
            "target-step", TransportType.KAFKA, null, null  // Missing Kafka config
        ))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("KafkaTransportConfig must be provided when transportType is KAFKA");
    }

    @Test
    void testGrpcTransportMissingConfig() {
        // This test can't be done because OutputTarget constructor validates transport config
        // The model prevents creation of invalid configs (GRPC type without grpc config)
        // OutputTarget constructor throws IllegalArgumentException:
        // "GrpcTransportConfig must be provided when transportType is GRPC"
        
        assertThatThrownBy(() -> new PipelineStepConfig.OutputTarget(
            "target-step", TransportType.GRPC, null, null  // Missing gRPC config
        ))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("GrpcTransportConfig must be provided when transportType is GRPC");
    }

    @Test
    void testMismatchedTransportConfig() {
        // This test can't be done because OutputTarget constructor validates transport config
        // The model prevents having both gRPC and Kafka configs when transport type is KAFKA
        // OutputTarget constructor throws IllegalArgumentException:
        // "GrpcTransportConfig should only be provided when transportType is GRPC"
        
        KafkaTransportConfig kafkaTransport = new KafkaTransportConfig(
            "test.topic", null, null, null, null, Map.of()
        );
        
        GrpcTransportConfig grpcTransport = new GrpcTransportConfig(
            "grpc-service", Map.of("timeout", "5000")
        );
        
        assertThatThrownBy(() -> new PipelineStepConfig.OutputTarget(
            "target-step", TransportType.KAFKA, grpcTransport, kafkaTransport
        ))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("GrpcTransportConfig should only be provided when transportType is GRPC");
    }

    @Test
    void testSingleOutputNotNamedDefault() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "test-service", null
        );
        
        KafkaTransportConfig kafkaTransport = new KafkaTransportConfig(
            "test.topic", null, null, null, null, Map.of()
        );
        
        PipelineStepConfig.OutputTarget output = new PipelineStepConfig.OutputTarget(
            "target-step", TransportType.KAFKA, null, kafkaTransport
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "test-step", 
            StepType.PIPELINE, 
            "Test step",
            null, null, null,
            Map.of("custom-output", output),  // Single output not named "default"
            null, null, null, null, null,
            processorInfo
        );

        // Add the target step to avoid "does not exist" errors
        PipelineStepConfig targetStep = new PipelineStepConfig(
            "target-step", 
            StepType.SINK, 
            "Target step",
            null, null, null,
            Map.of(),  // SINK has no outputs
            null, null, null, null, null,
            processorInfo
        );

        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("test-step", step, "target-step", targetStep)
        );

        ValidationResult result = getValidator().validate(config);
        
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
        assertThat(result.warnings()).containsExactly(
            "Step 'test-step': Single output should be named 'default' for clarity"
        );
    }

    @Test
    void testDuplicateOutputNames() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "test-service", null
        );
        
        KafkaTransportConfig kafkaTransport = new KafkaTransportConfig(
            "test.topic", null, null, null, null, Map.of()
        );
        
        PipelineStepConfig.OutputTarget output1 = new PipelineStepConfig.OutputTarget(
            "target-step", TransportType.KAFKA, null, kafkaTransport
        );
        
        PipelineStepConfig.OutputTarget output2 = new PipelineStepConfig.OutputTarget(
            "target-step", TransportType.KAFKA, null, kafkaTransport
        );
        
        Map<String, PipelineStepConfig.OutputTarget> outputs = new HashMap<>();
        outputs.put("output", output1);
        outputs.put("OUTPUT", output2);  // Case-insensitive duplicate
        
        PipelineStepConfig step = new PipelineStepConfig(
            "test-step", 
            StepType.PIPELINE, 
            "Test step",
            null, null, null,
            outputs,
            null, null, null, null, null,
            processorInfo
        );

        // Add the target step to avoid "does not exist" errors
        PipelineStepConfig targetStep = new PipelineStepConfig(
            "target-step", 
            StepType.SINK, 
            "Target step",
            null, null, null,
            Map.of(),  // SINK has no outputs
            null, null, null, null, null,
            processorInfo
        );

        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("test-step", step, "target-step", targetStep)
        );

        ValidationResult result = getValidator().validate(config);
        
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0)).contains("Duplicate output name");
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void testValidatorPriorityAndName() {
        assertThat(getValidator().getPriority()).isEqualTo(80);
        assertThat(getValidator().getValidatorName()).isEqualTo("OutputRoutingValidator");
    }
}