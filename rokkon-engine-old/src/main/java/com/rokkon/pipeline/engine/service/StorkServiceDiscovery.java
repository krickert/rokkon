package com.rokkon.pipeline.engine.service;

import io.smallrye.mutiny.Uni;
import io.smallrye.stork.Stork;
import io.smallrye.stork.api.ServiceInstance;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

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
public class StorkServiceDiscovery implements ServiceDiscovery {
    
    private static final Logger LOG = LoggerFactory.getLogger(StorkServiceDiscovery.class);
    
    @Inject
    Stork stork;
    
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
            .onFailure().invoke(error -> 
                LOG.error("Failed to discover service {}", serviceName, error)
            );
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
}