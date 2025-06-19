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
 * Service discovery implementation using Stork.
 * This provides load balancing and failover out of the box.
 */
@ApplicationScoped
public class StorkServiceDiscovery implements ServiceDiscovery {
    
    private static final Logger LOG = LoggerFactory.getLogger(StorkServiceDiscovery.class);
    
    @Inject
    Stork stork;
    
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