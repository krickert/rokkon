package com.rokkon.pipeline.engine.test;

import com.rokkon.pipeline.engine.grpc.DynamicGrpcClientFactory;
import io.quarkus.test.Mock;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.mockito.Mockito;

/**
 * Produces a mock DynamicGrpcClientFactory for tests to avoid classloading issues.
 * This replaces the real bean with a mock that doesn't have the problematic imports.
 */
@ApplicationScoped
public class MockDynamicGrpcClientFactoryProducer {
    
    @Produces
    @Mock
    @ApplicationScoped
    public DynamicGrpcClientFactory mockDynamicGrpcClientFactory() {
        DynamicGrpcClientFactory mock = Mockito.mock(DynamicGrpcClientFactory.class);
        
        // Setup default behavior - return a failure for any service
        Mockito.when(mock.getMutinyClientForService(Mockito.anyString()))
            .thenReturn(Uni.createFrom().failure(new RuntimeException("Mock factory - no services available")));
        
        return mock;
    }
}