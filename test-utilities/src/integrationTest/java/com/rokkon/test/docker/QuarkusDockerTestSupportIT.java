package com.rokkon.test.docker;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for QuarkusDockerTestSupport.
 * These tests verify Docker integration works correctly.
 */
@QuarkusIntegrationTest
class QuarkusDockerTestSupportIT {
    
    @Inject
    QuarkusDockerTestSupport dockerSupport;
    
    @Test
    void testDockerClientInjection() {
        // Verify the Docker support bean is properly injected
        assertThat(dockerSupport).isNotNull();
    }
    
    @Test
    @DisabledIfSystemProperty(named = "ci.build", matches = "true")
    void testDockerAvailability() {
        // This test checks if Docker is available
        // It's disabled in CI environments where Docker might not be available
        boolean dockerAvailable = dockerSupport.isDockerAvailable();
        
        // Just log the result - don't fail if Docker isn't available
        if (dockerAvailable) {
            System.out.println("Docker is available for testing");
            
            // If Docker is available, we can test listing containers
            var containers = dockerSupport.getRunningContainers();
            assertThat(containers).isNotNull();
            System.out.println("Found " + containers.size() + " running containers");
            
            // Test listing networks
            var networks = dockerSupport.getNetworks();
            assertThat(networks).isNotNull();
            assertThat(networks).isNotEmpty(); // At least the default networks should exist
            System.out.println("Found " + networks.size() + " Docker networks");
        } else {
            System.out.println("Docker is not available - skipping Docker-specific tests");
        }
    }
}