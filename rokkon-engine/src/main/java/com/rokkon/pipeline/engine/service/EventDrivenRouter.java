package com.rokkon.pipeline.engine.service;

import com.rokkon.pipeline.config.model.PipelineStepConfig;
import com.rokkon.pipeline.config.model.TransportType;
import com.rokkon.search.model.PipeStream;
import com.rokkon.search.sdk.ProcessRequest;
import com.rokkon.search.sdk.ProcessResponse;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

/**
 * Event-driven router interface that orchestrates message routing between pipeline steps
 * using pluggable transport mechanisms.
 * 
 * <p>
 * The EventDrivenRouter is a core abstraction in the Rokkon Engine that decouples
 * the orchestration logic from specific transport implementations. It enables:
 * </p>
 * 
 * <h2>Key Features:</h2>
 * <ul>
 *   <li><b>Transport Agnostic:</b> Supports multiple transport types (gRPC, Kafka, HTTP, etc.)</li>
 *   <li><b>Parallel Routing:</b> Can route messages to multiple destinations simultaneously</li>
 *   <li><b>Event-Driven Architecture:</b> Publishes routing events for monitoring and observability</li>
 *   <li><b>Pluggable Handlers:</b> Transport handlers can be registered dynamically</li>
 *   <li><b>Configuration-Based:</b> Routing decisions based on pipeline step configuration</li>
 * </ul>
 * 
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * @Inject
 * EventDrivenRouter router;
 * 
 * // Route a request through the pipeline
 * ProcessRequest request = createRequest();
 * PipelineStepConfig stepConfig = getStepConfig();
 * 
 * router.routeRequest(request, stepConfig)
 *     .onItem().invoke(response -> handleResponse(response))
 *     .onFailure().invoke(error -> handleError(error));
 * }</pre>
 * 
 * <h2>Implementation Note:</h2>
 * <p>
 * Implementations should ensure thread-safety as the router may be called
 * from multiple concurrent pipeline executions. Transport handlers should
 * be registered during application startup.
 * </p>
 * 
 * @see TransportHandler
 * @see PipelineStepConfig
 * @see TransportType
 * @since 1.0
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