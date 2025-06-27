package com.rokkon.pipeline.config.model;

/**
 * Summary information about a pipeline definition
 */
public record PipelineDefinitionSummary(
    String id,
    String name,
    String description,
    int stepCount,
    String createdAt,
    String modifiedAt,
    int activeInstances
) {}