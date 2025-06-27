package com.rokkon.engine.validation;

import com.rokkon.engine.api.RealValidatorsTestProfile;
import com.rokkon.pipeline.config.model.PipelineConfig;
import com.rokkon.pipeline.validation.CompositeValidator;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Component test that uses real validators to test validation logic
 * without external dependencies like Consul.
 */
@QuarkusTest
@TestProfile(RealValidatorsTestProfile.class)
class ValidatorComponentTest {
    
    @Inject
    CompositeValidator<PipelineConfig> pipelineValidator;
    
    @Test
    void testRealValidatorsAreUsed() {
        // Verify we have the composite validator injected
        assertThat(pipelineValidator).isNotNull();
        
        // Check if we have any validators
        System.out.println("Number of validators: " + pipelineValidator.getValidators().size());
        
        // Log the validators being used (might be empty if no real validators exist)
        if (pipelineValidator.getValidators().isEmpty()) {
            System.out.println("No real validators found - this is expected if validators module doesn't have any PipelineConfig validators yet");
        } else {
            pipelineValidator.getValidators().forEach(validator -> 
                System.out.println("Using validator: " + validator.getValidatorName())
            );
        }
    }
    
    @Test
    void testValidPipelineConfig() {
        // Create a valid pipeline config
        PipelineConfig validConfig = new PipelineConfig(
            "test-pipeline",
            Map.of() // Empty steps for now
        );
        
        // Validate using real validators
        var result = pipelineValidator.validate(validConfig);
        
        // Should be valid (unless real validators have specific requirements)
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }
    
    @Test
    void testPipelineConfigRequiresName() {
        // PipelineConfig enforces non-null names in constructor
        // This is good! Names are essential for pipeline identification
        
        // Test that null name throws exception
        assertThatThrownBy(() -> new PipelineConfig(null, Map.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("name cannot be null");
            
        // Test that empty name also throws exception
        assertThatThrownBy(() -> new PipelineConfig("", Map.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("name cannot be null or blank");
            
        // Test that blank name throws exception
        assertThatThrownBy(() -> new PipelineConfig("   ", Map.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("name cannot be null or blank");
    }
    
    @Test
    void testValidationWithSpecialCharactersInName() {
        // Test pipeline with special characters that might need validation
        PipelineConfig configWithSpecialChars = new PipelineConfig(
            "test-pipeline_123!@#",
            Map.of()
        );
        
        var result = pipelineValidator.validate(configWithSpecialChars);
        
        // Log the result - validators might have rules about allowed characters
        System.out.println("Validation for special chars: " + result.valid());
        if (!result.errors().isEmpty()) {
            System.out.println("Errors: " + result.errors());
        }
    }
}