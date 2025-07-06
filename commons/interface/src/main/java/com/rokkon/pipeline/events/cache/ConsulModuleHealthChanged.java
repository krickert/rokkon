package com.rokkon.pipeline.events.cache;

/**
 * CDI event fired when a module's health status changes in Consul.
 * This is used to track module health across the system.
 */
public record ConsulModuleHealthChanged(
    String serviceId,
    String serviceName,
    String status,
    String reason
) {}