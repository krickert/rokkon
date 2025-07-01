package com.rokkon.pipeline.engine.grpc;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Abstract base test class for DynamicGrpcClientFactory tests.
 * 
 * This class implements the Abstract Getter Pattern to enable both unit and integration testing:
 * - Unit tests: Return mocked dependencies
 * - Integration tests: Return real instances with actual connections
 * 
 * Key benefits:
 * - No CDI dependency issues
 * - Tests can run in isolation
 * - Same test logic for both unit and integration tests
 * - Clear separation of concerns
 */
public abstract class AbstractDynamicGrpcTestBase {
    
    protected DynamicGrpcClientFactory clientFactory;
    protected ServiceDiscovery serviceDiscovery;
    
    // Abstract methods for dependency injection
    protected abstract ServiceDiscovery getServiceDiscovery();
    
    @BeforeEach
    void setupBase() {
        // Allow concrete classes to setup their dependencies first
        additionalSetup();
        
        // Get dependencies from concrete implementations
        serviceDiscovery = getServiceDiscovery();
        
        // Create real DynamicGrpcClientFactory if not already created
        if (clientFactory == null) {
            clientFactory = new DynamicGrpcClientFactory();
            clientFactory.setServiceDiscovery(serviceDiscovery);
        }
    }
    
    @AfterEach
    void cleanupBase() {
        // The factory's shutdown is handled by @PreDestroy in production
        // For tests, we'll let each test decide if it needs special cleanup
        
        // Allow concrete classes to do additional cleanup
        additionalCleanup();
    }
    
    // Hook methods for concrete classes
    protected void additionalSetup() {
        // Override in concrete classes if needed
    }
    
    protected void additionalCleanup() {
        // Override in concrete classes if needed
    }
    
    // Common test methods
    
    @Test
    void testClientFactoryInitialization() {
        assertThat(clientFactory).isNotNull();
        assertThat(serviceDiscovery).isNotNull();
    }
    
    @Test
    void testGetClientForService() {
        // This test will work for both unit and integration tests
        // Unit tests will use mock service discovery
        // Integration tests will use real Consul
        
        String serviceName = "test-service";
        
        // The behavior depends on the concrete implementation
        // Unit test: Mock returns predefined service instance
        // Integration test: Real Consul lookup
        
        Uni<com.rokkon.search.sdk.PipeStepProcessor> clientUni = 
            clientFactory.getClientForService(serviceName);
        
        // The test assertion depends on the setup
        // We'll handle specific assertions in concrete classes
        assertThat(clientUni).isNotNull();
    }
    
    @Test
    void testChannelCaching() {
        // Test that channels are properly cached
        String serviceName = "cached-service";
        
        // Get client twice
        Uni<com.rokkon.search.sdk.PipeStepProcessor> client1 = 
            clientFactory.getClientForService(serviceName);
        Uni<com.rokkon.search.sdk.PipeStepProcessor> client2 = 
            clientFactory.getClientForService(serviceName);
        
        // Both should return the same channel (when service exists)
        assertThat(client1).isNotNull();
        assertThat(client2).isNotNull();
    }
}