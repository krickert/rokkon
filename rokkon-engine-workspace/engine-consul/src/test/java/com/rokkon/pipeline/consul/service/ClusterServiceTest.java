package com.rokkon.pipeline.consul.service;

import com.rokkon.pipeline.consul.test.ConsulTestResource;
import com.rokkon.pipeline.validation.ValidationResult;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.Optional;

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