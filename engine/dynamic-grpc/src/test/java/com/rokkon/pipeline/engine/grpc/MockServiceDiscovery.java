package com.rokkon.pipeline.engine.grpc;

import io.quarkus.test.Mock;
import io.smallrye.mutiny.Uni;
import io.smallrye.stork.api.ServiceInstance;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mock implementation of ServiceDiscovery for testing.
 */
@Mock
@ApplicationScoped
public class MockServiceDiscovery implements ServiceDiscovery {
    
    private final Map<String, ServiceInstance> services = new ConcurrentHashMap<>();
    private static int testGrpcPort = 0;
    
    /**
     * Add a service instance for testing.
     */
    public void addService(String serviceName, String host, int port) {
        services.put(serviceName, new TestServiceInstance(serviceName, host, port));
    }
    
    /**
     * Clear all services.
     */
    public void clear() {
        services.clear();
    }
    
    /**
     * Set the test gRPC port for echo-test service.
     */
    public static void setTestGrpcPort(int port) {
        testGrpcPort = port;
    }
    
    @Override
    public Uni<ServiceInstance> discoverService(String serviceName) {
        ServiceInstance instance = services.get(serviceName);
        if (instance != null) {
            return Uni.createFrom().item(instance);
        }
        
        // Return default test instances for common test service names
        return switch (serviceName) {
            case "echo" -> Uni.createFrom().item(new TestServiceInstance("echo", "localhost", 49091));
            case "test-module" -> Uni.createFrom().item(new TestServiceInstance("test-module", "localhost", 49092));
            case "mutiny-test" -> Uni.createFrom().item(new TestServiceInstance("mutiny-test", "localhost", 49093));
            case "echo-test" -> Uni.createFrom().item(new TestServiceInstance("echo-test", "localhost", testGrpcPort != 0 ? testGrpcPort : 49094));
            default -> Uni.createFrom().failure(new RuntimeException("Service not found: " + serviceName));
        };
    }
    
    @Override
    public Uni<List<ServiceInstance>> discoverAllInstances(String serviceName) {
        ServiceInstance instance = services.get(serviceName);
        if (instance != null) {
            return Uni.createFrom().item(List.of(instance));
        }
        return Uni.createFrom().item(List.of());
    }
    
    /**
     * Simple test implementation of ServiceInstance.
     */
    private static class TestServiceInstance implements ServiceInstance {
        private final String serviceName;
        private final String host;
        private final int port;
        
        TestServiceInstance(String serviceName, String host, int port) {
            this.serviceName = serviceName;
            this.host = host;
            this.port = port;
        }
        
        @Override
        public long getId() {
            return serviceName.hashCode();
        }
        
        @Override
        public String getHost() {
            return host;
        }
        
        @Override
        public int getPort() {
            return port;
        }
        
        @Override
        public boolean isSecure() {
            return false;
        }
        
        @Override
        public Optional<String> getPath() {
            return Optional.empty();
        }
        
        @Override
        public Map<String, String> getLabels() {
            return Map.of("service", serviceName);
        }
        
        @Override
        public io.smallrye.stork.api.Metadata<? extends io.smallrye.stork.api.MetadataKey> getMetadata() {
            return io.smallrye.stork.api.Metadata.empty();
        }
    }
}