package com.rokkon.search.config.pipeline.service.validation;

import com.rokkon.search.config.pipeline.model.PipelineClusterConfig;
import com.rokkon.search.config.pipeline.model.PipelineConfig;

import java.util.List;
import java.util.Map;

/**
 * Interface for cluster-wide validation rules.
 * These rules validate across multiple pipelines within a cluster.
 */
public interface ClusterStructuralValidationRule {
    
    /**
     * Validates cluster-wide configuration.
     * 
     * @param clusterConfig The complete cluster configuration
     * @return List of error messages, empty if valid
     */
    List<String> validate(PipelineClusterConfig clusterConfig);
    
    /**
     * Validates a subset of pipelines within a cluster context.
     * This is useful for incremental validation when only some pipelines change.
     * 
     * @param pipelines Map of pipeline ID to pipeline configuration
     * @param clusterConfig The complete cluster configuration for context
     * @return List of error messages, empty if valid
     */
    default List<String> validatePipelineSubset(Map<String, PipelineConfig> pipelines, 
                                               PipelineClusterConfig clusterConfig) {
        // Default implementation validates the full cluster
        return validate(clusterConfig);
    }
    
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