package com.rokkon.pipeline.consul.service;

import com.rokkon.pipeline.config.service.ClusterService;
import com.rokkon.pipeline.config.service.ModuleWhitelistService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base test class to verify ModuleWhitelistService basic functionality.
 */
public abstract class ModuleWhitelistServiceSimpleTestBase {

    protected abstract ModuleWhitelistService getWhitelistService();
    protected abstract ClusterService getClusterService();

    @Test
    void testServiceInjection() {
        assertThat(getWhitelistService()).isNotNull();
        assertThat(getClusterService()).isNotNull();
    }

    @Test
    void testListModulesOnEmptyCluster() {
        // This should work even if cluster doesn't exist - it should return empty list
        var modules = getWhitelistService().listWhitelistedModules("non-existent-cluster")
            .await().indefinitely();

        assertThat(modules).isNotNull();
        assertThat(modules).isEmpty();
    }
}