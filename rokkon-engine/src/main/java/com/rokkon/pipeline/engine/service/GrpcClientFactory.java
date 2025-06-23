package com.rokkon.pipeline.engine.service;

import com.rokkon.search.sdk.PipeStepProcessor;

/**
 * Factory interface for creating gRPC clients to module services.
 */
public interface GrpcClientFactory {
    
    /**
     * Get or create a gRPC client for the specified host and port.
     * 
     * @param host The host address of the gRPC service
     * @param port The port of the gRPC service
     * @return A PipeStepProcessor client connected to the service
     */
    PipeStepProcessor getClient(String host, int port);
}