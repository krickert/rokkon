package com.rokkon.pipeline.events;

import java.util.Map;

/**
 * Event fired when a module registration is requested via gRPC.
 * This allows the registration service to delegate to the GlobalModuleRegistryService
 * without creating a circular dependency.
 */
public record ModuleRegistrationRequestEvent(
    String moduleName,
    String implementationId,
    String host,
    int port,
    String serviceType,
    String version,
    Map<String, String> metadata,
    String engineHost,
    int enginePort,
    String jsonSchema,
    String requestId  // To correlate with the response
) {
    public static ModuleRegistrationRequestEvent create(
            String moduleName,
            String implementationId,
            String host,
            int port,
            String serviceType,
            String version,
            Map<String, String> metadata,
            String engineHost,
            int enginePort,
            String jsonSchema,
            String requestId) {
        return new ModuleRegistrationRequestEvent(
            moduleName,
            implementationId,
            host,
            port,
            serviceType,
            version,
            metadata != null ? Map.copyOf(metadata) : Map.of(),
            engineHost,
            enginePort,
            jsonSchema,
            requestId
        );
    }
}