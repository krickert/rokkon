package com.rokkon.pipeline.engine;

import com.rokkon.pipeline.config.service.ClusterService;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for custom cluster lifecycle scenarios.
 * Tests with real dependencies and custom cluster configuration.
 */
@QuarkusIntegrationTest
@TestProfile(CustomClusterLifecycleIT.CustomClusterProfile.class)
class CustomClusterLifecycleIT extends CustomClusterLifecycleTestBase {
    
    public static class CustomClusterProfile implements io.quarkus.test.junit.QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                "rokkon.cluster.name", "integration-custom-cluster",
                "quarkus.application.name", "integration-test-engine"
            );
        }
    }
    
    @Inject
    EngineLifecycle engineLifecycle;
    
    @Inject
    ClusterService clusterService;
    
    @ConfigProperty(name = "rokkon.cluster.name", defaultValue = "default-cluster")
    String clusterName;
    
    @Override
    protected EngineLifecycle getEngineLifecycle() {
        return engineLifecycle;
    }
    
    @Override
    protected ClusterService getClusterService() {
        return clusterService;
    }
    
    @Override
    protected String getCustomClusterName() {
        return clusterName;
    }
    
    @Test
    void testCustomClusterNameConfiguration() {
        // Verify custom cluster name is properly configured
        assertThat(clusterName).isEqualTo("integration-custom-cluster");
        assertThat(clusterName).isNotEqualTo("default-cluster");
    }
    
    @Test
    void testEngineLifecycleWithCustomCluster() {
        // Verify engine lifecycle is properly injected
        assertThat(engineLifecycle).isNotNull();
        assertThat(engineLifecycle.applicationName).isEqualTo("integration-test-engine");
        
        // Test manual startup
        engineLifecycle.onStart(startupEvent);
    }
    
    @Test
    void testClusterServiceInjection() {
        // Verify cluster service is properly injected in integration environment
        assertThat(clusterService).isNotNull();
        
        // In a real integration test with Consul running, we could:
        // - Check if the custom cluster exists
        // - Create the cluster if needed
        // - Verify cluster operations work correctly
    }
}