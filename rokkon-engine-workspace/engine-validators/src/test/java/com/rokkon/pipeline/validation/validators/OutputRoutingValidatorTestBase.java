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
        assertThat(result.errors()).containsExactly(
            "Step 'sink-step': SINK steps should not have outputs"
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
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "test-service", null
        );
        
        PipelineStepConfig.OutputTarget output = new PipelineStepConfig.OutputTarget(
            "target-step", TransportType.KAFKA, null, null  // Missing Kafka config
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
            "Step 'test-step' output 'default': Kafka transport config required for KAFKA transport type"
        );
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void testGrpcTransportMissingConfig() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "test-service", null
        );
        
        PipelineStepConfig.OutputTarget output = new PipelineStepConfig.OutputTarget(
            "target-step", TransportType.GRPC, null, null  // Missing gRPC config
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
            "Step 'test-step' output 'default': gRPC transport config required for GRPC transport type"
        );
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void testMismatchedTransportConfig() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "test-service", null
        );
        
        KafkaTransportConfig kafkaTransport = new KafkaTransportConfig(
            "test.topic", null, null, null, null, Map.of()
        );
        
        GrpcTransportConfig grpcTransport = new GrpcTransportConfig(
            "grpc-service", Map.of("timeout", "5000")
        );
        
        // KAFKA type but has gRPC config
        PipelineStepConfig.OutputTarget output = new PipelineStepConfig.OutputTarget(
            "target-step", TransportType.KAFKA, grpcTransport, kafkaTransport
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
        
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
        assertThat(result.warnings()).containsExactly(
            "Step 'test-step' output 'default': gRPC config specified but transport type is KAFKA"
        );
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

        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("test-step", step)
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

        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("test-step", step)
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