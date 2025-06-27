package com.rokkon.pipeline.config.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.rokkon.pipeline.validation.PipelineClusterConfigValidatable;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Collections;
import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Pipeline cluster configuration")
public record PipelineClusterConfig(
        @JsonProperty("clusterName") String clusterName,
        @JsonProperty("pipelineGraphConfig") PipelineGraphConfig pipelineGraphConfig,
        @JsonProperty("pipelineModuleMap") PipelineModuleMap pipelineModuleMap,
        @JsonProperty("defaultPipelineName") String defaultPipelineName,
        @JsonProperty("allowedKafkaTopics") Set<String> allowedKafkaTopics,
        @JsonProperty("allowedGrpcServices") Set<String> allowedGrpcServices
) implements PipelineClusterConfigValidatable {
    @JsonCreator
    public PipelineClusterConfig(
            @JsonProperty("clusterName") String clusterName,
            @JsonProperty("pipelineGraphConfig") PipelineGraphConfig pipelineGraphConfig,
            @JsonProperty("pipelineModuleMap") PipelineModuleMap pipelineModuleMap,
            @JsonProperty("defaultPipelineName") String defaultPipelineName,
            @JsonProperty("allowedKafkaTopics") Set<String> allowedKafkaTopics,
            @JsonProperty("allowedGrpcServices") Set<String> allowedGrpcServices
    ) {
        if (clusterName == null || clusterName.isBlank()) {
            throw new IllegalArgumentException("PipelineClusterConfig clusterName cannot be null or blank.");
        }
        this.clusterName = clusterName;

        this.pipelineGraphConfig = pipelineGraphConfig; // Can be null
        this.pipelineModuleMap = pipelineModuleMap;     // Can be null
        this.defaultPipelineName = defaultPipelineName; // Can be null

        // Validate and normalize allowedKafkaTopics
        if (allowedKafkaTopics == null) {
            this.allowedKafkaTopics = Collections.emptySet();
        } else {
            for (String topic : allowedKafkaTopics) {
                if (topic == null || topic.isBlank()) {
                    throw new IllegalArgumentException("allowedKafkaTopics cannot contain null or blank strings.");
                }
            }
            this.allowedKafkaTopics = Set.copyOf(allowedKafkaTopics);
        }

        // Validate and normalize allowedGrpcServices
        if (allowedGrpcServices == null) {
            this.allowedGrpcServices = Collections.emptySet();
        } else {
            for (String service : allowedGrpcServices) {
                if (service == null || service.isBlank()) {
                    throw new IllegalArgumentException("allowedGrpcServices cannot contain null or blank strings.");
                }
            }
            this.allowedGrpcServices = Set.copyOf(allowedGrpcServices);
        }
    }
}