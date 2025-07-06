package com.rokkon.pipeline.events.cache;

/**
 * CDI event fired when a module registration changes in Consul.
 * This event is used to trigger cache invalidation across all engine instances.
 */
public record ConsulModuleRegistrationChangedEvent(
    String moduleId,
    String value
) {}