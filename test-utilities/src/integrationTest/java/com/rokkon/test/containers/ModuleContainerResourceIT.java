package com.rokkon.test.containers;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Network;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for container utilities.
 * Note: Actual container startup tests should be in the modules that have real containers.
 * These tests verify the utility classes work correctly.
 */
class ModuleContainerResourceIT {
    
    @Test
    void testSharedNetworkManager() {
        // Test that SharedNetworkManager can create and manage networks
        Network network1 = SharedNetworkManager.getOrCreateNetwork();
        assertThat(network1).isNotNull();
        assertThat(network1.getId()).isNotBlank();
        
        // Subsequent calls should return the same network
        Network network2 = SharedNetworkManager.getSharedNetwork();
        assertThat(network2).isNotNull();
        assertThat(network2.getId()).isEqualTo(network1.getId());
        
        // Cleanup
        SharedNetworkManager.cleanup();
        
        // After cleanup, should create a new network
        Network network3 = SharedNetworkManager.getOrCreateNetwork();
        assertThat(network3).isNotNull();
        assertThat(network3.getId()).isNotEqualTo(network1.getId());
        
        // Final cleanup
        SharedNetworkManager.cleanup();
    }
}