package com.rokkon.pipeline.engine.service;

import com.rokkon.pipeline.config.model.PipelineStepConfig;
import com.rokkon.pipeline.consul.connection.ConsulConnectionManager;
import com.rokkon.pipeline.engine.grpc.DynamicGrpcClientFactory;
import com.rokkon.search.engine.MutinyPipeStreamEngineGrpc;
import com.rokkon.search.model.PipeStream;
import com.rokkon.search.sdk.ProcessRequest;
import com.rokkon.search.sdk.ProcessResponse;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.consul.ServiceEntry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Enhanced transport handler that supports both module (PipeStepProcessor) and 
 * engine (PipeStreamEngine) routing based on service metadata in Consul.
 */
@ApplicationScoped
public class EngineRoutingGrpcTransportHandler extends GrpcTransportHandler {
    
    private static final Logger LOG = LoggerFactory.getLogger(EngineRoutingGrpcTransportHandler.class);
    
    @Inject
    ConsulConnectionManager consulConnectionManager;
    
    @Inject
    DynamicGrpcClientFactory grpcClientFactory;
    
    @Override
    public Uni<ProcessResponse> routeRequest(ProcessRequest request, PipelineStepConfig stepConfig) {
        if (!canHandle(stepConfig)) {
            return Uni.createFrom().failure(
                new IllegalArgumentException("Step does not have gRPC configuration: " + stepConfig.stepName())
            );
        }
        
        String serviceName = stepConfig.processorInfo().grpcServiceName();
        
        // First check if this is an engine service
        return isEngineService(serviceName)
            .flatMap(isEngine -> {
                if (isEngine) {
                    LOG.debug("Routing to engine service: {} via PipeStreamEngine interface", serviceName);
                    return routeToEngine(request, serviceName);
                } else {
                    LOG.debug("Routing to module service: {} via PipeStepProcessor interface", serviceName);
                    return super.routeRequest(request, stepConfig);
                }
            });
    }
    
    /**
     * Check if a service is an engine by looking at its metadata in Consul.
     */
    private Uni<Boolean> isEngineService(String serviceName) {
        return consulConnectionManager.getClient()
            .map(client -> 
                Uni.createFrom().completionStage(
                    client.healthServiceNodes(serviceName, true).toCompletionStage()
                )
                .map(serviceList -> {
                    if (serviceList.getList() != null && !serviceList.getList().isEmpty()) {
                        ServiceEntry entry = serviceList.getList().get(0);
                        Map<String, String> meta = entry.getService().getMeta();
                        String serviceType = meta != null ? meta.get("service-type") : null;
                        boolean isEngine = "ENGINE".equals(serviceType);
                        LOG.trace("Service {} has service-type: {} (isEngine: {})", 
                            serviceName, serviceType, isEngine);
                        return isEngine;
                    }
                    return false;
                })
            )
            .orElse(Uni.createFrom().item(false));
    }
    
    /**
     * Route a request to another engine using PipeStreamEngine interface.
     */
    private Uni<ProcessResponse> routeToEngine(ProcessRequest request, String engineServiceName) {
        return grpcClientFactory.getMutinyEngineClientForService(engineServiceName)
            .flatMap(engineClient -> {
                // Convert ProcessRequest to PipeStreamRequest
                PipeStreamRequest streamRequest = PipeStreamRequest.newBuilder()
                    .setPipeStream(PipeStream.newBuilder()
                        .setStreamId(request.getMetadata().getStreamId())
                        .setPipeDoc(request.getDocument())
                        .setCurrentHopNumber(request.getMetadata().getCurrentHopNumber())
                        .build())
                    .build();
                
                // Call the engine
                return engineClient.processPipeStream(streamRequest)
                    .map(streamResponse -> {
                        // Convert PipeStreamResponse back to ProcessResponse
                        return ProcessResponse.newBuilder()
                            .setSuccess(!streamResponse.hasError())
                            .setOutputDoc(streamResponse.getPipeStream().getPipeDoc())
                            .addAllProcessorLogs(streamResponse.getLogsList())
                            .build();
                    });
            })
            .onFailure().invoke(error -> 
                LOG.error("Failed to route to engine {}", engineServiceName, error));
    }
}