package com.rokkon.pipeline.engine.service;

import com.rokkon.pipeline.config.model.PipelineStepConfig;
import com.rokkon.pipeline.config.model.TransportType;
import com.rokkon.search.model.PipeStream;
import com.rokkon.search.sdk.ProcessRequest;
import com.rokkon.search.sdk.ProcessResponse;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

/**
 * Event-driven router interface that handles routing between pipeline steps
 * using various transport mechanisms (gRPC, Kafka, etc.).
 * 
 * This router follows an event-driven pattern where:
 * - Routing decisions are made based on step configuration
 * - Multiple transports can be used in parallel
 * - Events can be published for monitoring/observability
 */
public interface EventDrivenRouter {
    
    /**
     * Route a process request to a module using the appropriate transport.
     * 
     * @param request The process request to route
     * @param stepConfig The step configuration containing transport details
     * @return A Uni containing the process response
     */
    Uni<ProcessResponse> routeRequest(ProcessRequest request, PipelineStepConfig stepConfig);
    
    /**
     * Route a PipeStream to one or more destinations based on step outputs.
     * This can result in multiple parallel routes (e.g., gRPC + Kafka).
     * 
     * @param stream The PipeStream to route
     * @param currentStep The current step configuration with output definitions
     * @return A Multi of routing results (one per output)
     */
    Multi<RoutingResult> routeStream(PipeStream stream, PipelineStepConfig currentStep);
    
    /**
     * Register a transport handler for a specific transport type.
     * 
     * @param transportType The transport type to handle
     * @param handler The handler implementation
     */
    void registerTransportHandler(TransportType transportType, TransportHandler handler);
    
    /**
     * Result of a routing operation.
     */
    record RoutingResult(
        String targetStepName,
        TransportType transportType,
        boolean success,
        String message,
        Throwable error
    ) {
        public static RoutingResult success(String targetStepName, TransportType transportType, String message) {
            return new RoutingResult(targetStepName, transportType, true, message, null);
        }
        
        public static RoutingResult failure(String targetStepName, TransportType transportType, String message, Throwable error) {
            return new RoutingResult(targetStepName, transportType, false, message, error);
        }
    }
}