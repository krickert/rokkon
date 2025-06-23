package com.rokkon.test.containers;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Network;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test the ModuleContainerResource functionality
 */
class ModuleContainerResourceTest {
    
    @Test
    void testExtractModuleName() {
        ModuleContainerResource resource = new TestModuleContainerResource();
        
        // Test the network creation logic by checking start returns proper config
        Map<String, String> config = resource.start();
        
        try {
            // Verify essential configuration keys are present
            assertThat(config).containsKeys(
                "test.module.container.host",
                "test.module.container.grpc.port",
                "test.module.container.http.port",
                "test.module.container.internal.grpc.port",
                "test.module.container.internal.http.port",
                "test.module.container.id",
                "test.module.container.name",
                "test.module.container.image"
            );
            
            // Verify ports are properly mapped
            assertThat(config.get("test.module.container.internal.grpc.port")).isEqualTo("9090");
            assertThat(config.get("test.module.container.internal.http.port")).isEqualTo("8080");
            
            // Verify image name
            assertThat(config.get("test.module.container.image")).isEqualTo("rokkon/test-module:1.0.0-SNAPSHOT");
            
        } finally {
            resource.stop();
        }
    }
    
    @Test
    void testSharedNetworkManager() {
        // Test that SharedNetworkManager can create networks
        Network network = SharedNetworkManager.getOrCreateNetwork();
        assertThat(network).isNotNull();
        assertThat(network.getId()).isNotBlank();
        
        // Test that subsequent calls return the same network
        Network sameNetwork = SharedNetworkManager.getSharedNetwork();
        assertThat(sameNetwork).isNotNull();
        assertThat(sameNetwork.getId()).isEqualTo(network.getId());
        
        // Cleanup
        SharedNetworkManager.cleanup();
    }
}