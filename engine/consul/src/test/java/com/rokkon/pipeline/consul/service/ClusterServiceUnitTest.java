package com.rokkon.pipeline.consul.service;

import com.rokkon.pipeline.config.service.ClusterService;
import com.rokkon.pipeline.consul.test.RequiresMockCluster;
import com.rokkon.pipeline.consul.test.UnifiedTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;

/**
 * Unit test for ClusterService that uses mocked dependencies.
 * Tests service logic without requiring real Consul.
 */
@QuarkusTest
@TestProfile(UnifiedTestProfile.class)
@RequiresMockCluster
class ClusterServiceUnitTest extends ClusterServiceTestBase {
    
    @Inject
    ClusterService clusterService;
    
    @BeforeEach
    void setup() {
        // Clear any previous test data
        if (clusterService instanceof MockClusterService) {
            ((MockClusterService) clusterService).clear();
        }
    }
    
    @Override
    protected ClusterService getClusterService() {
        return clusterService;
    }
}