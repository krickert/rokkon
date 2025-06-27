package com.rokkon.pipeline.consul.service;

import com.rokkon.pipeline.config.service.ClusterService;
import com.rokkon.pipeline.config.service.ModuleWhitelistService;
import com.rokkon.pipeline.consul.test.ConsulTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Simple test to verify ModuleWhitelistService is properly injected.
 */
@QuarkusTest
@QuarkusTestResource(ConsulTestResource.class)
class ModuleWhitelistServiceSimpleTest {

    @Inject
    ModuleWhitelistService whitelistService;

    @Inject
    ClusterService clusterService;

    @Test
    void testServiceInjection() {
        assertThat(whitelistService).isNotNull();
        assertThat(clusterService).isNotNull();
    }

    @Test
    void testListModulesOnEmptyCluster() {
        // This should work even if cluster doesn't exist - it should return empty list
        var modules = whitelistService.listWhitelistedModules("non-existent-cluster")
            .await().indefinitely();

        assertThat(modules).isNotNull();
        assertThat(modules).isEmpty();
    }
}
