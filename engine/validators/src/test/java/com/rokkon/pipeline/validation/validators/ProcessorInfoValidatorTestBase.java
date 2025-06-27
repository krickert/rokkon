package com.rokkon.pipeline.validation.validators;

import com.rokkon.pipeline.config.model.*;
import com.rokkon.pipeline.validation.DELET_ME_I_SHOULD_USE_INTERFACE_OR_MOCK_OR_DEFAULT_ValidationResult;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class ProcessorInfoValidatorTestBase {
    
    protected abstract ProcessorInfoValidator getValidator();
    
    @Test
    void testValidatorPriorityAndName() {
        ProcessorInfoValidator validator = getValidator();
        assertThat(validator.getPriority()).isEqualTo(250);
        assertThat(validator.getValidatorName()).isEqualTo("ProcessorInfoValidator");
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
    void testValidGrpcServiceName() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "parser-service",
            null
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "parser",
            StepType.PIPELINE,
            "Parser step",
            null, null,
            Collections.emptyList(),
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
    void testValidInternalBeanName() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            null,
            "documentParserBean"
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "parser",
            StepType.PIPELINE,
            "Parser step",
            null, null,
            Collections.emptyList(),
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
    void testShortGrpcServiceName() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "ab", // Too short
            null
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "parser",
            StepType.PIPELINE,
            "Parser step",
            null, null,
            Collections.emptyList(),
            Collections.emptyMap(),
            null, null, null, null, null,
            processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("step1", step)
        );
        
        DELET_ME_I_SHOULD_USE_INTERFACE_OR_MOCK_OR_DEFAULT_ValidationResult result = getValidator().validate(config);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0)).contains("gRPC service name 'ab' is too short");
    }
    
    @Test
    void testLongGrpcServiceName() {
        String longName = "a".repeat(101);
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            longName,
            null
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "parser",
            StepType.PIPELINE,
            "Parser step",
            null, null,
            Collections.emptyList(),
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
        assertThat(result.warnings().get(0)).contains("gRPC service name").contains("is very long");
    }
    
    @Test
    void testInvalidGrpcServiceNameFormat() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "123-invalid-start", // Starts with number
            null
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "parser",
            StepType.PIPELINE,
            "Parser step",
            null, null,
            Collections.emptyList(),
            Collections.emptyMap(),
            null, null, null, null, null,
            processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("step1", step)
        );
        
        DELET_ME_I_SHOULD_USE_INTERFACE_OR_MOCK_OR_DEFAULT_ValidationResult result = getValidator().validate(config);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0)).contains("should start with a letter");
    }
    
    @Test
    void testLocalhostWarning() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "localhost:8080",
            null
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "parser",
            StepType.PIPELINE,
            "Parser step",
            null, null,
            Collections.emptyList(),
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
        assertThat(result.warnings().get(0)).contains("localhost reference");
    }
    
    @Test
    void testInvalidBeanName() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            null,
            "invalid-bean-name" // Contains hyphens
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "parser",
            StepType.PIPELINE,
            "Parser step",
            null, null,
            Collections.emptyList(),
            Collections.emptyMap(),
            null, null, null, null, null,
            processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("step1", step)
        );
        
        DELET_ME_I_SHOULD_USE_INTERFACE_OR_MOCK_OR_DEFAULT_ValidationResult result = getValidator().validate(config);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0)).contains("must be a valid Java identifier");
    }
    
    @Test
    void testGenericBeanNameWarning() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            null,
            "processor" // Too generic
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "parser",
            StepType.PIPELINE,
            "Parser step",
            null, null,
            Collections.emptyList(),
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
        assertThat(result.warnings().get(0)).contains("too generic");
    }
    
    @Test
    void testMultipleStepsWithMixedIssues() {
        // Valid processor
        PipelineStepConfig.ProcessorInfo validProcessor = new PipelineStepConfig.ProcessorInfo(
            "document-parser.service.com",
            null
        );
        
        // Invalid gRPC name
        PipelineStepConfig.ProcessorInfo invalidGrpc = new PipelineStepConfig.ProcessorInfo(
            "@invalid",
            null
        );
        
        // Invalid bean name
        PipelineStepConfig.ProcessorInfo invalidBean = new PipelineStepConfig.ProcessorInfo(
            null,
            "123bean"
        );
        
        PipelineStepConfig step1 = new PipelineStepConfig(
            "parser",
            StepType.PIPELINE,
            "Parser step",
            null, null,
            Collections.emptyList(),
            Collections.emptyMap(),
            null, null, null, null, null,
            validProcessor
        );
        
        PipelineStepConfig step2 = new PipelineStepConfig(
            "processor",
            StepType.PIPELINE,
            "Processor step",
            null, null,
            Collections.emptyList(),
            Collections.emptyMap(),
            null, null, null, null, null,
            invalidGrpc
        );
        
        PipelineStepConfig step3 = new PipelineStepConfig(
            "writer",
            StepType.SINK,
            "Writer step",
            null, null,
            Collections.emptyList(),
            Collections.emptyMap(),
            null, null, null, null, null,
            invalidBean
        );
        
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("step1", step1, "step2", step2, "step3", step3)
        );
        
        DELET_ME_I_SHOULD_USE_INTERFACE_OR_MOCK_OR_DEFAULT_ValidationResult result = getValidator().validate(config);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).hasSize(2); // Two invalid names
        assertThat(result.warnings()).isEmpty();
    }
}