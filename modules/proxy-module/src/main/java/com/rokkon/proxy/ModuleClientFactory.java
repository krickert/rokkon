package com.rokkon.proxy;

import com.rokkon.search.sdk.MutinyPipeStepProcessorGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.concurrent.TimeUnit;

/**
 * Factory for creating gRPC clients to connect to the backend module.
 */
@ApplicationScoped
public class ModuleClientFactory {
    private static final Logger LOG = Logger.getLogger(ModuleClientFactory.class);
    
    @ConfigProperty(name = "module.host", defaultValue = "localhost")
    String moduleHost;
    
    @ConfigProperty(name = "module.port", defaultValue = "9091")
    int modulePort;
    
    private ManagedChannel channel;
    
    /**
     * Creates a new gRPC client stub for the PipeStepProcessor service.
     * 
     * @return A Mutiny-based gRPC client stub
     */
    public MutinyPipeStepProcessorGrpc.MutinyPipeStepProcessorStub createClient() {
        if (channel == null || channel.isShutdown()) {
            LOG.infof("Creating new gRPC channel to module at %s:%d", moduleHost, modulePort);
            channel = ManagedChannelBuilder.forAddress(moduleHost, modulePort)
                .usePlaintext() // In production, use TLS
                .build();
        }
        
        return MutinyPipeStepProcessorGrpc.newMutinyStub(channel);
    }
    
    /**
     * Shutdown the gRPC channel when the application is stopped.
     */
    @PreDestroy
    public void shutdown() {
        if (channel != null && !channel.isShutdown()) {
            LOG.info("Shutting down gRPC channel");
            try {
                channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                LOG.warn("Error shutting down gRPC channel", e);
                Thread.currentThread().interrupt();
            }
        }
    }
}