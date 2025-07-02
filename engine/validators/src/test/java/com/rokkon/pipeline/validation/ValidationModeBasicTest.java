package com.rokkon.pipeline.validation;

import com.rokkon.pipeline.config.model.PipelineConfig;
import com.rokkon.pipeline.validation.validators.ProcessorInfoValidator;
import com.rokkon.pipeline.validation.validators.OutputRoutingValidator;
import com.rokkon.pipeline.validation.validators.RequiredFieldsValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Basic unit tests for validation mode functionality.
 * Tests the core behavior without CDI complexity.
 */
class ValidationModeBasicTest {
    
    private CompositeValidator<PipelineConfig> validator;
    
    @BeforeEach
    void setup() {
        // Create a simple validator that supports all modes
        ConfigValidator<PipelineConfig> allModesValidator = new ConfigValidator<PipelineConfig>() {
            @Override
            public ValidationResult validate(PipelineConfig config) {
                if (config == null) {
                    return ValidationResultFactory.failure("Config is null");
                }
                return ValidationResultFactory.success();
            }
            
            @Override
            public String getValidatorName() {
                return "AllModesValidator";
            }
        };
        
        // Create a validator that only runs in PRODUCTION mode
        ConfigValidator<PipelineConfig> productionOnlyValidator = new ConfigValidator<PipelineConfig>() {
            @Override
            public ValidationResult validate(PipelineConfig config) {
                return ValidationResultFactory.failure("Production-only check failed");
            }
            
            @Override
            public String getValidatorName() {
                return "ProductionOnlyValidator";
            }
            
            @Override
            public Set<ValidationMode> supportedModes() {
                return Set.of(ValidationMode.PRODUCTION);
            }
        };
        
        // Create composite validator with both
        validator = new CompositeValidator<>("TestComposite", 
            List.of(allModesValidator, productionOnlyValidator));
    }
    
    @Test
    void testValidatorSupportsModes() {
        // Test that validators can declare supported modes
        ProcessorInfoValidator processorValidator = new ProcessorInfoValidator();
        assertThat(processorValidator.supportedModes()).containsExactly(ValidationMode.PRODUCTION);
        
        OutputRoutingValidator routingValidator = new OutputRoutingValidator();
        assertThat(routingValidator.supportedModes()).containsExactly(ValidationMode.PRODUCTION);
        
        RequiredFieldsValidator requiredValidator = new RequiredFieldsValidator();
        assertThat(requiredValidator.supportedModes()).containsExactlyInAnyOrder(
            ValidationMode.PRODUCTION, ValidationMode.DESIGN, ValidationMode.TESTING);
    }
    
    @Test
    void testDesignModeSkipsProductionOnlyValidators() {
        PipelineConfig config = new PipelineConfig("test", null);
        
        // Design mode should only run the all-modes validator
        ValidationResult designResult = validator.validate(config, ValidationMode.DESIGN);
        assertThat(designResult.valid()).isTrue();
        assertThat(designResult.hasErrors()).isFalse();
        
        // Production mode should run both validators
        ValidationResult productionResult = validator.validate(config, ValidationMode.PRODUCTION);
        assertThat(productionResult.valid()).isFalse();
        assertThat(productionResult.hasErrors()).isTrue();
        assertThat(productionResult.errors()).contains("Production-only check failed");
    }
    
    @Test
    void testTestingModeSkipsProductionOnlyValidators() {
        PipelineConfig config = new PipelineConfig("test", null);
        
        // Testing mode should only run the all-modes validator
        ValidationResult testingResult = validator.validate(config, ValidationMode.TESTING);
        assertThat(testingResult.valid()).isTrue();
        assertThat(testingResult.hasErrors()).isFalse();
    }
    
    @Test
    void testDefaultModeIsProduction() {
        PipelineConfig config = new PipelineConfig("test", null);
        
        // Default validate() should use PRODUCTION mode
        ValidationResult defaultResult = validator.validate(config);
        ValidationResult productionResult = validator.validate(config, ValidationMode.PRODUCTION);
        
        assertThat(defaultResult.valid()).isEqualTo(productionResult.valid());
        assertThat(defaultResult.errors()).isEqualTo(productionResult.errors());
    }
}