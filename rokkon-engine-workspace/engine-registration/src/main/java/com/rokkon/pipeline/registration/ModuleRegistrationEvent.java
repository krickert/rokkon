package com.rokkon.pipeline.registration;

import java.time.Instant;

/**
 * Event fired when modules are registered/unregistered
 */
public record ModuleRegistrationEvent(
    String eventType,  // REGISTERED, UNREGISTERED, HEALTH_CHECK_FAILED, etc.
    String moduleName,
    String serviceId,
    String consulServiceId,
    Instant timestamp
) {
    public ModuleRegistrationEvent(String eventType, String moduleName, String serviceId, String consulServiceId) {
        this(eventType, moduleName, serviceId, consulServiceId, Instant.now());
    }
}