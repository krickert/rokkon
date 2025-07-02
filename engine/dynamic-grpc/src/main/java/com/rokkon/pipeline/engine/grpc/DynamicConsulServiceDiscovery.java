package com.rokkon.pipeline.engine.grpc;

import com.rokkon.pipeline.consul.connection.ConsulConnectionManager;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.vertx.UniHelper;
import io.smallrye.stork.api.LoadBalancer;
import io.vertx.ext.consul.ConsulClient;
import io.vertx.ext.consul.ServiceEntry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Dynamic Consul-based service discovery that uses Stork's LoadBalancer
 * for intelligent instance selection without requiring pre-configuration.
 * 
 * This class is typed as DynamicConsulServiceDiscovery only (not ServiceDiscovery) to avoid
 * ambiguous dependencies. It's made available as ServiceDiscovery through producers.
 * 
 * IMPORTANT: Uses optional injection pattern to work both standalone and integrated:
 * - When used in engine: ConsulConnectionManager is injected
 * - When used standalone: Creates its own ConsulClient from configuration
 */
@ApplicationScoped
@Typed(DynamicConsulServiceDiscovery.class)
@ServiceDiscoveryImpl(ServiceDiscoveryImpl.Type.CONSUL_DIRECT)
public class DynamicConsulServiceDiscovery implements ServiceDiscovery {
    
    private static final Logger LOG = LoggerFactory.getLogger(DynamicConsulServiceDiscovery.class);
    
    @Inject
    Instance<ConsulConnectionManager> connectionManagerInstance;
    
    @Inject
    Instance<io.vertx.core.Vertx> vertxInstance;
    
    @ConfigProperty(name = "quarkus.consul.host", defaultValue = "localhost")
    String consulHost;
    
    @ConfigProperty(name = "quarkus.consul.port", defaultValue = "8500")
    int consulPort;
    
    private ConsulClient consulClient;
    
    // Use Stork's load balancer for proper distribution
    private final LoadBalancer loadBalancer = new RandomLoadBalancer();
    
    // Track if we created the client ourselves
    private boolean standaloneClient = false;
    
    @PostConstruct
    void init() {
        if (connectionManagerInstance.isResolvable()) {
            // Use engine's connection manager when available
            LOG.info("Using injected ConsulConnectionManager for service discovery");
            ConsulConnectionManager connectionManager = connectionManagerInstance.get();
            connectionManager.getClient().ifPresent(client -> this.consulClient = client);
        } else {
            // Create our own ConsulClient for standalone usage
            LOG.info("Creating standalone ConsulClient for service discovery on {}:{}", consulHost, consulPort);
            createStandaloneConsulClient();
        }
    }
    
    private void createStandaloneConsulClient() {
        if (!vertxInstance.isResolvable()) {
            throw new IllegalStateException("Neither ConsulConnectionManager nor Vertx is available. Cannot create ConsulClient.");
        }
        
        io.vertx.core.Vertx vertx = vertxInstance.get();
        io.vertx.ext.consul.ConsulClientOptions options = new io.vertx.ext.consul.ConsulClientOptions()
            .setHost(consulHost)
            .setPort(consulPort);
        
        this.consulClient = ConsulClient.create(vertx, options);
        this.standaloneClient = true;
        LOG.info("Created standalone ConsulClient connected to {}:{}", consulHost, consulPort);
    }
    
    @PreDestroy
    void cleanup() {
        if (standaloneClient && consulClient != null) {
            LOG.info("Closing standalone ConsulClient");
            consulClient.close();
        }
    }
    
    @Override
    public Uni<io.smallrye.stork.api.ServiceInstance> discoverService(String serviceName) {
        LOG.debug("Discovering service {} dynamically from Consul", serviceName);
        
        return findHealthyInstances(serviceName)
            .map(instances -> {
                if (instances == null || instances.isEmpty()) {
                    throw new ServiceDiscoveryException(
                        "No healthy instances found for service: " + serviceName
                    );
                }
                
                // Convert to Stork ServiceInstances
                List<io.smallrye.stork.api.ServiceInstance> storkInstances = new ArrayList<>();
                long id = 0;
                for (ServiceEntry entry : instances) {
                    storkInstances.add(new StorkServiceInstanceAdapter(
                        id++,
                        entry.getService().getAddress(),
                        entry.getService().getPort()
                    ));
                }
                
                // Use Stork's load balancer to select an instance
                io.smallrye.stork.api.ServiceInstance selected = loadBalancer.selectServiceInstance(storkInstances);
                
                LOG.debug("Selected instance for service {}: {}:{} (id={})", 
                    serviceName, selected.getHost(), selected.getPort(), selected.getId());
                
                // Convert back to our ServiceInstance interface
                return new ConsulServiceInstance(
                    String.valueOf(selected.getId()), 
                    selected.getHost(), 
                    selected.getPort(), 
                    serviceName
                );
            });
    }
    
    @Override
    public Uni<List<io.smallrye.stork.api.ServiceInstance>> discoverAllInstances(String serviceName) {
        LOG.debug("Discovering all instances for service {} from Consul", serviceName);
        
        return findHealthyInstances(serviceName)
            .map(instances -> {
                if (instances == null) {
                    return List.of();
                }
                
                return instances.stream()
                    .map(entry -> {
                        String host = entry.getService().getAddress();
                        int port = entry.getService().getPort();
                        String id = entry.getService().getId();
                        return new ConsulServiceInstance(id, host, port, serviceName);
                    })
                    .collect(Collectors.toList());
            });
    }
    
    /**
     * Finds all healthy instances of a service in Consul.
     */
    @SuppressWarnings("unchecked")
    private Uni<List<ServiceEntry>> findHealthyInstances(String serviceName) {
        if (consulClient == null) {
            throw new ServiceDiscoveryException("ConsulClient not initialized. Check configuration.");
        }
        
        return UniHelper.toUni(consulClient.healthServiceNodes(serviceName, true))
            .map(serviceList -> {
                if (serviceList == null || serviceList.getList() == null) {
                    LOG.warn("No healthy nodes found for service '{}' in Consul", serviceName);
                    return List.of();
                }
                
                LOG.debug("Found {} healthy instances for service '{}'", 
                    serviceList.getList().size(), serviceName);
                
                return serviceList.getList();
            })
            .onFailure().invoke(error -> 
                LOG.error("Failed to query Consul for service '{}'", serviceName, error)
            )
            .map(list -> (List<ServiceEntry>) list);
    }
    
    /**
     * Simple ServiceInstance implementation for Consul service entries.
     */
    private static class ConsulServiceInstance implements io.smallrye.stork.api.ServiceInstance {
        private final String id;
        private final String host;
        private final int port;
        private final String serviceName;
        
        ConsulServiceInstance(String id, String host, int port, String serviceName) {
            this.id = id;
            this.host = host;
            this.port = port;
            this.serviceName = serviceName;
        }
        
        @Override
        public long getId() {
            return Long.parseLong(id.replaceAll("[^0-9]", "").substring(0, Math.min(8, id.replaceAll("[^0-9]", "").length())));
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
            return false; // gRPC modules use plain text internally
        }
        
        @Override
        public Optional<String> getPath() {
            return Optional.empty(); // Not used for gRPC
        }
        
        @Override
        public io.smallrye.stork.api.Metadata<? extends io.smallrye.stork.api.MetadataKey> getMetadata() {
            return io.smallrye.stork.api.Metadata.empty();
        }
    }
    
    /**
     * Adapter for Stork ServiceInstance
     */
    private static class StorkServiceInstanceAdapter implements io.smallrye.stork.api.ServiceInstance {
        private final long id;
        private final String host;
        private final int port;
        
        StorkServiceInstanceAdapter(long id, String host, int port) {
            this.id = id;
            this.host = host;
            this.port = port;
        }
        
        @Override
        public long getId() {
            return id;
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
        public io.smallrye.stork.api.Metadata<? extends io.smallrye.stork.api.MetadataKey> getMetadata() {
            return io.smallrye.stork.api.Metadata.empty();
        }
    }
    
    /**
     * Custom exception for service discovery failures.
     */
    public static class ServiceDiscoveryException extends RuntimeException {
        public ServiceDiscoveryException(String message) {
            super(message);
        }
        
        public ServiceDiscoveryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}