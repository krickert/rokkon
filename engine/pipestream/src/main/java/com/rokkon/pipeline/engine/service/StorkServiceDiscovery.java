package com.rokkon.pipeline.engine.service;

import com.rokkon.pipeline.engine.grpc.ServiceDiscovery;
import com.rokkon.pipeline.engine.grpc.ServiceDiscoveryImpl;
import io.smallrye.mutiny.Uni;
import io.smallrye.stork.Stork;
import io.smallrye.stork.api.ServiceInstance;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Service discovery implementation using SmallRye Stork.
 * <p>
 * This class provides service discovery capabilities with built-in load balancing and failover.
 * It integrates with Consul through Stork's service discovery providers to locate available
 * instances of modules registered in the Rokkon Engine ecosystem.
 * </p>
 * 
 * <h2>Key Features:</h2>
 * <ul>
 *   <li>Automatic load balancing across service instances</li>
 *   <li>Built-in failover to healthy instances</li>
 *   <li>Integration with Consul for dynamic service discovery</li>
 *   <li>Non-blocking reactive operations using Mutiny</li>
 * </ul>
 * 
 * <h2>Configuration:</h2>
 * <p>
 * Service discovery is configured through application properties:
 * <pre>
 * stork.{service-name}.service-discovery.type=consul
 * stork.{service-name}.service-discovery.consul-host=localhost
 * stork.{service-name}.service-discovery.consul-port=8500
 * stork.{service-name}.load-balancer.type=round-robin
 * </pre>
 * </p>
 * 
 * @see ServiceDiscovery
 * @see io.smallrye.stork.Stork
 * @since 1.0
 */
@ApplicationScoped
@Typed(StorkServiceDiscovery.class)
@ServiceDiscoveryImpl(ServiceDiscoveryImpl.Type.STORK)
public class StorkServiceDiscovery implements ServiceDiscovery {
    
    private static final Logger LOG = LoggerFactory.getLogger(StorkServiceDiscovery.class);
    
    @Inject
    Stork stork;
    
    @ConfigProperty(name = "consul.host", defaultValue = "localhost")
    String consulHost;
    
    @ConfigProperty(name = "consul.port", defaultValue = "8500")
    String consulPort;
    
    @Inject
    ObjectMapper objectMapper;
    
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    
    private final Random random = new Random();
    
    /**
     * Discovers a single healthy instance of the specified service.
     * <p>
     * This method uses Stork's load balancing algorithm to select an appropriate
     * instance from all available healthy instances. The selection strategy is
     * configurable (e.g., round-robin, least-connections, random).
     * </p>
     * 
     * @param serviceName the name of the service to discover (e.g., "chunker", "parser")
     * @return a {@link Uni} emitting the selected {@link ServiceInstance}, or a failure
     *         if no healthy instances are available
     * @throws io.smallrye.stork.api.NoServiceInstanceFoundException if no instances are found
     */
    @Override
    public Uni<ServiceInstance> discoverService(String serviceName) {
        LOG.debug("Discovering service {} using Stork", serviceName);
        
        return stork.getService(serviceName)
            .selectInstance()
            .onItem().invoke(instance -> 
                LOG.debug("Selected instance for {}: {}:{}", 
                    serviceName, instance.getHost(), instance.getPort())
            )
            .onFailure().recoverWithUni(error -> {
                LOG.debug("Stork lookup failed for {}, falling back to direct Consul query", serviceName);
                return discoverServiceDirectlyFromConsul(serviceName);
            });
    }
    
    /**
     * Directly query Consul for service instances when Stork doesn't have the service configured.
     * This enables dynamic service discovery without pre-configuration.
     */
    private Uni<ServiceInstance> discoverServiceDirectlyFromConsul(String serviceName) {
        String url = String.format("http://%s:%s/v1/health/service/%s?passing=true", 
            consulHost, consulPort, serviceName);
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(5))
            .GET()
            .build();
        
        return Uni.createFrom().completionStage(
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
        )
        .map(response -> {
            if (response.statusCode() != 200) {
                throw new RuntimeException("No service defined for name " + serviceName);
            }
            
            try {
                JsonNode root = objectMapper.readTree(response.body());
                if (root.isArray() && root.size() > 0) {
                    // Select a random healthy instance for load balancing
                    int index = root.size() > 1 ? random.nextInt(root.size()) : 0;
                    JsonNode instance = root.get(index);
                    JsonNode service = instance.get("Service");
                    
                    String host = service.get("Address").asText();
                    int port = service.get("Port").asInt();
                    String id = service.get("ID").asText();
                    
                    LOG.debug("Found service {} instance via direct Consul lookup: {}:{}", 
                        serviceName, host, port);
                    
                    // Create a simple ServiceInstance implementation
                    return new ConsulServiceInstance(id, host, port, serviceName);
                }
                throw new RuntimeException("No healthy instances found for service: " + serviceName);
            } catch (Exception e) {
                LOG.error("Failed to parse Consul response for service {}", serviceName, e);
                throw new RuntimeException("Failed to discover service: " + serviceName, e);
            }
        });
    }
    
    /**
     * Discovers all available instances of the specified service.
     * <p>
     * This method retrieves all registered instances of a service, regardless of their
     * health status. This is useful for monitoring, debugging, or implementing custom
     * load balancing strategies.
     * </p>
     * 
     * @param serviceName the name of the service to discover (e.g., "chunker", "parser")
     * @return a {@link Uni} emitting a list of all {@link ServiceInstance}s, which may
     *         be empty if no instances are registered
     */
    @Override
    public Uni<List<ServiceInstance>> discoverAllInstances(String serviceName) {
        LOG.debug("Discovering all instances for service {} using Stork", serviceName);
        
        return stork.getService(serviceName)
            .getInstances()
            .onItem().invoke(instances -> 
                LOG.debug("Found {} instances for service {}", instances.size(), serviceName)
            )
            .onFailure().invoke(error -> 
                LOG.error("Failed to discover instances for service {}", serviceName, error)
            );
    }
    
    /**
     * Simple ServiceInstance implementation for direct Consul lookups.
     */
    private static class ConsulServiceInstance implements ServiceInstance {
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
            try {
                return Long.parseLong(id.replaceAll("[^0-9]", ""));
            } catch (Exception e) {
                return id.hashCode();
            }
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
}