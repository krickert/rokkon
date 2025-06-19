package com.rokkon.search.config.pipeline.service.validation.rules;

import com.rokkon.search.config.pipeline.model.PipelineConfig;
import com.rokkon.search.config.pipeline.model.PipelineStepConfig;
import com.rokkon.search.config.pipeline.service.validation.StructuralValidationRule;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates naming conventions for pipelines and steps.
 */
public class NamingConventionValidator implements StructuralValidationRule {
    
    private static final String VALID_NAME_PATTERN = "^[a-z][a-z0-9-]*$";
    private static final int MAX_NAME_LENGTH = 63;
    
    @Override
    public List<String> validate(String pipelineId, PipelineConfig config) {
        List<String> errors = new ArrayList<>();
        
        // Validate pipeline ID
        if (pipelineId != null) {
            validateName("Pipeline ID", pipelineId, errors);
        }
        
        // Validate pipeline name
        if (config.name() != null) {
            validateName("Pipeline name", config.name(), errors);
        }
        
        // Validate step names
        if (config.pipelineSteps() != null) {
            for (var entry : config.pipelineSteps().entrySet()) {
                String stepId = entry.getKey();
                PipelineStepConfig step = entry.getValue();
                
                // Validate step ID
                validateName("Step ID '" + stepId + "'", stepId, errors);
                
                // Validate step name
                if (step != null && step.stepName() != null) {
                    validateName("Step name for '" + stepId + "'", step.stepName(), errors);
                }
            }
        }
        
        return errors;
    }
    
    private void validateName(String fieldName, String name, List<String> errors) {
        if (name == null || name.isBlank()) {
            return; // Let RequiredFieldsValidator handle null/blank validation
        }
        
        if (!name.matches(VALID_NAME_PATTERN)) {
            errors.add(fieldName + " must start with lowercase letter and contain only lowercase letters, numbers, and hyphens");
        }
        
        if (name.length() > MAX_NAME_LENGTH) {
            errors.add(fieldName + " cannot exceed " + MAX_NAME_LENGTH + " characters");
        }
        
        if (name.startsWith("-") || name.endsWith("-")) {
            errors.add(fieldName + " cannot start or end with hyphens");
        }
        
        if (name.contains("--")) {
            errors.add(fieldName + " cannot contain consecutive hyphens");
        }
    }
    
    @Override
    public int getPriority() {
        return 20; // Run after required fields validation
    }
}