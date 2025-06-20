package com.rokkon.pipeline.consul.test;

import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.ext.consul.ConsulClient;
import io.vertx.ext.consul.ServiceOptions;
import io.vertx.ext.consul.CheckOptions;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

/**
 * Helper class to register test modules in Consul for testing.
 * This simulates what the engine's registration service would do in production.
 */
@ApplicationScoped
public class TestModuleRegistrationHelper {
    
    @Inject
    ConsulClient consulClient;
    
    /**
     * Register a test module in Consul.
     * This simulates what would happen when the CLI tool registers a module.
     */
    public Uni<Void> registerTestModule(String moduleName, String host, int port) {
        ServiceOptions service = new ServiceOptions()
            .setName(moduleName)
            .setId(moduleName + "-" + System.currentTimeMillis())
            .setAddress(host)
            .setPort(port)
            .setTags(List.of("grpc", "test"));
        
        // Add a gRPC health check
        CheckOptions check = new CheckOptions()
            .setGrpc(host + ":" + port)
            .setInterval("10s")
            .setDeregisterCriticalServiceAfter("1m");
        
        service.setCheckOptions(check);
        
        return consulClient.registerServiceWithCheck(service);
    }
    
    /**
     * Register the standard test-module that the tests expect.
     */
    public Uni<Void> registerStandardTestModule() {
        return registerTestModule("test-module", "localhost", 9094);
    }
    
    /**
     * Deregister all instances of a module.
     */
    public Uni<Void> deregisterModule(String moduleName) {
        return consulClient.catalogServiceNodes(moduleName)
            .flatMap(nodes -> {
                if (nodes == null || nodes.getList() == null) {
                    return Uni.createFrom().voidItem();
                }
                
                return Uni.combine().all().unis(
                    nodes.getList().stream()
                        .map(node -> consulClient.deregisterService(node.getServiceId()))
                        .toList()
                ).discardItems();
            });
    }
}