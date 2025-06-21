package com.rokkon.pipeline.config.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.rokkon.pipeline.config.model.PipelineStepConfig.JsonConfigOptions;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;

/**
 * Represents a deployed instance of a pipeline definition in a specific cluster.
 * An instance references a pipeline definition and can have instance-specific configuration overrides.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Pipeline instance - a deployed pipeline in a specific cluster")
public record PipelineInstance(
    @JsonProperty("instanceId")
    @Schema(description = "Unique instance ID within the cluster", required = true, example = "prod-pipeline-1")
    String instanceId,
    
    @JsonProperty("pipelineDefinitionId")
    @Schema(description = "Reference to the pipeline definition", required = true, example = "document-processing")
    String pipelineDefinitionId,
    
    @JsonProperty("clusterName")
    @Schema(description = "The cluster this instance is deployed to", required = true, example = "production")
    String clusterName,
    
    @JsonProperty("name")
    @Schema(description = "Human-readable name for this instance", example = "Production Document Processor")
    String name,
    
    @JsonProperty("description")
    @Schema(description = "Description of this specific instance", example = "High-throughput document processing for production workloads")
    String description,
    
    @JsonProperty("status")
    @Schema(description = "Current status of the instance", required = true)
    PipelineInstanceStatus status,
    
    @JsonProperty("configOverrides")
    @Schema(description = "Instance-specific configuration overrides for pipeline steps")
    Map<String, StepConfigOverride> configOverrides,
    
    @JsonProperty("kafkaTopicPrefix")
    @Schema(description = "Kafka topic prefix for this instance", example = "prod-docs")
    String kafkaTopicPrefix,
    
    @JsonProperty("priority")
    @Schema(description = "Processing priority (higher = more resources)", example = "100", minimum = "1", maximum = "1000")
    Integer priority,
    
    @JsonProperty("maxParallelism")
    @Schema(description = "Maximum parallel processing threads", example = "10", minimum = "1")
    Integer maxParallelism,
    
    @JsonProperty("metadata")
    @Schema(description = "Additional metadata for this instance")
    Map<String, String> metadata,
    
    @JsonProperty("createdAt")
    @Schema(description = "Instance creation timestamp", example = "2024-01-15T10:30:00Z")
    Instant createdAt,
    
    @JsonProperty("modifiedAt")
    @Schema(description = "Last modification timestamp", example = "2024-01-15T14:45:00Z")
    Instant modifiedAt,
    
    @JsonProperty("startedAt")
    @Schema(description = "When the instance was last started", example = "2024-01-15T10:35:00Z")
    Instant startedAt,
    
    @JsonProperty("stoppedAt")
    @Schema(description = "When the instance was last stopped", example = "2024-01-15T14:00:00Z")
    Instant stoppedAt
) {
    public PipelineInstance {
        if (instanceId == null || instanceId.isBlank()) {
            throw new IllegalArgumentException("Instance ID cannot be null or blank");
        }
        if (pipelineDefinitionId == null || pipelineDefinitionId.isBlank()) {
            throw new IllegalArgumentException("Pipeline definition ID cannot be null or blank");
        }
        if (clusterName == null || clusterName.isBlank()) {
            throw new IllegalArgumentException("Cluster name cannot be null or blank");
        }
        if (status == null) {
            status = PipelineInstanceStatus.STOPPED;
        }
        if (priority != null && (priority < 1 || priority > 1000)) {
            throw new IllegalArgumentException("Priority must be between 1 and 1000");
        }
        if (maxParallelism != null && maxParallelism < 1) {
            throw new IllegalArgumentException("Max parallelism must be at least 1");
        }
        
        configOverrides = configOverrides == null ? Map.of() : Map.copyOf(configOverrides);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
    
    /**
     * Creates a new instance with default values
     */
    public static PipelineInstance create(String instanceId, String pipelineDefinitionId, String clusterName) {
        return new PipelineInstance(
            instanceId,
            pipelineDefinitionId,
            clusterName,
            null,
            null,
            PipelineInstanceStatus.STOPPED,
            Map.of(),
            null,
            null,
            null,
            Map.of(),
            Instant.now(),
            Instant.now(),
            null,
            null
        );
    }
    
    /**
     * Status of a pipeline instance
     */
    public enum PipelineInstanceStatus {
        @Schema(description = "Instance is stopped")
        STOPPED,
        
        @Schema(description = "Instance is starting up")
        STARTING,
        
        @Schema(description = "Instance is running and processing")
        RUNNING,
        
        @Schema(description = "Instance is stopping")
        STOPPING,
        
        @Schema(description = "Instance has encountered an error")
        ERROR,
        
        @Schema(description = "Instance is suspended (manually paused)")
        SUSPENDED
    }
    
    /**
     * Configuration override for a specific step in the pipeline instance
     */
    @Schema(description = "Step-specific configuration override")
    public record StepConfigOverride(
        @JsonProperty("enabled")
        @Schema(description = "Whether this step is enabled for this instance", example = "true")
        Boolean enabled,
        
        @JsonProperty("customConfig")
        @Schema(description = "Custom configuration overrides for this step")
        JsonConfigOptions customConfig,
        
        @JsonProperty("maxRetries")
        @Schema(description = "Override max retries for this step", example = "5")
        Integer maxRetries,
        
        @JsonProperty("stepTimeoutMs")
        @Schema(description = "Override timeout for this step in milliseconds", example = "30000")
        Long stepTimeoutMs,
        
        @JsonProperty("priority")
        @Schema(description = "Step priority within this instance", example = "50")
        Integer priority
    ) {}
}