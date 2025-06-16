package com.rokkon.search.config.pipeline.service.events;

import com.rokkon.search.config.pipeline.model.PipelineConfig;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * Event fired when pipeline configurations change.
 * Used for cascading operations and notifications.
 */
public record ConfigChangeEvent(
    String clusterName,
    ConfigChangeType changeType,
    String pipelineId,
    PipelineConfig newConfig,
    PipelineConfig oldConfig,
    Set<String> affectedTopics,
    Set<String> affectedServices,
    Map<String, Object> metadata,
    Instant timestamp,
    String initiatedBy
) {
    
    public static ConfigChangeEvent pipelineCreated(String clusterName, String pipelineId, 
                                                   PipelineConfig newConfig, String initiatedBy) {
        return new ConfigChangeEvent(
            clusterName,
            ConfigChangeType.PIPELINE_CREATED,
            pipelineId,
            newConfig,
            null,
            extractKafkaTopics(newConfig),
            extractGrpcServices(newConfig),
            Map.of(),
            Instant.now(),
            initiatedBy
        );
    }
    
    public static ConfigChangeEvent pipelineUpdated(String clusterName, String pipelineId,
                                                   PipelineConfig newConfig, PipelineConfig oldConfig, 
                                                   String initiatedBy) {
        return new ConfigChangeEvent(
            clusterName,
            ConfigChangeType.PIPELINE_UPDATED,
            pipelineId,
            newConfig,
            oldConfig,
            extractKafkaTopics(newConfig),
            extractGrpcServices(newConfig),
            Map.of(),
            Instant.now(),
            initiatedBy
        );
    }
    
    public static ConfigChangeEvent pipelineDeleted(String clusterName, String pipelineId,
                                                   PipelineConfig oldConfig, String initiatedBy) {
        return new ConfigChangeEvent(
            clusterName,
            ConfigChangeType.PIPELINE_DELETED,
            pipelineId,
            null,
            oldConfig,
            extractKafkaTopics(oldConfig),
            extractGrpcServices(oldConfig),
            Map.of(),
            Instant.now(),
            initiatedBy
        );
    }
    
    public static ConfigChangeEvent moduleDeleted(String clusterName, String moduleId,
                                                 Set<String> affectedPipelines, String initiatedBy) {
        return new ConfigChangeEvent(
            clusterName,
            ConfigChangeType.MODULE_DELETED,
            null,
            null,
            null,
            Set.of(),
            Set.of(),
            Map.of("moduleId", moduleId, "affectedPipelines", affectedPipelines),
            Instant.now(),
            initiatedBy
        );
    }
    
    private static Set<String> extractKafkaTopics(PipelineConfig config) {
        if (config == null || config.pipelineSteps() == null) {
            return Set.of();
        }
        
        Set<String> topics = new java.util.HashSet<>();
        config.pipelineSteps().values().forEach(step -> {
            if (step.kafkaInputs() != null) {
                step.kafkaInputs().forEach(input -> {
                    if (input.listenTopics() != null) {
                        topics.addAll(input.listenTopics());
                    }
                });
            }
            if (step.outputs() != null) {
                step.outputs().values().forEach(output -> {
                    if (output.kafkaTransport() != null && output.kafkaTransport().topic() != null) {
                        topics.add(output.kafkaTransport().topic());
                    }
                });
            }
        });
        
        return Set.copyOf(topics);
    }
    
    private static Set<String> extractGrpcServices(PipelineConfig config) {
        if (config == null || config.pipelineSteps() == null) {
            return Set.of();
        }
        
        Set<String> services = new java.util.HashSet<>();
        config.pipelineSteps().values().forEach(step -> {
            if (step.processorInfo() != null && step.processorInfo().grpcServiceName() != null) {
                services.add(step.processorInfo().grpcServiceName());
            }
            if (step.outputs() != null) {
                step.outputs().values().forEach(output -> {
                    if (output.grpcTransport() != null && output.grpcTransport().serviceName() != null) {
                        services.add(output.grpcTransport().serviceName());
                    }
                });
            }
        });
        
        return Set.copyOf(services);
    }
}