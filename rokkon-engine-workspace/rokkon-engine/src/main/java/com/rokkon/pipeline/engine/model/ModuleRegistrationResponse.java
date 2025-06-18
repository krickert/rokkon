package com.rokkon.pipeline.engine.model;

/**
 * Response from module registration.
 */
public record ModuleRegistrationResponse(
    boolean success,
    String moduleId,
    String message
) {
    /**
     * Create a successful registration response.
     */
    public static ModuleRegistrationResponse success(String moduleId) {
        return new ModuleRegistrationResponse(true, moduleId, "Module registered successfully");
    }
    
    /**
     * Create a failed registration response.
     */
    public static ModuleRegistrationResponse failure(String message) {
        return new ModuleRegistrationResponse(false, null, message);
    }
}