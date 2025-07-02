package com.rokkon.pipeline.engine.service;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import io.smallrye.stork.Stork;
import io.smallrye.stork.api.Service;
import io.smallrye.stork.api.ServiceInstance;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@QuarkusTest
class StorkServiceDiscoveryTest {
    
    @Inject
    StorkServiceDiscovery serviceDiscovery;
    
    @InjectMock
    Stork stork;
    
    @Test
    void testDiscoverService() {
        // Given
        String serviceName = "test-service";
        ServiceInstance mockInstance = createMockServiceInstance("localhost", 9090);
        Service mockService = Mockito.mock(Service.class);
        
        when(stork.getService(serviceName)).thenReturn(mockService);
        when(mockService.selectInstance()).thenReturn(Uni.createFrom().item(mockInstance));
        
        // When
        ServiceInstance result = serviceDiscovery.discoverService(serviceName)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getHost()).isEqualTo("localhost");
        assertThat(result.getPort()).isEqualTo(9090);
    }
    
    @Test
    void testDiscoverAllInstances() {
        // Given
        String serviceName = "test-service";
        List<ServiceInstance> mockInstances = List.of(
            createMockServiceInstance("host1", 9090),
            createMockServiceInstance("host2", 9091)
        );
        Service mockService = Mockito.mock(Service.class);
        
        when(stork.getService(serviceName)).thenReturn(mockService);
        when(mockService.getInstances()).thenReturn(Uni.createFrom().item(mockInstances));
        
        // When
        List<ServiceInstance> result = serviceDiscovery.discoverAllInstances(serviceName)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getHost()).isEqualTo("host1");
        assertThat(result.get(1).getHost()).isEqualTo("host2");
    }
    
    @Test
    void testDiscoverServiceFailure() {
        // Given
        String serviceName = "non-existent-service";
        Service mockService = Mockito.mock(Service.class);
        
        when(stork.getService(serviceName)).thenReturn(mockService);
        when(mockService.selectInstance())
            .thenReturn(Uni.createFrom().failure(new RuntimeException("Service not found")));
        
        // When/Then
        serviceDiscovery.discoverService(serviceName)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitFailure()
            .assertFailedWith(RuntimeException.class, "Service not found");
    }
    
    private ServiceInstance createMockServiceInstance(String host, int port) {
        ServiceInstance instance = Mockito.mock(ServiceInstance.class);
        when(instance.getHost()).thenReturn(host);
        when(instance.getPort()).thenReturn(port);
        when(instance.isSecure()).thenReturn(false);
        return instance;
    }
}