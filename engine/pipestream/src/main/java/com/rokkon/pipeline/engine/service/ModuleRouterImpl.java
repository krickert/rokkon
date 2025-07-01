package com.rokkon.pipeline.engine.service;

import com.rokkon.pipeline.config.model.PipelineStepConfig;
import com.rokkon.pipeline.engine.grpc.ServiceDiscovery;
import com.rokkon.search.sdk.PipeStepProcessor;
import com.rokkon.search.sdk.ProcessRequest;
import com.rokkon.search.sdk.ProcessResponse;
import io.quarkus.grpc.GrpcClient;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of ModuleRouter that routes requests to modules via gRPC.
 * In the future, this can be extended to support other transports like Kafka.
 */
@ApplicationScoped
public class ModuleRouterImpl implements ModuleRouter {
    
    private static final Logger LOG = LoggerFactory.getLogger(ModuleRouterImpl.class);
    
    @Inject
    ServiceDiscovery serviceDiscovery;
    
    @Inject
    GrpcClientFactory grpcClientFactory;
    
    @Override
    public Uni<ProcessResponse> routeToModule(ProcessRequest request, PipelineStepConfig stepConfig) {
        if (stepConfig.processorInfo() == null || stepConfig.processorInfo().grpcServiceName() == null) {
            return Uni.createFrom().failure(
                new IllegalArgumentException("No gRPC service configured for step: " + stepConfig.stepName())
            );
        }
        
        String serviceName = stepConfig.processorInfo().grpcServiceName();
        
        // Discover healthy service instance
        return serviceDiscovery.discoverService(serviceName)
            .flatMap(serviceInstance -> {
                LOG.debug("Routing to module {} at {}:{}", 
                    serviceName, serviceInstance.getHost(), serviceInstance.getPort());
                
                // Get or create gRPC client for the service instance
                PipeStepProcessor client = grpcClientFactory.getClient(
                    serviceInstance.getHost(), 
                    serviceInstance.getPort()
                );
                
                // Call the module
                return client.processData(request)
                    .onFailure().invoke(error -> 
                        LOG.error("Failed to process request in module {}", serviceName, error));
            });
    }
}