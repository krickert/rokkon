package com.rokkon.pipeline.config.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Collections;
import java.util.Map;

/**
 * Defines a single named pipeline, comprising a map of its constituent pipeline steps.
 * This record is immutable.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Pipeline configuration")
public record PipelineConfig(
        @JsonProperty("name") 
        @Schema(description = "Pipeline name", required = true, example = "document-processing")
        String name,
        
        @JsonProperty("pipelineSteps") 
        @Schema(description = "Map of pipeline steps by step ID")
        Map<String, PipelineStepConfig> pipelineSteps
) {
    public PipelineConfig {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("PipelineConfig name cannot be null or blank.");
        }
        pipelineSteps = (pipelineSteps == null) ? Collections.emptyMap() : Map.copyOf(pipelineSteps);
        // Map.copyOf will throw NPE if map contains null keys or values.
    }
}