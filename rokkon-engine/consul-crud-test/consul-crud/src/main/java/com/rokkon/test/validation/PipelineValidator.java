package com.rokkon.test.validation;

import com.rokkon.test.model.PipelineConfig;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple pipeline validator
 */
@ApplicationScoped
public class PipelineValidator {
    
    public ValidationResult validate(PipelineConfig config) {
        List<String> errors = new ArrayList<>();
        
        if (config == null) {
            return ValidationResult.failure("Pipeline configuration cannot be null");
        }
        
        if (config.getPipelineName() == null || config.getPipelineName().isBlank()) {
            errors.add("Pipeline name is required");
        }
        
        if (config.getPipelineSteps() == null || config.getPipelineSteps().isEmpty()) {
            errors.add("Pipeline must have at least one step");
        } else {
            // Validate each step
            config.getPipelineSteps().forEach((stepId, step) -> {
                if (step.getStepName() == null || step.getStepName().isBlank()) {
                    errors.add("Step " + stepId + " must have a name");
                }
                if (step.getStepType() == null || step.getStepType().isBlank()) {
                    errors.add("Step " + stepId + " must have a type");
                }
            });
        }
        
        return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);
    }
}