package com.rokkon.pipeline.validation.validators;

import com.rokkon.pipeline.config.model.PipelineConfig;
import com.rokkon.pipeline.config.model.PipelineStepConfig;
import com.rokkon.pipeline.config.model.KafkaTransportConfig;
import com.rokkon.pipeline.config.model.TransportType;
import com.rokkon.pipeline.config.model.StepType;
import com.rokkon.pipeline.config.model.KafkaInputDefinition;
import com.rokkon.pipeline.validation.DELET_ME_I_SHOULD_USE_INTERFACE_OR_MOCK_OR_DEFAULT_ValidationResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class NamingConventionValidatorTestBase {
    
    protected abstract NamingConventionValidator getValidator();
    
    @Test
    void testValidatorPriorityAndName() {
        NamingConventionValidator validator = getValidator();
        assertThat(validator.getPriority()).isEqualTo(200);
        assertThat(validator.getValidatorName()).isEqualTo("NamingConventionValidator");
    }
    
    @Test
    void testNullPipelineConfiguration() {
        DELET_ME_I_SHOULD_USE_INTERFACE_OR_MOCK_OR_DEFAULT_ValidationResult result = getValidator().validate(null);
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
        assertThat(result.warnings()).isEmpty();
    }
    
    @Test
    void testValidPipelineNameConvention() {
        PipelineConfig config = new PipelineConfig(
            "document-processing",
            Collections.emptyMap()
        );
        
        DELET_ME_I_SHOULD_USE_INTERFACE_OR_MOCK_OR_DEFAULT_ValidationResult result = getValidator().validate(config);
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }
    
    @Test
    void testInvalidPipelineNameWithDots() {
        PipelineConfig config = new PipelineConfig(
            "document.processing",
            Collections.emptyMap()
        );
        
        DELET_ME_I_SHOULD_USE_INTERFACE_OR_MOCK_OR_DEFAULT_ValidationResult result = getValidator().validate(config);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).hasSize(2);
        assertThat(result.errors().get(0)).contains("cannot contain dots - dots are reserved as delimiters");
        assertThat(result.errors().get(1)).contains("must contain only alphanumeric characters and hyphens");
    }
    
    @Test
    void testInvalidPipelineNameWithSpecialCharacters() {
        PipelineConfig config = new PipelineConfig(
            "document_processing@test",
            Collections.emptyMap()
        );
        
        DELET_ME_I_SHOULD_USE_INTERFACE_OR_MOCK_OR_DEFAULT_ValidationResult result = getValidator().validate(config);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0)).contains("must contain only alphanumeric characters and hyphens");
    }
    
    @Test
    void testLongPipelineNameWarning() {
        String longName = "a".repeat(51);
        PipelineConfig config = new PipelineConfig(
            longName,
            Collections.emptyMap()
        );
        
        DELET_ME_I_SHOULD_USE_INTERFACE_OR_MOCK_OR_DEFAULT_ValidationResult result = getValidator().validate(config);
        assertThat(result.valid()).isTrue();
        assertThat(result.warnings()).hasSize(1);
        assertThat(result.warnings().get(0)).contains("longer than 50 characters");
    }
    
    @Test
    void testValidStepNameConvention() {
        // Create a minimal ProcessorInfo for testing
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "test-service", 
            null
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "document-parser",
            StepType.PIPELINE,
            "Test step",
            null,
            null,
            Collections.emptyList(),
            Collections.emptyMap(),
            null,
            null,
            null,
            null,
            null,
            processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "document-processing",
            Map.of("step1", step)
        );
        
        DELET_ME_I_SHOULD_USE_INTERFACE_OR_MOCK_OR_DEFAULT_ValidationResult result = getValidator().validate(config);
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }
    
    @Test
    void testInvalidStepNameWithDots() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "test-service",
            null
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "document.parser",
            StepType.PIPELINE,
            "Test step",
            null,
            null,
            Collections.emptyList(),
            Collections.emptyMap(),
            null,
            null,
            null,
            null,
            null,
            processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "document-processing",
            Map.of("step1", step)
        );
        
        DELET_ME_I_SHOULD_USE_INTERFACE_OR_MOCK_OR_DEFAULT_ValidationResult result = getValidator().validate(config);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).hasSize(2);
        assertThat(result.errors().get(0)).contains("cannot contain dots - dots are reserved as delimiters");
        assertThat(result.errors().get(1)).contains("must contain only alphanumeric characters and hyphens");
    }
    
    @Test
    void testValidTopicNamingConvention() {
        KafkaTransportConfig kafka = new KafkaTransportConfig(
            "document-processing.parser.input",
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
            kafka
        );
        
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "test-service",
            null
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "parser",
            StepType.PIPELINE,
            "Test step",
            null,
            null,
            Collections.emptyList(),
            Map.of("default", output),
            null,
            null,
            null,
            null,
            null,
            processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "document-processing",
            Map.of("step1", step)
        );
        
        DELET_ME_I_SHOULD_USE_INTERFACE_OR_MOCK_OR_DEFAULT_ValidationResult result = getValidator().validate(config);
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
        assertThat(result.warnings()).isEmpty();
    }
    
    @Test
    void testInvalidTopicNamingPattern() {
        KafkaTransportConfig kafka = new KafkaTransportConfig(
            "wrong-pattern",
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
            kafka
        );
        
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "test-service",
            null
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "parser",
            StepType.PIPELINE,
            "Test step",
            null,
            null,
            Collections.emptyList(),
            Map.of("default", output),
            null,
            null,
            null,
            null,
            null,
            processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "document-processing",
            Map.of("step1", step)
        );
        
        DELET_ME_I_SHOULD_USE_INTERFACE_OR_MOCK_OR_DEFAULT_ValidationResult result = getValidator().validate(config);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).hasSize(2);
        assertThat(result.errors().get(0)).contains("doesn't follow the required naming pattern");
        assertThat(result.errors().get(1)).contains("DLQ topic");
    }
    
    @Test
    void testValidConsumerGroupNaming() {
        KafkaInputDefinition kafkaInput = new KafkaInputDefinition(
            List.of("test-topic"),
            "document-processing.consumer-group",
            null
        );
        
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "test-service",
            null
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "parser",
            StepType.PIPELINE,
            "Test step",
            null,
            null,
            List.of(kafkaInput),
            Collections.emptyMap(),
            null,
            null,
            null,
            null,
            null,
            processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "document-processing",
            Map.of("step1", step)
        );
        
        DELET_ME_I_SHOULD_USE_INTERFACE_OR_MOCK_OR_DEFAULT_ValidationResult result = getValidator().validate(config);
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
        assertThat(result.warnings()).isEmpty();
    }
    
    @Test
    void testInvalidConsumerGroupNaming() {
        KafkaInputDefinition kafkaInput = new KafkaInputDefinition(
            List.of("test-topic"),
            "wrong-consumer-group",
            null
        );
        
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "test-service",
            null
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "parser",
            StepType.PIPELINE,
            "Test step",
            null,
            null,
            List.of(kafkaInput),
            Collections.emptyMap(),
            null,
            null,
            null,
            null,
            null,
            processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "document-processing",
            Map.of("step1", step)
        );
        
        DELET_ME_I_SHOULD_USE_INTERFACE_OR_MOCK_OR_DEFAULT_ValidationResult result = getValidator().validate(config);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0)).contains("doesn't follow the required naming pattern");
    }
}