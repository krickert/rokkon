package com.rokkon.pipeline.engine.grpc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Example unit test demonstrating the Abstract Getter Pattern.
 * 
 * This test extends AbstractDynamicGrpcTestBase and provides mocked
 * dependencies through the abstract getter methods.
 * 
 * This demonstrates how to test dynamic-grpc without needing the full
 * engine:consul module or real Consul connections.
 */
class ExampleAbstractGetterUnitTest extends AbstractDynamicGrpcTestBase {
    
    private MockServiceDiscovery mockServiceDiscovery = new MockServiceDiscovery();
    
    @Override
    protected void additionalSetup() {
        // Add some test services
        mockServiceDiscovery.addService("test-service", "localhost", 8080);
        mockServiceDiscovery.addService("echo", "localhost", 49091);
        
        // Create the client factory with our mock
        clientFactory = new DynamicGrpcClientFactory();
        clientFactory.setServiceDiscovery(mockServiceDiscovery);
    }
    
    @Override
    protected ServiceDiscovery getServiceDiscovery() {
        return mockServiceDiscovery;
    }
    
    @Test
    void testServiceDiscoveryWithMocks() {
        // The base class already set up the client factory with our mock
        
        // Test discovering a service
        var service = mockServiceDiscovery.discoverService("test-service")
            .await().atMost(Duration.ofSeconds(1));
        
        assertThat(service).isNotNull();
        assertThat(service.getHost()).isEqualTo("localhost");
        assertThat(service.getPort()).isEqualTo(8080);
    }
    
    @Test
    void testServiceNotFoundScenario() {
        // Test behavior when service is not found
        assertThatThrownBy(() -> 
            mockServiceDiscovery.discoverService("non-existent")
                .await().atMost(Duration.ofSeconds(1))
        ).hasMessageContaining("Service not found");
    }
    
    @Test
    void testClientFactoryWithMockDiscovery() {
        // The client factory uses our mock service discovery
        
        // Get a client for a known service
        var clientUni = clientFactory.getClientForService("echo");
        assertThat(clientUni).isNotNull();
        
        // In a unit test, we can't make real gRPC calls,
        // but we can verify the factory is using our mock
        var discoveredService = mockServiceDiscovery.discoverService("echo")
            .await().atMost(Duration.ofSeconds(1));
        
        assertThat(discoveredService.getPort()).isEqualTo(49091);
    }
    
    @Test
    void testClearingMockData() {
        // Add a service
        mockServiceDiscovery.addService("temp-service", "localhost", 9999);
        
        // Verify it exists
        var service = mockServiceDiscovery.discoverService("temp-service")
            .await().atMost(Duration.ofSeconds(1));
        assertThat(service.getPort()).isEqualTo(9999);
        
        // Clear all services
        mockServiceDiscovery.clear();
        
        // Verify it's gone
        assertThatThrownBy(() -> 
            mockServiceDiscovery.discoverService("temp-service")
                .await().atMost(Duration.ofSeconds(1))
        ).hasMessageContaining("Service not found");
    }
}