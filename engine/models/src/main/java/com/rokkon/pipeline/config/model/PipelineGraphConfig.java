package com.rokkon.pipeline.config.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Collections;
import java.util.Map;

/**
 * Configuration for a pipeline graph, which contains a map of all defined pipeline configurations.
 * This record is immutable.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Pipeline graph configuration containing all pipelines")
public record PipelineGraphConfig(
        @JsonProperty("pipelines") Map<String, PipelineConfig> pipelines
) {
    public PipelineGraphConfig {
        pipelines = (pipelines == null) ? Collections.emptyMap() : Map.copyOf(pipelines);
        // Map.copyOf will throw NPE if map contains null keys or values.
    }

    public PipelineConfig getPipelineConfig(String pipelineId) {
        return pipelines.get(pipelineId);
    }
}