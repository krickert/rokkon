package com.rokkon.pipeline.engine.service;

import com.rokkon.search.sdk.PipeStepProcessor;
import com.rokkon.search.sdk.PipeStepProcessorClient;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of GrpcClientFactory that manages gRPC client connections.
 * Caches clients per host:port combination to reuse connections.
 */
@ApplicationScoped
public class GrpcClientFactoryImpl implements GrpcClientFactory {
    
    private static final Logger LOG = LoggerFactory.getLogger(GrpcClientFactoryImpl.class);
    
    // Cache of channels and clients keyed by "host:port"
    private final Map<String, ManagedChannel> channels = new ConcurrentHashMap<>();
    private final Map<String, PipeStepProcessor> clients = new ConcurrentHashMap<>();
    
    @Override
    public PipeStepProcessor getClient(String host, int port) {
        String key = host + ":" + port;
        
        return clients.computeIfAbsent(key, k -> {
            LOG.info("Creating new gRPC client for {}", key);
            
            ManagedChannel channel = ManagedChannelBuilder
                .forAddress(host, port)
                .usePlaintext()
                .build();
            
            channels.put(key, channel);
            
            return new PipeStepProcessorClient("PipeStepProcessor", channel, (name, stub) -> stub);
        });
    }
    
    @PreDestroy
    void shutdown() {
        LOG.info("Shutting down gRPC channels");
        channels.values().forEach(channel -> {
            try {
                channel.shutdownNow();
            } catch (Exception e) {
                LOG.warn("Error shutting down channel", e);
            }
        });
        channels.clear();
        clients.clear();
    }
}