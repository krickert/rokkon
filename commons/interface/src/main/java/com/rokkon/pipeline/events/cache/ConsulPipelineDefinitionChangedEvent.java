package com.rokkon.pipeline.events.cache;

/**
 * CDI event fired when a pipeline definition changes in Consul.
 * This event is used to trigger cache invalidation across all engine instances.
 */
public record ConsulPipelineDefinitionChangedEvent(
    String pipelineId,
    String value
) {}