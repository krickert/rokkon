package com.rokkon.pipeline.engine.service;

import com.rokkon.pipeline.config.model.PipelineStepConfig;
import com.rokkon.pipeline.config.model.TransportType;
import com.rokkon.search.model.PipeStream;
import com.rokkon.search.sdk.ProcessRequest;
import com.rokkon.search.sdk.ProcessResponse;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.core.eventbus.EventBus;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Event-driven implementation of the router that:
 * - Publishes routing events for observability
 * - Supports multiple transport handlers
 * - Enables parallel routing to multiple destinations
 */
@ApplicationScoped
public class EventDrivenRouterImpl implements EventDrivenRouter {
    
    private static final Logger LOG = LoggerFactory.getLogger(EventDrivenRouterImpl.class);
    
    @Inject
    EventBus eventBus;
    
    @Inject
    Event<RoutingEvent> routingEvent;
    
    @Inject
    GrpcTransportHandler grpcHandler;
    
    @Inject
    KafkaTransportHandler kafkaHandler;
    
    private final Map<TransportType, TransportHandler> transportHandlers = new ConcurrentHashMap<>();
    
    @PostConstruct
    void init() {
        // Register default handlers
        registerTransportHandler(TransportType.GRPC, grpcHandler);
        registerTransportHandler(TransportType.KAFKA, kafkaHandler);
    }
    
    @Override
    public Uni<ProcessResponse> routeRequest(ProcessRequest request, PipelineStepConfig stepConfig) {
        LOG.debug("Routing request for step: {}", stepConfig.stepName());
        
        // For direct request routing, we use gRPC by default
        TransportHandler handler = transportHandlers.get(TransportType.GRPC);
        if (handler == null || !handler.canHandle(stepConfig)) {
            return Uni.createFrom().failure(
                new IllegalStateException("No handler available for step: " + stepConfig.stepName())
            );
        }
        
        // Publish routing started event
        publishRoutingEvent(RoutingEvent.started(stepConfig.stepName(), TransportType.GRPC));
        
        return handler.routeRequest(request, stepConfig)
            .onItem().invoke(response -> 
                publishRoutingEvent(RoutingEvent.completed(stepConfig.stepName(), TransportType.GRPC))
            )
            .onFailure().invoke(error -> 
                publishRoutingEvent(RoutingEvent.failed(stepConfig.stepName(), TransportType.GRPC, error))
            );
    }
    
    @Override
    public Multi<RoutingResult> routeStream(PipeStream stream, PipelineStepConfig currentStep) {
        if (currentStep.outputs() == null || currentStep.outputs().isEmpty()) {
            return Multi.createFrom().empty();
        }
        
        // Create a Multi from all outputs
        return Multi.createFrom().iterable(currentStep.outputs().entrySet())
            .onItem().transformToUniAndConcatenate(entry -> {
                String outputName = entry.getKey();
                var output = entry.getValue();
                TransportType transportType = output.transportType();
                String targetStepName = output.targetStepName();
                
                LOG.debug("Routing stream {} to {} via {}", 
                    stream.getStreamId(), targetStepName, transportType);
                
                TransportHandler handler = transportHandlers.get(transportType);
                if (handler == null) {
                    LOG.error("No handler registered for transport type: {}", transportType);
                    return Uni.createFrom().item(
                        RoutingResult.failure(targetStepName, transportType, 
                            "No handler for transport", null)
                    );
                }
                
                // Publish routing started event
                publishRoutingEvent(RoutingEvent.started(targetStepName, transportType));
                
                return handler.routeStream(stream, targetStepName, currentStep)
                    .replaceWith(RoutingResult.success(targetStepName, transportType, "Routed successfully"))
                    .onItem().invoke(result -> 
                        publishRoutingEvent(RoutingEvent.completed(targetStepName, transportType))
                    )
                    .onFailure().recoverWithItem(error -> {
                        LOG.error("Failed to route to {} via {}", targetStepName, transportType, error);
                        publishRoutingEvent(RoutingEvent.failed(targetStepName, transportType, error));
                        return RoutingResult.failure(targetStepName, transportType, 
                            error.getMessage(), error);
                    });
            });
    }
    
    @Override
    public void registerTransportHandler(TransportType transportType, TransportHandler handler) {
        LOG.info("Registering transport handler for: {}", transportType);
        transportHandlers.put(transportType, handler);
    }
    
    private void publishRoutingEvent(RoutingEvent event) {
        try {
            // CDI event
            routingEvent.fire(event);
            
            // Vert.x EventBus for broader distribution
            eventBus.publish("pipeline.routing." + event.status().name().toLowerCase(), event);
        } catch (Exception e) {
            LOG.warn("Failed to publish routing event", e);
        }
    }
    
    /**
     * Event representing a routing operation.
     */
    public record RoutingEvent(
        String stepName,
        TransportType transportType,
        Status status,
        String message,
        Throwable error,
        long timestamp
    ) {
        public enum Status {
            STARTED, COMPLETED, FAILED
        }
        
        public static RoutingEvent started(String stepName, TransportType transportType) {
            return new RoutingEvent(stepName, transportType, Status.STARTED, 
                "Routing started", null, System.currentTimeMillis());
        }
        
        public static RoutingEvent completed(String stepName, TransportType transportType) {
            return new RoutingEvent(stepName, transportType, Status.COMPLETED, 
                "Routing completed", null, System.currentTimeMillis());
        }
        
        public static RoutingEvent failed(String stepName, TransportType transportType, Throwable error) {
            return new RoutingEvent(stepName, transportType, Status.FAILED, 
                "Routing failed: " + error.getMessage(), error, System.currentTimeMillis());
        }
    }
}