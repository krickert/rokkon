package com.rokkon.pipeline.engine.registration;

import com.rokkon.pipeline.consul.test.ConsulTestResource;
import com.rokkon.test.containers.TestModuleContainerResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

/**
 * Simple test to debug network configuration
 */
@QuarkusIntegrationTest
@QuarkusTestResource(SharedNetworkConsulResource.class)
@QuarkusTestResource(TestModuleContainerResource.class)
class NetworkDebugIT {
    
    @ConfigProperty(name = "test.network.id", defaultValue = "not-set")
    String networkId;
    
    @ConfigProperty(name = "consul.container.host", defaultValue = "not-set")
    String consulContainerHost;
    
    @ConfigProperty(name = "test.module.container.network.alias", defaultValue = "not-set")
    String moduleNetworkAlias;
    
    @Test
    void debugNetworkConfiguration() {
        System.out.println("\n=== Network Configuration Debug ===");
        System.out.println("Network ID: " + networkId);
        System.out.println("Consul container host: " + consulContainerHost);
        System.out.println("Module network alias: " + moduleNetworkAlias);
        System.out.println("===================================\n");
    }
}