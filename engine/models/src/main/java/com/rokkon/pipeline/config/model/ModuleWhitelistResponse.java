package com.rokkon.pipeline.config.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

/**
 * Response from module whitelist operations.
 */
@Schema(description = "Response from module whitelist operations")
public record ModuleWhitelistResponse(
    @JsonProperty("success")
    @Schema(description = "Whether the operation was successful")
    boolean success,
    
    @JsonProperty("message")
    @Schema(description = "Success or error message")
    String message,
    
    @JsonProperty("errors")
    @Schema(description = "List of validation errors if operation failed")
    List<String> errors,
    
    @JsonProperty("warnings")
    @Schema(description = "List of validation warnings")
    List<String> warnings
) {
    /**
     * Creates a successful response.
     */
    public static ModuleWhitelistResponse success(String message) {
        return new ModuleWhitelistResponse(true, message, List.of(), List.of());
    }
    
    /**
     * Creates a failure response with a single error message.
     */
    public static ModuleWhitelistResponse failure(String message) {
        return new ModuleWhitelistResponse(false, message, List.of(message), List.of());
    }
    
    /**
     * Creates a failure response with validation errors and warnings.
     */
    public static ModuleWhitelistResponse failure(String message, List<String> errors, List<String> warnings) {
        return new ModuleWhitelistResponse(false, message, errors, warnings);
    }
}