package com.rokkon.pipeline.config.model;

import java.util.Map;

/**
 * Request to create a new pipeline instance
 */
public record CreateInstanceRequest(
    String instanceId,
    String pipelineDefinitionId,
    String name,
    String description,
    String kafkaTopicPrefix,
    Integer priority,
    Integer maxParallelism,
    Map<String, PipelineInstance.StepConfigOverride> configOverrides,
    Map<String, String> metadata
) {}