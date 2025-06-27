package com.rokkon.pipeline.consul.service;

import com.rokkon.pipeline.config.service.ClusterService;
import com.rokkon.pipeline.config.service.PipelineConfigService;
import com.rokkon.pipeline.consul.test.ConsulTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * Unit test for PipelineConfigService with real Consul backend.
 * Extends PipelineConfigServiceTestBase to reuse common test logic.
 */
@QuarkusTest
@QuarkusTestResource(ConsulTestResource.class)
class PipelineConfigServiceTest extends PipelineConfigServiceTestBase {

    @Inject
    PipelineConfigService service;

    @Inject
    ClusterService clusterService;

    @Override
    protected PipelineConfigService getPipelineConfigService() {
        return service;
    }

    @Override
    protected ClusterService getClusterService() {
        return clusterService;
    }
}