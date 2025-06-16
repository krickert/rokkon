package com.rokkon.search.config.pipeline.service.events;

/**
 * Types of configuration changes that can occur.
 */
public enum ConfigChangeType {
    /**
     * A new pipeline was created.
     */
    PIPELINE_CREATED,
    
    /**
     * An existing pipeline was updated.
     */
    PIPELINE_UPDATED,
    
    /**
     * A pipeline was deleted.
     */
    PIPELINE_DELETED,
    
    /**
     * A module was deleted (may affect multiple pipelines).
     */
    MODULE_DELETED,
    
    /**
     * A module was updated (may affect multiple pipelines).
     */
    MODULE_UPDATED,
    
    /**
     * A cluster configuration was updated.
     */
    CLUSTER_UPDATED,
    
    /**
     * A schema was deleted or updated (may affect multiple modules/pipelines).
     */
    SCHEMA_CHANGED
}