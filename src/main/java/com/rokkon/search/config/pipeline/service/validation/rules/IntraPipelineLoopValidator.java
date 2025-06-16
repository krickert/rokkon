package com.rokkon.search.config.pipeline.service.validation.rules;

import com.rokkon.search.config.pipeline.model.PipelineConfig;
import com.rokkon.search.config.pipeline.service.validation.StructuralValidationRule;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates that there are no circular dependencies within a pipeline.
 * TODO: Implement full loop detection logic from old system.
 */
public class IntraPipelineLoopValidator implements StructuralValidationRule {
    
    @Override
    public List<String> validate(String pipelineId, PipelineConfig config) {
        List<String> errors = new ArrayList<>();
        
        // TODO: Implement comprehensive loop detection
        // For now, just basic validation that we have steps
        if (config.pipelineSteps() == null || config.pipelineSteps().isEmpty()) {
            return errors; // Let other validators handle this
        }
        
        // Placeholder for future loop detection logic
        // The old system had sophisticated graph traversal logic
        
        return errors;
    }
    
    @Override
    public int getPriority() {
        return 60;
    }
}