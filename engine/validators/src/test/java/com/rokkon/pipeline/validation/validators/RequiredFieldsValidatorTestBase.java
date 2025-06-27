package com.rokkon.pipeline.validation.validators;

import com.rokkon.pipeline.config.model.*;
import com.rokkon.pipeline.validation.DELET_ME_I_SHOULD_USE_INTERFACE_OR_MOCK_OR_DEFAULT_ValidationResult;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Abstract base test class for RequiredFieldsValidator.
 * Contains all test logic that is shared between unit tests (@QuarkusTest) 
 * and integration tests (@QuarkusIntegrationTest).
 * 
 * Tests focus on business rules that aren't enforced by model constructors.
 */
public abstract class RequiredFieldsValidatorTestBase {
    
    protected abstract RequiredFieldsValidator getValidator();
    
    @Test
    void testValidPipelineConfiguration() {
        // Create a valid pipeline configuration
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "test-service", null
        );
        
        KafkaTransportConfig kafkaTransport = new KafkaTransportConfig(
            "test.topic", null, null, null, null, Map.of()
        );
        
        PipelineStepConfig.OutputTarget output = new PipelineStepConfig.OutputTarget(
            "next-step", TransportType.KAFKA, null, kafkaTransport
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "test-step", StepType.PIPELINE, "Test step description", null, null, null,
            Map.of("default", output), 3, 1000L, 30000L, 2.0, 60000L, processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("step1", step)
        );
        
        DELET_ME_I_SHOULD_USE_INTERFACE_OR_MOCK_OR_DEFAULT_ValidationResult result = getValidator().validate(config);
        
        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
        assertTrue(result.warnings().isEmpty());
    }
    
    @Test
    void testNullPipelineConfiguration() {
        DELET_ME_I_SHOULD_USE_INTERFACE_OR_MOCK_OR_DEFAULT_ValidationResult result = getValidator().validate(null);
        
        assertFalse(result.valid());
        assertEquals(1, result.errors().size());
        assertTrue(result.errors().get(0).contains("Pipeline configuration cannot be null"));
    }
    
    @Test
    void testEmptyPipelineSteps() {
        PipelineConfig config = new PipelineConfig("test-pipeline", Map.of());
        
        DELET_ME_I_SHOULD_USE_INTERFACE_OR_MOCK_OR_DEFAULT_ValidationResult result = getValidator().validate(config);
        
        // Empty pipelines are valid - steps will be added after services are whitelisted
        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
    }
    
    @Test
    void testStepWithoutDescription() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "test-service", null
        );
        
        KafkaTransportConfig kafkaTransport = new KafkaTransportConfig(
            "test.topic", null, null, null, null, Map.of()
        );
        
        PipelineStepConfig.OutputTarget output = new PipelineStepConfig.OutputTarget(
            "next-step", TransportType.KAFKA, null, kafkaTransport
        );
        
        // Step without description
        PipelineStepConfig step = new PipelineStepConfig(
            "test-step", StepType.PIPELINE, null, null, null, null,
            Map.of("default", output), null, null, null, null, null, processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("step1", step)
        );
        
        DELET_ME_I_SHOULD_USE_INTERFACE_OR_MOCK_OR_DEFAULT_ValidationResult result = getValidator().validate(config);
        
        assertTrue(result.valid()); // Valid but has warnings
        assertTrue(result.errors().isEmpty());
        assertEquals(1, result.warnings().size());
        assertTrue(result.warnings().get(0).contains("Step should have a meaningful description"));
    }
    
    @Test
    void testStepWithBlankDescription() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "test-service", null
        );
        
        KafkaTransportConfig kafkaTransport = new KafkaTransportConfig(
            "test.topic", null, null, null, null, Map.of()
        );
        
        PipelineStepConfig.OutputTarget output = new PipelineStepConfig.OutputTarget(
            "next-step", TransportType.KAFKA, null, kafkaTransport
        );
        
        // Step with blank description
        PipelineStepConfig step = new PipelineStepConfig(
            "test-step", StepType.PIPELINE, "   ", null, null, null,
            Map.of("default", output), null, null, null, null, null, processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("step1", step)
        );
        
        DELET_ME_I_SHOULD_USE_INTERFACE_OR_MOCK_OR_DEFAULT_ValidationResult result = getValidator().validate(config);
        
        assertTrue(result.valid()); // Valid but has warnings
        assertTrue(result.errors().isEmpty());
        assertEquals(1, result.warnings().size());
        assertTrue(result.warnings().get(0).contains("Step should have a meaningful description"));
    }
    
    // Note: Tests for missing transport configs are removed because 
    // OutputTarget constructor already enforces these rules
    
    @Test
    void testHighRetryValues() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "test-service", null
        );
        
        KafkaTransportConfig kafkaTransport = new KafkaTransportConfig(
            "test.topic", null, null, null, null, Map.of()
        );
        
        PipelineStepConfig.OutputTarget output = new PipelineStepConfig.OutputTarget(
            "next-step", TransportType.KAFKA, null, kafkaTransport
        );
        
        // High retry values that should generate warnings
        PipelineStepConfig step = new PipelineStepConfig(
            "test-step", StepType.PIPELINE, "Test step", null, null, null,
            Map.of("default", output), 15, 70000L, 400000L, 2.0, 120000L, processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("step1", step)
        );
        
        DELET_ME_I_SHOULD_USE_INTERFACE_OR_MOCK_OR_DEFAULT_ValidationResult result = getValidator().validate(config);
        
        assertTrue(result.valid()); // Valid but has warnings
        assertTrue(result.errors().isEmpty());
        assertEquals(2, result.warnings().size()); // Only 2 warnings now after removing transport validation
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("Max retries (15) is unusually high")));
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("Retry backoff (70000ms) is over 1 minute")));
    }
    
    @Test
    void testInvalidRetryBackoffLogic() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "test-service", null
        );
        
        KafkaTransportConfig kafkaTransport = new KafkaTransportConfig(
            "test.topic", null, null, null, null, Map.of()
        );
        
        PipelineStepConfig.OutputTarget output = new PipelineStepConfig.OutputTarget(
            "next-step", TransportType.KAFKA, null, kafkaTransport
        );
        
        // Initial backoff > max backoff (logical error)
        PipelineStepConfig step = new PipelineStepConfig(
            "test-step", StepType.PIPELINE, "Test step", null, null, null,
            Map.of("default", output), 3, 30000L, 10000L, 2.0, 60000L, processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("step1", step)
        );
        
        DELET_ME_I_SHOULD_USE_INTERFACE_OR_MOCK_OR_DEFAULT_ValidationResult result = getValidator().validate(config);
        
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> 
            e.contains("Initial retry backoff cannot be greater than max retry backoff")));
    }
    
    @Test
    void testValidGrpcConfiguration() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "grpc-service", null
        );
        
        GrpcTransportConfig grpcTransport = new GrpcTransportConfig(
            "target-service", Map.of()
        );
        
        PipelineStepConfig.OutputTarget output = new PipelineStepConfig.OutputTarget(
            "next-step", TransportType.GRPC, grpcTransport, null
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "test-step", StepType.PIPELINE, "Test gRPC step", null, null, null,
            Map.of("grpc-output", output), null, null, null, null, null, processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("step1", step)
        );
        
        DELET_ME_I_SHOULD_USE_INTERFACE_OR_MOCK_OR_DEFAULT_ValidationResult result = getValidator().validate(config);
        
        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
        assertTrue(result.warnings().isEmpty());
    }
    
    @Test
    void testValidInternalProcessorConfiguration() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            null, "internal-bean"
        );
        
        GrpcTransportConfig grpcTransport = new GrpcTransportConfig(
            "target-service", Map.of()
        );
        
        PipelineStepConfig.OutputTarget output = new PipelineStepConfig.OutputTarget(
            "next-step", TransportType.GRPC, grpcTransport, null
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "test-step", StepType.PIPELINE, "Test internal step", null, null, null,
            Map.of("grpc-output", output), null, null, null, null, null, processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("step1", step)
        );
        
        DELET_ME_I_SHOULD_USE_INTERFACE_OR_MOCK_OR_DEFAULT_ValidationResult result = getValidator().validate(config);
        
        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
        assertTrue(result.warnings().isEmpty());
    }
    
    @Test
    void testValidatorPriorityAndName() {
        assertEquals(10, getValidator().getPriority());
        assertEquals("RequiredFieldsValidator", getValidator().getValidatorName());
    }
}