package com.rokkon.pipeline.validation;

import com.rokkon.pipeline.config.model.PipelineClusterConfig;

/**
 * Interface for validators that validate PipelineClusterConfig.
 */
public interface PipelineClusterConfigValidator extends Validator<PipelineClusterConfig> {
    
    /**
     * Get the priority of this validator. Lower values run first.
     * @return the priority value
     */
    int getPriority();
    
    /**
     * Get the name of this validator for logging and reporting.
     * @return the validator name
     */
    String getValidatorName();
}