package com.rokkon.pipeline.engine.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.Map;

/**
 * Request for module registration.
 */
public record ModuleRegistrationRequest(
    @NotBlank(message = "Module name is required")
    String moduleName,
    
    @NotBlank(message = "Host is required")
    String host,
    
    @NotNull(message = "Port is required")
    @Positive(message = "Port must be positive")
    Integer port,
    
    @NotBlank(message = "Cluster name is required")
    String clusterName,
    
    @NotBlank(message = "Service type is required")
    String serviceType,
    
    Map<String, String> metadata
) {
    public ModuleRegistrationRequest {
        if (metadata == null) {
            metadata = Map.of();
        }
    }
}