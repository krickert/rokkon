package com.rokkon.pipeline.engine.grpc;

import io.quarkus.test.common.DevServicesContext;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.vertx.core.Vertx;
import io.vertx.ext.consul.ConsulClient;
import io.vertx.ext.consul.ConsulClientOptions;
import io.vertx.ext.consul.ServiceOptions;
import org.testcontainers.consul.ConsulContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Test resource that provides a Consul container with pre-registered test services
 * for dynamic gRPC integration tests.
 */
public class DynamicGrpcTestResource implements QuarkusTestResourceLifecycleManager, DevServicesContext.ContextAware {
    
    private static final String CONSUL_IMAGE = "hashicorp/consul:latest";
    private ConsulContainer consul;
    private Vertx vertx;
    private ConsulClient consulClient;
    private Optional<String> containerNetworkId = Optional.empty();
    
    @Override
    public void setIntegrationTestContext(DevServicesContext context) {
        containerNetworkId = context.containerNetworkId();
    }
    
    @Override
    public Map<String, String> start() {
        // Start Consul container
        consul = new ConsulContainer(DockerImageName.parse(CONSUL_IMAGE))
            .withCommand("agent -dev -client 0.0.0.0 -log-level debug");
        
        // Apply network if running in container mode
        containerNetworkId.ifPresent(consul::withNetworkMode);
        
        consul.start();
        
        // Create Vertx and Consul client for seeding data
        vertx = Vertx.vertx();
        ConsulClientOptions options = new ConsulClientOptions()
            .setHost(consul.getHost())
            .setPort(consul.getFirstMappedPort());
        consulClient = ConsulClient.create(vertx, options);
        
        // Seed test services
        seedTestServices();
        
        Map<String, String> props = new HashMap<>();
        
        // Configure Consul connection
        props.put("consul.host", consul.getHost());
        props.put("consul.port", String.valueOf(consul.getFirstMappedPort()));
        props.put("quarkus.consul-config.agent.host", consul.getHost());
        props.put("quarkus.consul-config.agent.port", String.valueOf(consul.getFirstMappedPort()));
        
        // Configure service discovery to use Consul
        props.put("service.discovery.type", "consul");
        props.put("service.discovery.consul.host", consul.getHost());
        props.put("service.discovery.consul.port", String.valueOf(consul.getFirstMappedPort()));
        
        // Enable dynamic gRPC
        props.put("dynamic.grpc.enabled", "true");
        props.put("dynamic.grpc.channel.cache.ttl", "300");
        
        return props;
    }
    
    @Override
    public void stop() {
        if (consulClient != null) {
            consulClient.close();
        }
        if (vertx != null) {
            vertx.close();
        }
        if (consul != null) {
            consul.stop();
        }
    }
    
    private void seedTestServices() {
        try {
            // Wait for Consul to be ready
            Thread.sleep(2000);
            
            // Register a test echo service
            ServiceOptions echoService = new ServiceOptions()
                .setName("echo-service")
                .setId("echo-1")
                .setAddress("localhost")
                .setPort(9091)
                .setTags(List.of("grpc", "test", "echo"));
            
            CompletableFuture<Void> future = consulClient.registerService(echoService)
                .toCompletionStage()
                .toCompletableFuture();
            future.get();
            
            // Register a test processor service
            ServiceOptions processorService = new ServiceOptions()
                .setName("test-processor")
                .setId("processor-1")
                .setAddress("localhost")
                .setPort(9092)
                .setTags(List.of("grpc", "test", "processor"));
            
            future = consulClient.registerService(processorService)
                .toCompletionStage()
                .toCompletableFuture();
            future.get();
            
            System.out.println("Test services registered successfully in Consul");
            
        } catch (Exception e) {
            System.err.println("Failed to seed test services: " + e.getMessage());
            e.printStackTrace();
        }
    }
}