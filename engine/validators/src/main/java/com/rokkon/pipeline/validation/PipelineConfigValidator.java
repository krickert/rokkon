package com.rokkon.pipeline.validation;

import com.rokkon.pipeline.config.model.PipelineConfig;

/**
 * Interface for validators that validate pipeline configurations.
 * This extends the base Validator interface with pipeline-specific functionality.
 */
public interface PipelineConfigValidator extends Validator<PipelineConfig> {
    
    /**
     * Validates a pipeline configuration with its ID.
     * 
     * @param pipelineId The ID of the pipeline being validated
     * @param config The pipeline configuration to validate
     * @return ValidationResult containing the validation outcome
     */
    default ValidationResult validateWithId(String pipelineId, PipelineConfig config) {
        // Default implementation just delegates to validate
        return validate(config);
    }
}