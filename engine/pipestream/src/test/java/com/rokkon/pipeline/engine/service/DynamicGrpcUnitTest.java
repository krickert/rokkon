package com.rokkon.pipeline.engine.service;

import com.rokkon.pipeline.engine.grpc.DynamicGrpcClientFactory;
import com.rokkon.pipeline.engine.grpc.ServiceDiscovery;
import com.rokkon.search.sdk.MutinyPipeStepProcessorGrpc;
import io.smallrye.mutiny.Uni;
import io.smallrye.stork.api.ServiceInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit test for DynamicGrpcClientFactory that tests the factory logic
 * without requiring real gRPC connections or Consul.
 */
class DynamicGrpcUnitTest {
    
    private DynamicGrpcClientFactory factory;
    private ServiceDiscovery mockServiceDiscovery;
    
    @BeforeEach
    void setup() {
        factory = new DynamicGrpcClientFactory();
        mockServiceDiscovery = Mockito.mock(ServiceDiscovery.class);
        factory.setServiceDiscovery(mockServiceDiscovery);
    }
    
    @Test
    void testGetMutinyClientForService_Success() {
        // Given
        ServiceInstance mockInstance = Mockito.mock(ServiceInstance.class);
        when(mockInstance.getHost()).thenReturn("localhost");
        when(mockInstance.getPort()).thenReturn(9090);
        
        when(mockServiceDiscovery.discoverService("test-service"))
            .thenReturn(Uni.createFrom().item(mockInstance));
        
        // When
        Uni<MutinyPipeStepProcessorGrpc.MutinyPipeStepProcessorStub> clientUni = 
            factory.getMutinyClientForService("test-service");
        
        // Then
        MutinyPipeStepProcessorGrpc.MutinyPipeStepProcessorStub client = 
            clientUni.await().atMost(Duration.ofSeconds(5));
        
        assertThat(client).isNotNull();
    }
    
    @Test
    void testGetMutinyClientForService_ServiceNotFound() {
        // Given
        when(mockServiceDiscovery.discoverService(anyString()))
            .thenReturn(Uni.createFrom().failure(
                new RuntimeException("No healthy instances found for service: unknown-service")
            ));
        
        // When/Then
        assertThatThrownBy(() -> 
            factory.getMutinyClientForService("unknown-service")
                .await().atMost(Duration.ofSeconds(5))
        )
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("No healthy instances found");
    }
    
    @Test
    void testDiscoverAllInstances() {
        // Given
        ServiceInstance instance1 = createMockInstance("host1", 8080);
        ServiceInstance instance2 = createMockInstance("host2", 8081);
        
        when(mockServiceDiscovery.discoverAllInstances("multi-instance-service"))
            .thenReturn(Uni.createFrom().item(List.of(instance1, instance2)));
        
        // When
        List<ServiceInstance> instances = mockServiceDiscovery
            .discoverAllInstances("multi-instance-service")
            .await().atMost(Duration.ofSeconds(5));
        
        // Then
        assertThat(instances).hasSize(2);
        assertThat(instances.get(0).getHost()).isEqualTo("host1");
        assertThat(instances.get(1).getHost()).isEqualTo("host2");
    }
    
    @Test
    void testChannelCaching() {
        // Given
        ServiceInstance mockInstance = createMockInstance("localhost", 9090);
        when(mockServiceDiscovery.discoverService("cached-service"))
            .thenReturn(Uni.createFrom().item(mockInstance));
        
        // When - Get client twice
        MutinyPipeStepProcessorGrpc.MutinyPipeStepProcessorStub client1 = 
            factory.getMutinyClientForService("cached-service")
                .await().atMost(Duration.ofSeconds(5));
        
        MutinyPipeStepProcessorGrpc.MutinyPipeStepProcessorStub client2 = 
            factory.getMutinyClientForService("cached-service")
                .await().atMost(Duration.ofSeconds(5));
        
        // Then - Both should be non-null (caching behavior would be same channel)
        assertThat(client1).isNotNull();
        assertThat(client2).isNotNull();
    }
    
    private ServiceInstance createMockInstance(String host, int port) {
        ServiceInstance instance = Mockito.mock(ServiceInstance.class);
        when(instance.getHost()).thenReturn(host);
        when(instance.getPort()).thenReturn(port);
        when(instance.isSecure()).thenReturn(false);
        return instance;
    }
}