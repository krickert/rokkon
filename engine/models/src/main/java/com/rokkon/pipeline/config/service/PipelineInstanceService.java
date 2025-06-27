package com.rokkon.pipeline.config.service;

import com.rokkon.pipeline.config.model.PipelineInstance;
import com.rokkon.pipeline.config.model.CreateInstanceRequest;
import com.rokkon.pipeline.validation.ValidationResult;
import io.smallrye.mutiny.Uni;

import java.util.List;

/**
 * Service for managing pipeline instances in clusters.
 * Pipeline instances are deployments of pipeline definitions with cluster-specific configurations.
 */
public interface PipelineInstanceService {
    
    /**
     * Lists all pipeline instances in a cluster.
     * 
     * @param clusterName the cluster name
     * @return list of pipeline instances
     */
    Uni<List<PipelineInstance>> listInstances(String clusterName);
    
    /**
     * Gets a specific pipeline instance.
     * 
     * @param clusterName the cluster name
     * @param instanceId the instance ID
     * @return the pipeline instance or null if not found
     */
    Uni<PipelineInstance> getInstance(String clusterName, String instanceId);
    
    /**
     * Creates a new pipeline instance from a definition.
     * 
     * @param clusterName the cluster name
     * @param request the create instance request
     * @return validation result
     */
    Uni<ValidationResult> createInstance(String clusterName, CreateInstanceRequest request);
    
    /**
     * Updates an existing pipeline instance.
     * 
     * @param clusterName the cluster name
     * @param instanceId the instance ID
     * @param instance the updated instance
     * @return validation result
     */
    Uni<ValidationResult> updateInstance(String clusterName, String instanceId, PipelineInstance instance);
    
    /**
     * Deletes a pipeline instance.
     * The instance must be stopped before deletion.
     * 
     * @param clusterName the cluster name
     * @param instanceId the instance ID
     * @return validation result
     */
    Uni<ValidationResult> deleteInstance(String clusterName, String instanceId);
    
    /**
     * Starts a pipeline instance.
     * 
     * @param clusterName the cluster name
     * @param instanceId the instance ID
     * @return validation result
     */
    Uni<ValidationResult> startInstance(String clusterName, String instanceId);
    
    /**
     * Stops a running pipeline instance.
     * 
     * @param clusterName the cluster name
     * @param instanceId the instance ID
     * @return validation result
     */
    Uni<ValidationResult> stopInstance(String clusterName, String instanceId);
    
    /**
     * Checks if an instance exists.
     * 
     * @param clusterName the cluster name
     * @param instanceId the instance ID
     * @return true if exists
     */
    Uni<Boolean> instanceExists(String clusterName, String instanceId);
    
    /**
     * Lists all instances of a specific pipeline definition across all clusters.
     * 
     * @param pipelineDefinitionId the pipeline definition ID
     * @return list of pipeline instances
     */
    Uni<List<PipelineInstance>> listInstancesByDefinition(String pipelineDefinitionId);
}