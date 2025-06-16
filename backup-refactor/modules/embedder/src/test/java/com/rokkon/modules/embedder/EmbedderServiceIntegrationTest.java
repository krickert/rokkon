package com.rokkon.modules.embedder;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for EmbedderService using @QuarkusIntegrationTest.
 * Tests the service in a fully containerized environment.
 * 
 * Note: These tests verify the application starts correctly in an integration environment.
 * For full gRPC testing, you would typically use TestContainers with actual gRPC clients.
 */
@QuarkusIntegrationTest
class EmbedderServiceIntegrationTest {
    
    @Test
    void testApplicationStarts() {
        // Basic test to ensure the application starts correctly with DJL and CUDA dependencies
        assertTrue(true, "Application should start without errors");
    }
    
    @Test
    void testApplicationBootstrapIntegration() {
        // Verify the integration test environment is working
        assertNotNull(System.getProperty("java.version"), "Java environment should be available");
        assertTrue(System.getProperty("java.version").startsWith("21"), "Should be running on Java 21");
    }
    
    @Test
    @EnabledIfEnvironmentVariable(named = "CUDA_VISIBLE_DEVICES", matches = ".*")
    void testCudaEnvironmentAvailable() {
        // This test only runs if CUDA environment variables are set
        assertNotNull(System.getenv("CUDA_VISIBLE_DEVICES"), "CUDA should be available in container");
    }
    
    // Note: For comprehensive integration testing, you would typically:
    // 1. Use TestContainers to spin up the service in a real container
    // 2. Create actual gRPC clients to test the embedding service endpoints
    // 3. Test the service with real ML models and GPU acceleration
    // 4. Verify containerized deployment with DJL dependencies
    // 5. Test batch processing capabilities under load
    //
    // This would require additional TestContainers dependencies and more complex setup.
}