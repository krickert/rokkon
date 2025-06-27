package com.rokkon.pipeline.validation;

/**
 * Interface for validators that validate pipeline configurations.
 * This extends the base ConfigValidator interface with pipeline-specific functionality.
 */
public interface PipelineConfigValidator extends ConfigValidator<PipelineConfigValidatable> {
    
    /**
     * Validates a pipeline configuration with its ID.
     * 
     * @param pipelineId The ID of the pipeline being validated
     * @param config The pipeline configuration to validate
     * @return ValidationResult containing the validation outcome
     */
    default ValidationResult validateWithId(String pipelineId, PipelineConfigValidatable config) {
        // Default implementation just delegates to validate
        return validate(config);
    }
}