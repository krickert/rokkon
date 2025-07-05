package com.rokkon.pipeline.engine.dev;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Simple test for HostIPDetector without mocking.
 * This test verifies the basic functionality with the real Docker client.
 */
@QuarkusTest
@TestProfile(DevModeTestProfile.class)
class HostIPDetectorSimpleTest {
    
    @Inject
    HostIPDetector hostIPDetector;
    
    @Test
    void testDetectHostIP() {
        // Test detection - should return a valid IP
        String detectedIP = hostIPDetector.detectHostIP();
        
        assertThat(detectedIP).isNotNull();
        assertThat(detectedIP).isNotEmpty();
        
        // Should be either host.docker.internal or an IP address
        boolean isValidIP = detectedIP.equals("host.docker.internal") || 
                           detectedIP.matches("\\d+\\.\\d+\\.\\d+\\.\\d+");
        assertThat(isValidIP).isTrue();
    }
    
    @Test
    void testCachesDetectedIP() {
        // First call
        String firstCall = hostIPDetector.detectHostIP();
        
        // Second call should return the same cached value
        String secondCall = hostIPDetector.detectHostIP();
        
        assertThat(firstCall).isEqualTo(secondCall);
    }
    
    @Test
    void testResetCache() {
        // Get initial IP
        String firstIP = hostIPDetector.detectHostIP();
        assertThat(firstIP).isNotNull();
        
        // Reset cache
        hostIPDetector.resetCache();
        
        // Should re-detect (result should be the same, but it went through detection again)
        String secondIP = hostIPDetector.detectHostIP();
        assertThat(secondIP).isNotNull();
        assertThat(secondIP).isEqualTo(firstIP); // Should detect the same IP
    }
}