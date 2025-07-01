package com.rokkon.pipeline.engine.grpc;

import io.grpc.Channel;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.quarkus.cache.CacheResult;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Generic gRPC client provider that supports any Mutiny stub type.
 * This provider uses reflection to create stubs dynamically, allowing
 * for type-safe client creation without compile-time dependencies.
 */
@ApplicationScoped
public class GrpcClientProvider {
    
    private static final Logger LOG = LoggerFactory.getLogger(GrpcClientProvider.class);
    private static final String CHANNEL_CACHE_NAME = "grpc-channels";
    
    @Inject
    ServiceDiscovery serviceDiscovery;
    
    // Cache of channels keyed by "host:port"
    private final Map<String, ManagedChannel> channels = new ConcurrentHashMap<>();
    
    /**
     * Get a Mutiny stub client for a specific host and port.
     * 
     * @param stubClass The Mutiny stub class (e.g., MutinyPipeStepProcessorGrpc.MutinyPipeStepProcessorStub)
     * @param host The target host
     * @param port The target port
     * @param <T> The stub type
     * @return The Mutiny stub instance
     */
    public <T> T getClient(Class<T> stubClass, String host, int port) {
        String key = host + ":" + port;
        
        ManagedChannel channel = channels.computeIfAbsent(key, k -> {
            LOG.info("Creating new gRPC channel for {}", key);
            
            return ManagedChannelBuilder
                .forAddress(host, port)
                .usePlaintext()
                .build();
        });
        
        return createStub(stubClass, channel);
    }
    
    /**
     * Get a Mutiny stub client for a service by discovering it from Consul.
     * 
     * @param stubClass The Mutiny stub class
     * @param serviceName The Consul service name (e.g., "echo", "test")
     * @param <T> The stub type
     * @return A Uni that resolves to the Mutiny stub instance
     */
    @CacheResult(cacheName = CHANNEL_CACHE_NAME)
    public <T> Uni<T> getClientForService(Class<T> stubClass, String serviceName) {
        return serviceDiscovery.discoverService(serviceName)
            .map(instance -> {
                LOG.debug("Discovered service {} at {}:{}", 
                    serviceName, instance.getHost(), instance.getPort());
                return getClient(stubClass, instance.getHost(), instance.getPort());
            });
    }
    
    /**
     * Get a Mutiny stub client without caching the discovery result.
     * 
     * @param stubClass The Mutiny stub class
     * @param serviceName The Consul service name
     * @param <T> The stub type
     * @return A Uni that resolves to the Mutiny stub instance
     */
    public <T> Uni<T> getClientForServiceUncached(Class<T> stubClass, String serviceName) {
        return serviceDiscovery.discoverService(serviceName)
            .map(instance -> {
                LOG.debug("Discovered service {} at {}:{} (uncached)", 
                    serviceName, instance.getHost(), instance.getPort());
                return getClient(stubClass, instance.getHost(), instance.getPort());
            });
    }
    
    /**
     * Create a stub instance using reflection.
     * This works by finding the static newMutinyStub method on the parent class.
     */
    @SuppressWarnings("unchecked")
    private <T> T createStub(Class<T> stubClass, Channel channel) {
        try {
            // Get the enclosing class (e.g., MutinyPipeStepProcessorGrpc)
            Class<?> grpcClass = stubClass.getEnclosingClass();
            if (grpcClass == null) {
                throw new IllegalArgumentException("Stub class must be a nested class of a gRPC service class");
            }
            
            // Find the newMutinyStub method
            Method newStubMethod = grpcClass.getMethod("newMutinyStub", Channel.class);
            
            // Create the stub
            return (T) newStubMethod.invoke(null, channel);
            
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Failed to create Mutiny stub for " + stubClass.getName(), e);
        }
    }
    
    /**
     * Gracefully shuts down all managed channels.
     */
    @PreDestroy
    void shutdown() {
        LOG.info("Shutting down {} gRPC channels", channels.size());
        
        channels.forEach((key, channel) -> {
            try {
                LOG.debug("Shutting down channel for {}", key);
                channel.shutdown();
                
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
     */
    public int getActiveChannelCount() {
        return channels.size();
    }
    
    /**
     * Check if a channel exists for a specific host:port.
     */
    public boolean hasChannel(String host, int port) {
        return channels.containsKey(host + ":" + port);
    }
}