package com.rokkon.pipeline.engine.service;

import com.rokkon.pipeline.config.model.PipelineStepConfig;
import com.rokkon.search.model.PipeStream;
import com.rokkon.search.sdk.PipeStepProcessor;
import com.rokkon.search.sdk.ProcessRequest;
import com.rokkon.search.sdk.ProcessResponse;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Transport handler for gRPC-based routing.
 * Handles service discovery and gRPC client management.
 */
@ApplicationScoped
public class GrpcTransportHandler implements TransportHandler {
    
    private static final Logger LOG = LoggerFactory.getLogger(GrpcTransportHandler.class);
    
    @Inject
    ServiceDiscovery serviceDiscovery;
    
    @Inject
    GrpcClientFactory grpcClientFactory;
    
    @Override
    public Uni<ProcessResponse> routeRequest(ProcessRequest request, PipelineStepConfig stepConfig) {
        if (!canHandle(stepConfig)) {
            return Uni.createFrom().failure(
                new IllegalArgumentException("Step does not have gRPC configuration: " + stepConfig.stepName())
            );
        }
        
        String serviceName = stepConfig.processorInfo().grpcServiceName();
        
        return serviceDiscovery.discoverService(serviceName)
            .flatMap(serviceInstance -> {
                LOG.debug("Routing to gRPC service {} at {}:{}", 
                    serviceName, serviceInstance.getHost(), serviceInstance.getPort());
                
                PipeStepProcessor client = grpcClientFactory.getClient(
                    serviceInstance.getHost(), 
                    serviceInstance.getPort()
                );
                
                return client.processData(request)
                    .onFailure().invoke(error -> 
                        LOG.error("Failed to process request in service {}", serviceName, error));
            });
    }
    
    @Override
    public Uni<Void> routeStream(PipeStream stream, String targetStepName, PipelineStepConfig stepConfig) {
        // For gRPC, we don't directly route streams - they go through processRequest
        // This is here for future streaming support
        LOG.debug("gRPC stream routing not yet implemented for step: {}", targetStepName);
        return Uni.createFrom().voidItem();
    }
    
    @Override
    public boolean canHandle(PipelineStepConfig stepConfig) {
        return stepConfig.processorInfo() != null && 
               stepConfig.processorInfo().grpcServiceName() != null &&
               !stepConfig.processorInfo().grpcServiceName().isBlank();
    }
}