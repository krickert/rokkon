package com.rokkon.pipeline.engine.service;

import com.rokkon.pipeline.config.model.KafkaTransportConfig;
import com.rokkon.pipeline.config.model.PipelineStepConfig;
import com.rokkon.search.model.PipeStream;
import com.rokkon.search.sdk.ProcessRequest;
import com.rokkon.search.sdk.ProcessResponse;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Transport handler for Kafka-based routing.
 * Handles both standard pipeline topics and custom topic forwarding.
 */
@ApplicationScoped
public class KafkaTransportHandler implements TransportHandler {
    
    private static final Logger LOG = LoggerFactory.getLogger(KafkaTransportHandler.class);
    
    // TODO: Inject ModuleWhitelistService when available
    // TODO: Inject actual Kafka producer when implemented
    
    @Override
    public Uni<ProcessResponse> routeRequest(ProcessRequest request, PipelineStepConfig stepConfig) {
        // Kafka is typically used for async messaging, not request-response
        return Uni.createFrom().failure(
            new UnsupportedOperationException("Kafka transport does not support synchronous request-response")
        );
    }
    
    @Override
    public Uni<Void> routeStream(PipeStream stream, String targetStepName, PipelineStepConfig stepConfig) {
        // Find the Kafka output configuration for this target
        var kafkaOutput = stepConfig.outputs().values().stream()
            .filter(output -> targetStepName.equals(output.targetStepName()) && 
                             output.kafkaTransport() != null)
            .findFirst()
            .orElse(null);
            
        if (kafkaOutput == null || kafkaOutput.kafkaTransport() == null) {
            return Uni.createFrom().failure(
                new IllegalStateException("No Kafka transport config found for target: " + targetStepName)
            );
        }
        
        KafkaTransportConfig kafkaConfig = kafkaOutput.kafkaTransport();
        String topicName;
        
        // Determine topic name based on configuration
        if (kafkaConfig.topic() != null && !kafkaConfig.topic().isBlank()) {
            // Custom topic forwarding - must be whitelisted
            topicName = kafkaConfig.topic();
            LOG.info("Using custom topic '{}' for step '{}'", topicName, targetStepName);
            
            // TODO: Check whitelist when whitelist service has the topic whitelist method
            // For now, just log that we would check
            LOG.debug("Would verify topic '{}' is whitelisted", topicName);
        } else {
            // Standard pipeline topic - auto-generate name
            topicName = String.format("%s.%s.input", 
                stream.getCurrentPipelineName(), targetStepName);
            LOG.info("Using standard topic '{}' for step '{}'", topicName, targetStepName);
        }
        
        // Get partition key
        String partitionKey = extractPartitionKey(stream, kafkaConfig.partitionKeyField());
        
        // TODO: Implement actual Kafka producer logic
        LOG.info("Would route stream {} to Kafka topic '{}' with partition key '{}'", 
            stream.getStreamId(), topicName, partitionKey);
        
        // Log producer config
        LOG.debug("Kafka producer config: {}", kafkaConfig.getAllProducerProperties());
        
        // For now, just complete successfully
        return Uni.createFrom().voidItem();
    }
    
    @Override
    public boolean canHandle(PipelineStepConfig stepConfig) {
        // For Kafka routing, we check outputs in the step config
        return stepConfig.outputs() != null && 
               stepConfig.outputs().values().stream()
                   .anyMatch(output -> output.kafkaTransport() != null);
    }
    
    private String extractPartitionKey(PipeStream stream, String partitionKeyField) {
        // Default to pipedocId as per design
        if ("pipedocId".equals(partitionKeyField) || partitionKeyField == null) {
            return stream.getDocument().getId();
        }
        
        // TODO: Support other partition key fields if needed
        LOG.warn("Custom partition key field '{}' not yet supported, using pipedocId", 
            partitionKeyField);
        return stream.getDocument().getId();
    }
}