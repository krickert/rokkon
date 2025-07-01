package com.rokkon.pipeline.engine.grpc;

import com.rokkon.pipeline.consul.connection.ConsulConnectionManager;
import io.quarkus.test.Mock;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.ext.consul.ConsulClient;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

/**
 * Mock ConsulConnectionManager for testing without a real Consul instance.
 */
@Mock
@ApplicationScoped
public class MockConsulConnectionManager extends ConsulConnectionManager {
    
    @Override
    public void onStart(io.quarkus.runtime.StartupEvent ev) {
        // Do nothing - mock implementation doesn't need real Consul connection
    }
    
    @Override
    public Optional<io.vertx.ext.consul.ConsulClient> getClient() {
        // Return empty - tests should use mocked service discovery
        return Optional.empty();
    }
    
    @Override
    public Optional<ConsulClient> getMutinyClient() {
        // Return empty - tests should use mocked service discovery
        return Optional.empty();
    }
    
    @Override
    public ConsulConnectionConfig getConfiguration() {
        return new ConsulConnectionConfig("mock-consul", 8500, false);
    }
    
    @Override
    public Uni<ConsulConnectionResult> updateConnection(String host, int port) {
        return Uni.createFrom().item(new ConsulConnectionResult(true, "Mock connection", 
            new ConsulConnectionConfig(host, port, true)));
    }
    
    @Override
    public Uni<ConsulConnectionResult> disconnect() {
        return Uni.createFrom().item(new ConsulConnectionResult(true, "Mock disconnected", 
            new ConsulConnectionConfig("", 0, false)));
    }
}