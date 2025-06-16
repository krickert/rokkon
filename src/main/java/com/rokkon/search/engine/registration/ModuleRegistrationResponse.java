package com.rokkon.search.engine.registration;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response after attempting to register a module with the engine.
 */
@Schema(description = "Result of module registration attempt")
public record ModuleRegistrationResponse(
    
    @JsonProperty("success")
    @Schema(description = "Whether registration was successful")
    boolean success,
    
    @JsonProperty("moduleId")
    @Schema(description = "Unique ID assigned to the registered module", required = false)
    String moduleId,
    
    @JsonProperty("consulServiceId") 
    @Schema(description = "Consul service ID for the registered module", required = false)
    String consulServiceId,
    
    @JsonProperty("message")
    @Schema(description = "Human-readable result message")
    String message,
    
    @JsonProperty("errors")
    @Schema(description = "List of validation or registration errors", required = false)
    java.util.List<String> errors
) {
    
    /**
     * Creates a successful registration response
     */
    public static ModuleRegistrationResponse success(String moduleId, String consulServiceId, String message) {
        return new ModuleRegistrationResponse(
            true,
            moduleId,
            consulServiceId, 
            message,
            java.util.List.of()
        );
    }
    
    /**
     * Creates a failed registration response
     */
    public static ModuleRegistrationResponse failure(String message, java.util.List<String> errors) {
        return new ModuleRegistrationResponse(
            false,
            null,
            null,
            message,
            errors
        );
    }
}