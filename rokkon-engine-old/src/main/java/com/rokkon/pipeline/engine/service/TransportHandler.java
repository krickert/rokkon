package com.rokkon.pipeline.engine.service;

import com.rokkon.pipeline.config.model.PipelineStepConfig;
import com.rokkon.search.model.PipeStream;
import com.rokkon.search.sdk.ProcessRequest;
import com.rokkon.search.sdk.ProcessResponse;
import io.smallrye.mutiny.Uni;

/**
 * Interface for transport-specific handlers that know how to route
 * messages using a specific transport mechanism (gRPC, Kafka, etc.).
 */
public interface TransportHandler {
    
    /**
     * Route a process request using this transport.
     * 
     * @param request The process request to send
     * @param stepConfig The step configuration
     * @return A Uni containing the process response
     */
    Uni<ProcessResponse> routeRequest(ProcessRequest request, PipelineStepConfig stepConfig);
    
    /**
     * Route a PipeStream using this transport.
     * 
     * @param stream The PipeStream to route
     * @param targetStepName The target step name
     * @param stepConfig The current step configuration
     * @return A Uni that completes when routing is done
     */
    Uni<Void> routeStream(PipeStream stream, String targetStepName, PipelineStepConfig stepConfig);
    
    /**
     * Check if this handler can handle the given step configuration.
     * 
     * @param stepConfig The step configuration to check
     * @return true if this handler can process the step
     */
    boolean canHandle(PipelineStepConfig stepConfig);
}