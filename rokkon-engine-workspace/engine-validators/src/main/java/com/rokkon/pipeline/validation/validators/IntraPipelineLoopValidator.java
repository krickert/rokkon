package com.rokkon.pipeline.validation.validators;

import com.rokkon.pipeline.config.model.PipelineConfig;
import com.rokkon.pipeline.validation.PipelineConfigValidator;
import com.rokkon.pipeline.validation.ValidationResult;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates that there are no circular dependencies within a pipeline.
 * TODO: Implement full loop detection logic.
 */
@ApplicationScoped
public class IntraPipelineLoopValidator implements PipelineConfigValidator {
    
    @Override
    public ValidationResult validate(PipelineConfig config) {
        if (config == null || config.pipelineSteps() == null || config.pipelineSteps().isEmpty()) {
            return new ValidationResult(true, List.of(), List.of());
        }
        
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // TODO: Implement comprehensive loop detection
        // For now, just add a warning that this validation is not yet implemented
        // The old system had sophisticated graph traversal logic
        warnings.add("Intra-pipeline loop detection is not yet implemented");
        
        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }
    
    @Override
    public int getPriority() {
        return 600; // Run after reference validation
    }
    
    @Override
    public String getValidatorName() {
        return "IntraPipelineLoopValidator";
    }
}