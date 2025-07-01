package com.rokkon.pipeline.engine.grpc;

import com.rokkon.search.sdk.MutinyPipeStepProcessorGrpc;
import com.rokkon.search.sdk.PipeStepProcessor;
import com.rokkon.search.sdk.PipeStepProcessorClient;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.quarkus.cache.CacheResult;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Factory for creating gRPC clients with dynamic service discovery.
 * This factory supports both direct host:port connections and 
 * service name based discovery through Consul.
 */
@ApplicationScoped
public class DynamicGrpcClientFactory {
    
    private static final Logger LOG = LoggerFactory.getLogger(DynamicGrpcClientFactory.class);
    private static final String CHANNEL_CACHE_NAME = "grpc-channels";
    
    @Inject
    ServiceDiscovery serviceDiscovery;
    
    // Cache of channels keyed by "host:port"
    private final Map<String, ManagedChannel> channels = new ConcurrentHashMap<>();
    
    /**
     * Setter for integration tests to inject their own ServiceDiscovery implementation
     * @param serviceDiscovery The service discovery implementation to use
     */
    public void setServiceDiscovery(ServiceDiscovery serviceDiscovery) {
        this.serviceDiscovery = serviceDiscovery;
    }
    
    /**
     * Get a client for a specific host and port.
     * This method creates a direct connection without service discovery.
     * 
     * @param host The target host
     * @param port The target port
     * @return A PipeStepProcessor client
     */
    public PipeStepProcessor getClient(String host, int port) {
        String key = host + ":" + port;
        
        ManagedChannel channel = channels.computeIfAbsent(key, k -> {
            LOG.info("Creating new gRPC channel for {}", key);
            
            return ManagedChannelBuilder
                .forAddress(host, port)
                .usePlaintext()
                .build();
        });
        
        return new PipeStepProcessorClient("PipeStepProcessor", channel, (name, stub) -> stub);
    }
    
    /**
     * Get a Mutiny stub for a specific host and port.
     * This method creates a type-safe Mutiny client.
     * 
     * @param host The target host
     * @param port The target port
     * @return A Mutiny stub instance
     */
    public MutinyPipeStepProcessorGrpc.MutinyPipeStepProcessorStub getMutinyClient(String host, int port) {
        String key = host + ":" + port;
        
        ManagedChannel channel = channels.computeIfAbsent(key, k -> {
            LOG.info("Creating new gRPC channel for {}", key);
            
            return ManagedChannelBuilder
                .forAddress(host, port)
                .usePlaintext()
                .build();
        });
        
        return MutinyPipeStepProcessorGrpc.newMutinyStub(channel);
    }
    
    /**
     * Get a client for a service by discovering it from Consul.
     * This enables dynamic service discovery without pre-configuration.
     * The result is cached to avoid repeated service discovery calls.
     * 
     * @param serviceName The Consul service name (e.g., "echo", "test")
     * @return A Uni that resolves to a PipeStepProcessor client
     */
    @CacheResult(cacheName = CHANNEL_CACHE_NAME)
    public Uni<PipeStepProcessor> getClientForService(String serviceName) {
        return serviceDiscovery.discoverService(serviceName)
            .map(instance -> {
                LOG.debug("Discovered service {} at {}:{}", 
                    serviceName, instance.getHost(), instance.getPort());
                return getClient(instance.getHost(), instance.getPort());
            });
    }
    
    /**
     * Get a Mutiny stub for a service by discovering it from Consul.
     * This provides a more reactive-friendly API.
     * 
     * @param serviceName The Consul service name (e.g., "echo", "test")
     * @return A Uni that resolves to a Mutiny stub
     */
    @CacheResult(cacheName = CHANNEL_CACHE_NAME + "-mutiny")
    public Uni<MutinyPipeStepProcessorGrpc.MutinyPipeStepProcessorStub> getMutinyClientForService(String serviceName) {
        return serviceDiscovery.discoverService(serviceName)
            .map(instance -> {
                LOG.debug("Discovered service {} at {}:{} for Mutiny client", 
                    serviceName, instance.getHost(), instance.getPort());
                return getMutinyClient(instance.getHost(), instance.getPort());
            });
    }
    
    /**
     * Get a client for a service without caching the discovery result.
     * Useful for testing or when you need fresh discovery on each call.
     * 
     * @param serviceName The Consul service name
     * @return A Uni that resolves to a PipeStepProcessor client
     */
    public Uni<PipeStepProcessor> getClientForServiceUncached(String serviceName) {
        return serviceDiscovery.discoverService(serviceName)
            .map(instance -> {
                LOG.debug("Discovered service {} at {}:{} (uncached)", 
                    serviceName, instance.getHost(), instance.getPort());
                return getClient(instance.getHost(), instance.getPort());
            });
    }
    
    /**
     * Get a Mutiny stub for a service without caching.
     * 
     * @param serviceName The Consul service name
     * @return A Uni that resolves to a Mutiny stub
     */
    public Uni<MutinyPipeStepProcessorGrpc.MutinyPipeStepProcessorStub> getMutinyClientForServiceUncached(String serviceName) {
        return serviceDiscovery.discoverService(serviceName)
            .map(instance -> {
                LOG.debug("Discovered service {} at {}:{} for Mutiny client (uncached)", 
                    serviceName, instance.getHost(), instance.getPort());
                return getMutinyClient(instance.getHost(), instance.getPort());
            });
    }
    
    /**
     * Gracefully shuts down all managed channels.
     * This is critical to prevent resource leaks.
     */
    @PreDestroy
    void shutdown() {
        LOG.info("Shutting down {} gRPC channels", channels.size());
        
        channels.forEach((key, channel) -> {
            try {
                LOG.debug("Shutting down channel for {}", key);
                channel.shutdown();
                
                // Wait up to 5 seconds for graceful shutdown
                if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                    LOG.warn("Channel {} did not terminate gracefully, forcing shutdown", key);
                    channel.shutdownNow();
                }
            } catch (Exception e) {
                LOG.error("Error shutting down channel {}", key, e);
                try {
                    channel.shutdownNow();
                } catch (Exception ex) {
                    LOG.error("Error forcing shutdown of channel {}", key, ex);
                }
            }
        });
        
        channels.clear();
    }
    
    /**
     * Get the number of active channels.
     * Useful for monitoring and testing.
     * 
     * @return The number of active channels
     */
    public int getActiveChannelCount() {
        return channels.size();
    }
    
    /**
     * Check if a channel exists for a specific host:port.
     * 
     * @param host The host
     * @param port The port
     * @return true if a channel exists
     */
    public boolean hasChannel(String host, int port) {
        return channels.containsKey(host + ":" + port);
    }
}