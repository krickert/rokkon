package com.rokkon.pipeline.config.service;

import com.rokkon.pipeline.config.model.PipelineConfig;
import com.rokkon.pipeline.config.model.PipelineDefinitionSummary;
import com.rokkon.pipeline.validation.ValidationResult;
import com.rokkon.pipeline.validation.ValidationMode;
import io.smallrye.mutiny.Uni;

import java.util.List;

/**
 * Service for managing global pipeline definitions in Consul.
 * Pipeline definitions are templates that can be instantiated in multiple clusters.
 */
public interface PipelineDefinitionService {
    
    /**
     * Lists all pipeline definitions.
     * 
     * @return list of pipeline definition summaries
     */
    Uni<List<PipelineDefinitionSummary>> listDefinitions();
    
    /**
     * Gets a specific pipeline definition.
     * 
     * @param pipelineId the pipeline definition ID
     * @return the pipeline definition or null if not found
     */
    Uni<PipelineConfig> getDefinition(String pipelineId);
    
    /**
     * Creates a new pipeline definition.
     * 
     * @param pipelineId the pipeline definition ID
     * @param definition the pipeline definition
     * @return validation result
     */
    Uni<ValidationResult> createDefinition(String pipelineId, PipelineConfig definition);
    
    /**
     * Creates a new pipeline definition with specified validation mode.
     * 
     * @param pipelineId the pipeline definition ID
     * @param definition the pipeline definition
     * @param validationMode the validation mode to use (DRAFT or PRODUCTION)
     * @return validation result
     */
    Uni<ValidationResult> createDefinition(String pipelineId, PipelineConfig definition, ValidationMode validationMode);
    
    /**
     * Updates an existing pipeline definition.
     * 
     * @param pipelineId the pipeline definition ID
     * @param definition the updated pipeline definition
     * @return validation result
     */
    Uni<ValidationResult> updateDefinition(String pipelineId, PipelineConfig definition);
    
    /**
     * Updates an existing pipeline definition with specified validation mode.
     * 
     * @param pipelineId the pipeline definition ID
     * @param definition the updated pipeline definition
     * @param validationMode the validation mode to use (DRAFT or PRODUCTION)
     * @return validation result
     */
    Uni<ValidationResult> updateDefinition(String pipelineId, PipelineConfig definition, ValidationMode validationMode);
    
    /**
     * Deletes a pipeline definition.
     * Will fail if there are active instances using this definition.
     * 
     * @param pipelineId the pipeline definition ID
     * @return validation result
     */
    Uni<ValidationResult> deleteDefinition(String pipelineId);
    
    /**
     * Checks if a pipeline definition exists.
     * 
     * @param pipelineId the pipeline definition ID
     * @return true if exists
     */
    Uni<Boolean> definitionExists(String pipelineId);
    
    /**
     * Gets the count of active instances for a pipeline definition.
     * 
     * @param pipelineId the pipeline definition ID
     * @return count of active instances
     */
    Uni<Integer> getActiveInstanceCount(String pipelineId);
}