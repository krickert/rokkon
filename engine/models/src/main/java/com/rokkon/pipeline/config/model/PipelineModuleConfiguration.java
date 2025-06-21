package com.rokkon.pipeline.config.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Collections;
import java.util.Map;

/**
 * Defines a type of pipeline module, corresponding to a specific gRPC service implementation.
 * This record is immutable.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Pipeline module configuration defining a gRPC service type")
public record PipelineModuleConfiguration(
        @JsonProperty("implementationName") String implementationName,
        @JsonProperty("implementationId") String implementationId,
        @JsonProperty("customConfigSchemaReference") SchemaReference customConfigSchemaReference,
        @JsonProperty("customConfig") Map<String, Object> customConfig
) {
    public PipelineModuleConfiguration {
        if (implementationName == null || implementationName.isBlank()) {
            throw new IllegalArgumentException("PipelineModuleConfiguration implementationName cannot be null or blank.");
        }
        if (implementationId == null || implementationId.isBlank()) {
            throw new IllegalArgumentException("PipelineModuleConfiguration implementationId cannot be null or blank.");
        }
        // customConfigSchemaReference can be null

        // Ensure customConfig is unmodifiable and handle null input gracefully
        customConfig = (customConfig == null) ? Collections.emptyMap() : Map.copyOf(customConfig);
    }

    // Optional: Overloaded constructor for convenience if customConfig is often not provided directly
    public PipelineModuleConfiguration(String implementationName, String implementationId, SchemaReference customConfigSchemaReference) {
        this(implementationName, implementationId, customConfigSchemaReference, null);
    }
}