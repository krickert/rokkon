package com.rokkon.pipeline.engine.service;

import com.rokkon.pipeline.engine.test.ConsulTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import io.smallrye.stork.api.ServiceInstance;
import io.vertx.ext.consul.ConsulClient;
import io.vertx.ext.consul.ServiceOptions;
import io.vertx.mutiny.core.Vertx;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@QuarkusIntegrationTest
@QuarkusTestResource(ConsulTestResource.class)
class StorkServiceDiscoveryIT {
    
    @Inject
    StorkServiceDiscovery serviceDiscovery;
    
    @Inject
    Vertx vertx;
    
    @ConfigProperty(name = "consul.host")
    String consulHost;
    
    @ConfigProperty(name = "consul.port")
    int consulPort;
    
    ConsulClient consulClient;
    
    @BeforeEach
    void setup() {
        consulClient = ConsulClient.create(vertx.getDelegate(), 
            new io.vertx.ext.consul.ConsulClientOptions()
                .setHost(consulHost)
                .setPort(consulPort));
    }
    
    @Test
    void testServiceDiscoveryWithConsul() {
        // Given - Register a test service in Consul
        String serviceName = "test-module-" + UUID.randomUUID();
        String serviceId = serviceName + "-1";
        
        ServiceOptions service = new ServiceOptions()
            .setName(serviceName)
            .setId(serviceId)
            .setAddress("localhost")
            .setPort(9999)
            .setTags(List.of("grpc", "test"));
        
        // Register the service
        consulClient.registerService(service)
            .onSuccess(v -> System.out.println("Service registered: " + serviceId))
            .onFailure(error -> System.err.println("Failed to register: " + error))
            .toCompletionStage()
            .toCompletableFuture()
            .join();
        
        // Wait for Stork to pick up the service
        await().atMost(Duration.ofSeconds(10))
            .untilAsserted(() -> {
                ServiceInstance instance = serviceDiscovery.discoverService(serviceName)
                    .subscribe().withSubscriber(UniAssertSubscriber.create())
                    .awaitItem()
                    .getItem();
                
                assertThat(instance).isNotNull();
                assertThat(instance.getHost()).isEqualTo("localhost");
                assertThat(instance.getPort()).isEqualTo(9999);
            });
        
        // Clean up
        consulClient.deregisterService(serviceId)
            .toCompletionStage()
            .toCompletableFuture()
            .join();
    }
    
    @Test
    void testMultipleInstanceDiscovery() {
        // Given - Register multiple instances of the same service
        String serviceName = "multi-instance-service-" + UUID.randomUUID();
        
        for (int i = 0; i < 3; i++) {
            String serviceId = serviceName + "-" + i;
            ServiceOptions service = new ServiceOptions()
                .setName(serviceName)
                .setId(serviceId)
                .setAddress("localhost")
                .setPort(9000 + i)
                .setTags(List.of("grpc", "test"));
            
            consulClient.registerService(service)
                .toCompletionStage()
                .toCompletableFuture()
                .join();
        }
        
        // Wait for Stork to pick up all instances
        await().atMost(Duration.ofSeconds(10))
            .untilAsserted(() -> {
                List<ServiceInstance> instances = serviceDiscovery.discoverAllInstances(serviceName)
                    .subscribe().withSubscriber(UniAssertSubscriber.create())
                    .awaitItem()
                    .getItem();
                
                assertThat(instances).hasSize(3);
                assertThat(instances)
                    .extracting(ServiceInstance::getPort)
                    .containsExactlyInAnyOrder(9000, 9001, 9002);
            });
        
        // Clean up
        for (int i = 0; i < 3; i++) {
            consulClient.deregisterService(serviceName + "-" + i)
                .toCompletionStage()
                .toCompletableFuture()
                .join();
        }
    }
    
    @Test
    void testServiceNotFound() {
        // Given - A non-existent service
        String serviceName = "non-existent-service-" + UUID.randomUUID();
        
        // When/Then - Should fail gracefully
        serviceDiscovery.discoverService(serviceName)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitFailure()
            .assertFailedWith(RuntimeException.class);
    }
}