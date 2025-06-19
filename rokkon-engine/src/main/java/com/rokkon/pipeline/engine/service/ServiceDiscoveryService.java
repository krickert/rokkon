package com.rokkon.pipeline.engine.service;

import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.consul.CheckList;
import io.vertx.ext.consul.ConsulClient;
import io.vertx.ext.consul.ServiceEntryList;
import io.vertx.mutiny.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service discovery implementation using Consul.
 * Discovers healthy service instances and provides basic load balancing.
 */
@ApplicationScoped
public class ServiceDiscoveryService {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceDiscoveryService.class);

    @Inject
    Vertx vertx;

    @ConfigProperty(name = "consul.host", defaultValue = "localhost")
    String consulHost;

    @ConfigProperty(name = "consul.port", defaultValue = "8500")
    int consulPort;

    // Cache of Consul clients per datacenter
    private final Map<String, ConsulClient> consulClients = new ConcurrentHashMap<>();

    // Random for simple load balancing
    private final Random random = new Random();

    /**
     * Discover a healthy instance of the specified service.
     * Uses round-robin load balancing if multiple instances are available.
     */
    public Uni<ServiceInstance> discoverHealthyService(String serviceName) {
        LOG.debug("Discovering healthy instances for service: {}", serviceName);

        ConsulClient consulClient = getConsulClient();

        return Uni.createFrom().emitter(emitter -> {
            // Just query for healthy service nodes without options for now
            consulClient.healthServiceNodes(serviceName, true)
                    .onSuccess(serviceList -> {
                        // serviceList is a ServiceEntryList (extends JsonArray)
                        JsonArray entries = (JsonArray) serviceList;
                        if (entries.isEmpty()) {
                            LOG.warn("No healthy instances found for service: {}", serviceName);
                            emitter.complete(null);
                            return;
                        }

                        // Simple random load balancing
                        JsonObject selected = entries.getJsonObject(random.nextInt(entries.size()));
                        
                        JsonObject service = selected.getJsonObject("Service");
                        String address = service.getString("Address");
                        int port = service.getInteger("Port");
                        String serviceId = service.getString("ID");

                        LOG.debug("Selected service instance: {} at {}:{}", serviceId, address, port);

                        ServiceInstance instance = new ServiceInstance(
                                serviceName,
                                serviceId,
                                address,
                                port,
                                extractTags(service)
                        );

                        emitter.complete(instance);
                    })
                    .onFailure(error -> {
                        LOG.error("Failed to discover service: {}", serviceName, error);
                        emitter.fail(error);
                    });
        });
    }

    /**
     * Discover all healthy instances of a service.
     */
    public Uni<List<ServiceInstance>> discoverAllHealthyInstances(String serviceName) {
        LOG.debug("Discovering all healthy instances for service: {}", serviceName);

        ConsulClient consulClient = getConsulClient();

        return Uni.createFrom().emitter(emitter -> {
            // Query for healthy service nodes
            consulClient.healthServiceNodes(serviceName, true)
                    .onSuccess(serviceList -> {
                        // serviceList is a ServiceEntryList (extends JsonArray)
                        JsonArray entries = (JsonArray) serviceList;
                        List<ServiceInstance> instances = entries.stream()
                                .map(obj -> {
                                    JsonObject entry = (JsonObject) obj;
                                    JsonObject service = entry.getJsonObject("Service");
                                    return new ServiceInstance(
                                            serviceName,
                                            service.getString("ID"),
                                            service.getString("Address"),
                                            service.getInteger("Port"),
                                            extractTags(service)
                                    );
                                })
                                .toList();

                        LOG.debug("Found {} healthy instances for service {}", 
                                instances.size(), serviceName);
                        
                        emitter.complete(instances);
                    })
                    .onFailure(error -> {
                        LOG.error("Failed to discover all instances for service: {}", 
                                serviceName, error);
                        emitter.fail(error);
                    });
        });
    }

    /**
     * Check if a specific service instance is healthy.
     */
    public Uni<Boolean> isServiceHealthy(String serviceName, String serviceId) {
        ConsulClient consulClient = getConsulClient();

        return Uni.createFrom().emitter(emitter -> {
            consulClient.healthChecks(serviceName)
                    .onSuccess(checks -> {
                        // checks is a CheckList (extends JsonArray)
                        JsonArray checkArray = (JsonArray) checks;
                        boolean isHealthy = true;
                        for (int i = 0; i < checkArray.size(); i++) {
                            JsonObject check = checkArray.getJsonObject(i);
                            if (serviceId.equals(check.getString("ServiceID")) && 
                                !"passing".equals(check.getString("Status"))) {
                                isHealthy = false;
                                break;
                            }
                        }
                        
                        emitter.complete(isHealthy);
                    })
                    .onFailure(error -> {
                        LOG.error("Failed to check health for service: {} instance: {}", 
                                serviceName, serviceId, error);
                        emitter.fail(error);
                    });
        });
    }

    /**
     * Get or create Consul client for the default datacenter.
     */
    private ConsulClient getConsulClient() {
        return consulClients.computeIfAbsent("default", dc -> {
            LOG.info("Creating Consul client for {}:{}", consulHost, consulPort);
            return ConsulClient.create(vertx.getDelegate(), 
                    new io.vertx.ext.consul.ConsulClientOptions()
                            .setHost(consulHost)
                            .setPort(consulPort));
        });
    }

    /**
     * Extract tags from service JSON.
     */
    private List<String> extractTags(JsonObject service) {
        JsonArray tags = service.getJsonArray("Tags");
        if (tags == null) {
            return List.of();
        }
        return tags.stream()
                .map(Object::toString)
                .toList();
    }

    /**
     * Represents a discovered service instance.
     */
    public static class ServiceInstance {
        private final String serviceName;
        private final String serviceId;
        private final String address;
        private final int port;
        private final List<String> tags;

        public ServiceInstance(String serviceName, String serviceId, 
                             String address, int port, List<String> tags) {
            this.serviceName = serviceName;
            this.serviceId = serviceId;
            this.address = address;
            this.port = port;
            this.tags = tags;
        }

        public String getServiceName() {
            return serviceName;
        }

        public String getServiceId() {
            return serviceId;
        }

        public String getAddress() {
            return address;
        }

        public int getPort() {
            return port;
        }

        public List<String> getTags() {
            return tags;
        }

        @Override
        public String toString() {
            return String.format("ServiceInstance[%s:%s at %s:%d]", 
                    serviceName, serviceId, address, port);
        }
    }
}