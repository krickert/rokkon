package com.rokkon.pipeline.consul.service;

import com.rokkon.pipeline.config.service.ClusterService;
import com.rokkon.pipeline.config.service.ModuleWhitelistService;
import com.rokkon.pipeline.consul.test.UnifiedTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;

/**
 * Unit test for ModuleWhitelistService basic functionality.
 * Tests without requiring real Consul.
 */
@QuarkusTest
@TestProfile(UnifiedTestProfile.class)
class ModuleWhitelistServiceSimpleUnitTest extends ModuleWhitelistServiceSimpleTestBase {

    @Inject
    ModuleWhitelistService whitelistService;

    @Inject
    ClusterService clusterService;

    @Override
    protected ModuleWhitelistService getWhitelistService() {
        return whitelistService;
    }

    @Override
    protected ClusterService getClusterService() {
        return clusterService;
    }
}