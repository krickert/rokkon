package com.rokkon.pipeline.config.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.Map;

/**
 * Request to add a module to a cluster's whitelist.
 */
@Schema(description = "Request to whitelist a module for a cluster")
public record ModuleWhitelistRequest(
    @JsonProperty("implementationName")
    @NotBlank(message = "Implementation name is required")
    @Schema(description = "Human-readable name of the module", example = "Test Processor")
    String implementationName,
    
    @JsonProperty("grpcServiceName")
    @NotBlank(message = "gRPC service name is required")
    @Schema(description = "gRPC service name used in Consul", example = "test-processor")
    String grpcServiceName,
    
    @JsonProperty("customConfigSchemaReference")
    @Schema(description = "Reference to the JSON schema for module configuration")
    SchemaReference customConfigSchemaReference,
    
    @JsonProperty("customConfig")
    @Schema(description = "Default configuration for the module")
    Map<String, Object> customConfig
) {
    /**
     * Convenience constructor for minimal whitelist request.
     */
    public ModuleWhitelistRequest(String implementationName, String grpcServiceName) {
        this(implementationName, grpcServiceName, null, null);
    }
}