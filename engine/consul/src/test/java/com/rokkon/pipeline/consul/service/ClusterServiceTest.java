package com.rokkon.pipeline.consul.service;

import com.rokkon.pipeline.config.service.ClusterService;
import com.rokkon.pipeline.consul.test.ConsulTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@QuarkusTestResource(ConsulTestResource.class)
class ClusterServiceTest extends ClusterServiceTestBase {
    
    @Inject
    ClusterService clusterService;
    
    @Override
    protected ClusterService getClusterService() {
        return clusterService;
    }
}