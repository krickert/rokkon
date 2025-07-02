package com.rokkon.pipeline.engine.service;

import com.rokkon.pipeline.engine.grpc.DynamicGrpcClientFactory;
import com.rokkon.search.sdk.PipeStepProcessor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Adapter to bridge the old GrpcClientFactory interface with the new DynamicGrpcClientFactory.
 * This allows existing code to work with the new dynamic discovery module.
 */
@ApplicationScoped
public class GrpcClientFactoryAdapter implements GrpcClientFactory {
    
    @Inject
    DynamicGrpcClientFactory dynamicFactory;
    
    @Override
    public PipeStepProcessor getClient(String host, int port) {
        return dynamicFactory.getClient(host, port);
    }
}