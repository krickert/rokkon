package com.rokkon.pipeline.consul.test;

import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.ext.consul.ConsulClient;
import io.vertx.ext.consul.ServiceOptions;
import io.vertx.ext.consul.CheckOptions;
import io.vertx.ext.consul.ServiceEntry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper class to register test modules in Consul for testing.
 * This simulates what the engine's registration service would do in production.
 */
@ApplicationScoped
public class TestModuleRegistrationHelper {

    private static final Logger LOG = Logger.getLogger(TestModuleRegistrationHelper.class);

    @Inject
    io.vertx.mutiny.core.Vertx vertx;
    
    @ConfigProperty(name = "consul.host")
    String consulHost;
    
    @ConfigProperty(name = "consul.port")
    int consulPort;
    
    private ConsulClient consulClient;

    /**
     * Get or create the Consul client.
     */
    private ConsulClient getConsulClient() {
        if (consulClient == null) {
            io.vertx.ext.consul.ConsulClientOptions options = new io.vertx.ext.consul.ConsulClientOptions()
                .setHost(consulHost)
                .setPort(consulPort);
            consulClient = ConsulClient.create(vertx, options);
        }
        return consulClient;
    }
    
    /**
     * Register a test module in Consul.
     * This simulates what would happen when the CLI tool registers a module.
     */
    public Uni<Void> registerTestModule(String moduleName, String host, int port) {
        return registerTestModule(moduleName, host, port, Collections.emptyMap(), List.of("grpc", "test"));
    }

    /**
     * Register a test module in Consul with custom metadata.
     * 
     * @param moduleName The name of the module
     * @param host The host where the module is running
     * @param port The port where the module is listening
     * @param metadata Additional metadata to store with the service
     * @return A Uni that completes when the registration is done
     */
    public Uni<Void> registerTestModule(String moduleName, String host, int port, Map<String, String> metadata) {
        return registerTestModule(moduleName, host, port, metadata, List.of("grpc", "test"));
    }

    /**
     * Register a test module in Consul with custom metadata and tags.
     * 
     * @param moduleName The name of the module
     * @param host The host where the module is running
     * @param port The port where the module is listening
     * @param metadata Additional metadata to store with the service
     * @param tags Tags to apply to the service
     * @return A Uni that completes when the registration is done
     */
    public Uni<Void> registerTestModule(String moduleName, String host, int port, Map<String, String> metadata, List<String> tags) {
        String serviceId = moduleName + "-" + System.currentTimeMillis();
        LOG.infof("Registering test module %s with ID %s at %s:%d", moduleName, serviceId, host, port);

        ServiceOptions service = new ServiceOptions()
            .setName(moduleName)
            .setId(serviceId)
            .setAddress(host)
            .setPort(port)
            .setTags(tags);

        // Add metadata if provided
        if (metadata != null && !metadata.isEmpty()) {
            service.setMeta(metadata);
        }

        // Add a gRPC health check
        CheckOptions check = new CheckOptions()
            .setGrpc(host + ":" + port)
            .setInterval("10s")
            .setDeregisterAfter("1m");

        service.setCheckOptions(check);

        return getConsulClient().registerService(service);
    }

    /**
     * Register the standard test-module that the tests expect.
     */
    public Uni<Void> registerStandardTestModule() {
        return registerTestModule("test-module", "localhost", 9094);
    }

    /**
     * Register the standard test-module with custom metadata.
     * 
     * @param metadata Additional metadata to store with the service
     */
    public Uni<Void> registerStandardTestModule(Map<String, String> metadata) {
        return registerTestModule("test-module", "localhost", 9094, metadata);
    }

    /**
     * Register multiple test modules at once.
     * Useful for setting up complex test scenarios.
     * 
     * @param moduleConfigs List of module configurations to register
     * @return A Uni that completes when all registrations are done
     */
    public Uni<Void> registerMultipleModules(List<ModuleConfig> moduleConfigs) {
        return Uni.combine().all().unis(
            moduleConfigs.stream()
                .map(config -> registerTestModule(
                    config.moduleName, 
                    config.host, 
                    config.port, 
                    config.metadata, 
                    config.tags))
                .toList()
        ).discardItems();
    }

    /**
     * Deregister all instances of a module.
     */
    public Uni<Void> deregisterModule(String moduleName) {
        LOG.infof("Deregistering all instances of module %s", moduleName);
        // For simplicity, we'll just deregister the service by name
        // This is a simplified approach that doesn't rely on accessing service IDs from catalog nodes
        return getConsulClient().deregisterService(moduleName)
            .onItem().invoke(() -> LOG.infof("Deregistered service %s", moduleName));
    }

    /**
     * Deregister a specific instance of a module by service ID.
     * 
     * @param serviceId The ID of the service to deregister
     * @return A Uni that completes when the deregistration is done
     */
    public Uni<Void> deregisterModuleById(String serviceId) {
        LOG.infof("Deregistering module with ID %s", serviceId);
        return getConsulClient().deregisterService(serviceId);
    }

    /**
     * Check if a module is registered in Consul.
     * 
     * @param moduleName The name of the module to check
     * @return A Uni that resolves to true if the module is registered, false otherwise
     */
    public Uni<Boolean> isModuleRegistered(String moduleName) {
        return getConsulClient().catalogServiceNodes(moduleName)
            .map(nodes -> nodes != null && nodes.getList() != null && !nodes.getList().isEmpty());
    }

    /**
     * Get all registered instances of a module.
     * 
     * @param moduleName The name of the module
     * @return A Uni that resolves to a list of service entries
     */
    public Uni<List<Object>> getModuleInstances(String moduleName) {
        return getConsulClient().catalogServiceNodes(moduleName)
            .map(nodes -> {
                if (nodes == null || nodes.getList() == null) {
                    return Collections.emptyList();
                }
                // Create a new list and add all elements from the original list
                List<Object> result = new ArrayList<>();
                nodes.getList().forEach(result::add);
                return result;
            });
    }

    /**
     * Get details of a specific module instance by service ID.
     * 
     * @param serviceId The ID of the service
     * @return A Uni that resolves to the service object, or null if not found
     */
    public Uni<Object> getModuleById(String serviceId) {
        // For simplicity, we'll just return the service ID as a string
        // In a real implementation, you would query Consul for the service details
        return Uni.createFrom().item(serviceId);
    }

    /**
     * Configuration for a module to be registered.
     */
    public static class ModuleConfig {
        private final String moduleName;
        private final String host;
        private final int port;
        private final Map<String, String> metadata;
        private final List<String> tags;

        public ModuleConfig(String moduleName, String host, int port) {
            this(moduleName, host, port, Collections.emptyMap(), List.of("grpc", "test"));
        }

        public ModuleConfig(String moduleName, String host, int port, Map<String, String> metadata) {
            this(moduleName, host, port, metadata, List.of("grpc", "test"));
        }

        public ModuleConfig(String moduleName, String host, int port, Map<String, String> metadata, List<String> tags) {
            this.moduleName = moduleName;
            this.host = host;
            this.port = port;
            this.metadata = metadata != null ? new HashMap<>(metadata) : Collections.emptyMap();
            this.tags = tags != null ? List.copyOf(tags) : List.of("grpc", "test");
        }
    }
}
