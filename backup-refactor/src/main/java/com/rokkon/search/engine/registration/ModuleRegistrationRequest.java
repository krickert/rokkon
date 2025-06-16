package com.rokkon.search.engine.registration;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request for registering a new module with the engine.
 * This is sent by the Docker CLI after it verifies the module's health.
 */
@Schema(description = "Module registration request from Docker CLI")
public record ModuleRegistrationRequest(
    
    @JsonProperty("moduleName")
    @Schema(description = "Name of the module (e.g., 'echo-service', 'chunker-v1')", example = "echo-service")
    String moduleName,
    
    @JsonProperty("moduleType") 
    @Schema(description = "Type of processing module", example = "echo-processor")
    String moduleType,
    
    @JsonProperty("host")
    @Schema(description = "Host where the module is running", example = "localhost")
    String host,
    
    @JsonProperty("port")
    @Schema(description = "gRPC port where the module is listening", example = "9001")
    int port,
    
    @JsonProperty("version")
    @Schema(description = "Module version", example = "1.0.0")
    String version,
    
    @JsonProperty("jsonSchema")
    @Schema(description = "Optional JSON schema for module configuration", required = false)
    String jsonSchema,
    
    @JsonProperty("metadata")
    @Schema(description = "Additional module metadata")
    java.util.Map<String, String> metadata
) {
    
    /**
     * Creates a simple module registration for testing
     */
    public static ModuleRegistrationRequest simple(String moduleName, String host, int port) {
        return new ModuleRegistrationRequest(
            moduleName,
            moduleName + "-processor", 
            host,
            port,
            "1.0.0",
            null, // No schema
            java.util.Map.of("source", "docker-cli")
        );
    }
}