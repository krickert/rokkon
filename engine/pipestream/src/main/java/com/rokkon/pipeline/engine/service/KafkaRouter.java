package com.rokkon.pipeline.engine.service;

import com.rokkon.pipeline.config.model.PipelineStepConfig;
import com.rokkon.search.model.PipeStream;
import io.smallrye.mutiny.Uni;

/**
 * Interface for routing PipeStreams to Kafka topics based on pipeline configuration.
 * This enables asynchronous processing and decoupling between pipeline steps.
 */
public interface KafkaRouter {
    
    /**
     * Route a PipeStream to the appropriate Kafka topic for the target step.
     * 
     * @param pipelineName The name of the pipeline
     * @param targetStepName The name of the target step
     * @param stream The PipeStream to route
     * @param stepConfig The current step configuration
     * @return A Uni that completes when the message is sent to Kafka
     */
    Uni<Void> routeToModule(String pipelineName, String targetStepName, 
                           PipeStream stream, PipelineStepConfig stepConfig);
}