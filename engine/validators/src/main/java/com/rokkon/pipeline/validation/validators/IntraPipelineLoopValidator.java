package com.rokkon.pipeline.validation.validators;

import com.rokkon.pipeline.config.model.PipelineConfig;
import com.rokkon.pipeline.validation.PipelineConfigValidator;
import com.rokkon.pipeline.validation.DefaultValidationResult;
import com.rokkon.pipeline.validation.DELET_ME_I_SHOULD_USE_INTERFACE_OR_MOCK_OR_DEFAULT_ValidationResult;
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
    public DELET_ME_I_SHOULD_USE_INTERFACE_OR_MOCK_OR_DEFAULT_ValidationResult validate(PipelineConfig config) {
        if (config == null || config.pipelineSteps() == null || config.pipelineSteps().isEmpty()) {
            return new DefaultValidationResult(true, List.of(), List.of());
        }
        
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // TODO: Implement comprehensive loop detection
        // For now, just add a warning that this validation is not yet implemented
        // The old system had sophisticated graph traversal logic
        warnings.add("Intra-pipeline loop detection is not yet implemented");
        
        return new DefaultValidationResult(errors.isEmpty(), errors, warnings);
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