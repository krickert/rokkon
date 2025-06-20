package com.rokkon.pipeline.engine.api;

import java.time.Instant;

public record ModuleRegistrationResponse(
    boolean success,
    String message,
    String moduleId,
    String consulServiceId,
    Instant registeredAt
) {
    public static ModuleRegistrationResponse success(String moduleId, String consulServiceId) {
        return new ModuleRegistrationResponse(
            true,
            "Module registered successfully",
            moduleId,
            consulServiceId,
            Instant.now()
        );
    }
    
    public static ModuleRegistrationResponse failure(String message) {
        return new ModuleRegistrationResponse(
            false,
            message,
            null,
            null,
            null
        );
    }
}