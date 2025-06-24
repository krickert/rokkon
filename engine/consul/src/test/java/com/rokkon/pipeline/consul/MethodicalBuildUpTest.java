package com.rokkon.pipeline.consul;

import com.rokkon.pipeline.consul.service.ClusterService;
import com.rokkon.pipeline.consul.service.ModuleWhitelistService;
import com.rokkon.pipeline.consul.service.PipelineConfigService;
import com.rokkon.pipeline.consul.test.ConsulTestResource;
import com.rokkon.pipeline.consul.test.MockTestModuleContainerResource;
import com.rokkon.pipeline.consul.test.TestSeedingService;
import com.rokkon.pipeline.consul.test.TestSeedingServiceImpl;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * Unit test for methodical build-up of the engine ecosystem.
 * Runs in dev mode with real Consul and test-module containers.
 */
@QuarkusTest
@QuarkusTestResource(ConsulTestResource.class)
@QuarkusTestResource(MockTestModuleContainerResource.class)
class MethodicalBuildUpTest extends MethodicalBuildUpTestBase {

    @Inject
    ClusterService clusterService;

    @Inject
    ModuleWhitelistService moduleWhitelistService;

    @Inject
    PipelineConfigService pipelineConfigService;

    @Inject
    TestSeedingServiceImpl testSeedingService;

    @Override
    protected ClusterService getClusterService() {
        return clusterService;
    }

    @Override
    protected ModuleWhitelistService getModuleWhitelistService() {
        return moduleWhitelistService;
    }

    @Override
    protected PipelineConfigService getPipelineConfigService() {
        return pipelineConfigService;
    }

    @Override
    protected TestSeedingService getTestSeedingService() {
        return testSeedingService;
    }
}
