package com.rokkon.search.engine.registration;

import com.rokkon.search.sdk.PipeStepProcessorGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.Map;

/**
 * Manages dynamic gRPC clients for registered modules.
 * 
 * When a module registers successfully, this creates a persistent
 * gRPC client that the engine can use for pipeline execution.
 */
@ApplicationScoped
public class DynamicGrpcClientManager {
    
    private static final Logger LOG = LoggerFactory.getLogger(DynamicGrpcClientManager.class);
    
    // Store channels and clients for registered modules
    private final Map<String, ManagedChannel> channels = new ConcurrentHashMap<>();
    private final Map<String, PipeStepProcessorGrpc.PipeStepProcessorBlockingStub> clients = new ConcurrentHashMap<>();
    
    /**
     * Create a new gRPC client for a registered module
     */
    public void createClient(String moduleId, String host, int port) {
        LOG.info("Creating gRPC client for module: {} at {}:{}", moduleId, host, port);
        
        // Remove existing client if it exists
        removeClient(moduleId);
        
        // Create new channel
        ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .keepAliveTime(30, TimeUnit.SECONDS)
                .keepAliveTimeout(10, TimeUnit.SECONDS)
                .keepAliveWithoutCalls(true)
                .build();
        
        // Create blocking stub for synchronous calls
        PipeStepProcessorGrpc.PipeStepProcessorBlockingStub client = 
                PipeStepProcessorGrpc.newBlockingStub(channel)
                        .withDeadlineAfter(30, TimeUnit.SECONDS);
        
        // Store both
        channels.put(moduleId, channel);
        clients.put(moduleId, client);
        
        LOG.info("✅ gRPC client created for module: {}", moduleId);
    }
    
    /**
     * Get a gRPC client for a registered module
     */
    public PipeStepProcessorGrpc.PipeStepProcessorBlockingStub getClient(String moduleId) {
        PipeStepProcessorGrpc.PipeStepProcessorBlockingStub client = clients.get(moduleId);
        if (client == null) {
            throw new IllegalArgumentException("No gRPC client found for module: " + moduleId);
        }
        return client;
    }
    
    /**
     * Check if a client exists for a module
     */
    public boolean hasClient(String moduleId) {
        return clients.containsKey(moduleId);
    }
    
    /**
     * Remove and shutdown a gRPC client
     */
    public void removeClient(String moduleId) {
        LOG.info("Removing gRPC client for module: {}", moduleId);
        
        // Remove client
        clients.remove(moduleId);
        
        // Shutdown channel
        ManagedChannel channel = channels.remove(moduleId);
        if (channel != null) {
            try {
                channel.shutdown();
                if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                    LOG.warn("Channel did not terminate gracefully, forcing shutdown for module: {}", moduleId);
                    channel.shutdownNow();
                }
            } catch (InterruptedException e) {
                LOG.warn("Interrupted while shutting down channel for module: {}", moduleId);
                channel.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        LOG.info("✅ gRPC client removed for module: {}", moduleId);
    }
    
    /**
     * Get all registered module IDs
     */
    public java.util.Set<String> getRegisteredModuleIds() {
        return clients.keySet();
    }
    
    /**
     * Get count of registered modules
     */
    public int getRegisteredModuleCount() {
        return clients.size();
    }
    
    /**
     * Shutdown all clients (called on application shutdown)
     */
    public void shutdownAll() {
        LOG.info("Shutting down all gRPC clients...");
        
        for (String moduleId : clients.keySet()) {
            removeClient(moduleId);
        }
        
        LOG.info("✅ All gRPC clients shutdown");
    }
    
    /**
     * Health check - test connectivity to a module
     */
    public boolean testModuleConnectivity(String moduleId) {
        try {
            PipeStepProcessorGrpc.PipeStepProcessorBlockingStub client = getClient(moduleId);
            
            // Test with getServiceRegistration call
            com.google.protobuf.Empty request = com.google.protobuf.Empty.newBuilder().build();
            client.withDeadlineAfter(5, TimeUnit.SECONDS)
                  .getServiceRegistration(request);
            
            LOG.debug("✅ Module connectivity test passed: {}", moduleId);
            return true;
            
        } catch (Exception e) {
            LOG.warn("Module connectivity test failed for {}: {}", moduleId, e.getMessage());
            return false;
        }
    }
}