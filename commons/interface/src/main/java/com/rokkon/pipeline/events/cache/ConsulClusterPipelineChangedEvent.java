package com.rokkon.pipeline.events.cache;

/**
 * CDI event fired when a cluster pipeline configuration changes in Consul.
 * This event is used to trigger cache invalidation across all engine instances.
 */
public record ConsulClusterPipelineChangedEvent(
    String clusterName,
    String pipelineId,
    String value
) {}