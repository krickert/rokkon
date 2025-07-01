package com.rokkon.pipeline.engine.grpc;

import org.junit.jupiter.api.BeforeEach;

/**
 * Unit test for DynamicGrpcClientFactory using Abstract Getter Pattern.
 * 
 * This test extends DynamicGrpcClientFactoryTestBase and provides
 * dependencies through abstract getters instead of CDI injection.
 * This allows testing without Quarkus context or engine dependencies.
 */
class DynamicGrpcClientFactoryUnitTest extends DynamicGrpcClientFactoryTestBase {
    
    private MockServiceDiscovery mockServiceDiscovery;
    private DynamicGrpcClientFactory factory;
    
    @BeforeEach
    void setupTest() {
        
        // Create mock service discovery
        mockServiceDiscovery = new MockServiceDiscovery();
        
        // Create factory and inject mock
        factory = new DynamicGrpcClientFactory();
        factory.setServiceDiscovery(mockServiceDiscovery);
        
        // Add echo-test service for the test server
        mockServiceDiscovery.addService("echo-test", "localhost", testGrpcPort);
    }
    
    @Override
    protected DynamicGrpcClientFactory getFactory() {
        return factory;
    }
}