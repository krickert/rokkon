package com.rokkon.search.config.pipeline.service.validation;

import com.rokkon.search.config.pipeline.model.PipelineConfig;

import java.util.List;

/**
 * Interface for pipeline structural validation rules.
 * Migrated from the old validation system but adapted for new Record models.
 */
public interface StructuralValidationRule {
    
    /**
     * Validates a single pipeline configuration.
     * 
     * @param pipelineId The ID of the pipeline being validated
     * @param config The pipeline configuration to validate
     * @return List of error messages, empty if valid
     */
    List<String> validate(String pipelineId, PipelineConfig config);
    
    /**
     * Returns the name of this validation rule for error reporting.
     * 
     * @return A human-readable name for this rule
     */
    default String getRuleName() {
        return this.getClass().getSimpleName();
    }
    
    /**
     * Returns the priority of this rule (lower numbers = higher priority).
     * Rules with higher priority are executed first.
     * 
     * @return Priority level (default is 100)
     */
    default int getPriority() {
        return 100;
    }
}