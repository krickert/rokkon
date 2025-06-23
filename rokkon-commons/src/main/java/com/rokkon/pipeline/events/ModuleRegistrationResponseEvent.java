package com.rokkon.pipeline.events;

/**
 * Event fired when a module registration request has been processed.
 * This is the response to a ModuleRegistrationRequestEvent.
 */
public record ModuleRegistrationResponseEvent(
    String requestId,     // To correlate with the request
    boolean success,
    String message,
    String moduleId,      // The assigned module ID if successful
    String moduleName,
    String error          // Error message if failed
) {
    public static ModuleRegistrationResponseEvent success(
            String requestId,
            String moduleId,
            String moduleName,
            String message) {
        return new ModuleRegistrationResponseEvent(
            requestId,
            true,
            message,
            moduleId,
            moduleName,
            null
        );
    }
    
    public static ModuleRegistrationResponseEvent failure(
            String requestId,
            String error) {
        return new ModuleRegistrationResponseEvent(
            requestId,
            false,
            "Registration failed",
            null,
            null,
            error
        );
    }
}