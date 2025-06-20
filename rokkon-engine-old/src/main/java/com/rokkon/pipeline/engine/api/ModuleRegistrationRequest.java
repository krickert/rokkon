package com.rokkon.pipeline.engine.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record ModuleRegistrationRequest(
    @NotBlank(message = "Module name is required")
    String moduleName,
    
    @NotBlank(message = "Module host is required") 
    String host,
    
    @NotNull(message = "Module port is required")
    Integer port,
    
    @NotBlank(message = "Cluster name is required")
    String clusterName,
    
    // The proxied data that we'll validate by calling the module
    Map<String, Object> serviceData,
    
    // Optional metadata about the module
    Map<String, String> metadata
) {}