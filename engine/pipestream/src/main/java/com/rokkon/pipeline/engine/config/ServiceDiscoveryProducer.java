package com.rokkon.pipeline.engine.config;

import com.rokkon.pipeline.engine.grpc.DynamicConsulServiceDiscovery;
import com.rokkon.pipeline.engine.grpc.ServiceDiscovery;
import com.rokkon.pipeline.engine.grpc.ServiceDiscoveryImpl;
import com.rokkon.pipeline.engine.service.StorkServiceDiscovery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Producer that selects the appropriate ServiceDiscovery implementation
 * based on configuration.
 */
@ApplicationScoped
public class ServiceDiscoveryProducer implements com.rokkon.pipeline.engine.grpc.ServiceDiscoveryProducer {
    
    private static final Logger LOG = LoggerFactory.getLogger(ServiceDiscoveryProducer.class);
    
    @ConfigProperty(name = "rokkon.service-discovery.type", defaultValue = "consul-direct")
    String discoveryType;
    
    @Inject
    @ServiceDiscoveryImpl(ServiceDiscoveryImpl.Type.CONSUL_DIRECT)
    DynamicConsulServiceDiscovery dynamicConsulDiscovery;
    
    @Inject
    @ServiceDiscoveryImpl(ServiceDiscoveryImpl.Type.STORK)
    StorkServiceDiscovery storkDiscovery;
    
    @Produces
    @Default
    @ApplicationScoped
    @Override
    public ServiceDiscovery produceServiceDiscovery() {
        LOG.info("Configuring service discovery with type: {}", discoveryType);
        
        return switch (discoveryType.toLowerCase()) {
            case "stork" -> {
                LOG.info("Using Stork-based service discovery (requires pre-configuration)");
                yield storkDiscovery;
            }
            case "consul-direct", "consul", "dynamic" -> {
                LOG.info("Using dynamic Consul service discovery (supports dynamic services)");
                yield dynamicConsulDiscovery;
            }
            default -> {
                LOG.warn("Unknown service discovery type '{}', defaulting to dynamic consul", discoveryType);
                yield dynamicConsulDiscovery;
            }
        };
    }
}