package com.rokkon.pipeline.consul;

import com.rokkon.pipeline.config.model.Cluster;
import com.rokkon.pipeline.config.model.ClusterMetadata;
import com.rokkon.pipeline.config.service.ClusterService;
import com.rokkon.pipeline.config.service.ModuleWhitelistService;
import com.rokkon.pipeline.config.service.PipelineConfigService;
import com.rokkon.pipeline.consul.test.TestSeedingService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;

import java.util.List;

import static java.util.Map.of;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit test version of MethodicalBuildUpTest using mocks.
 * This tests the service interaction logic without requiring real Consul or containers.
 */
@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MethodicalBuildUpUnitTest extends MethodicalBuildUpTestBase {

    @InjectMock
    ClusterService clusterService;

    @InjectMock
    ModuleWhitelistService moduleWhitelistService;

    @InjectMock
    PipelineConfigService pipelineConfigService;

    @InjectMock
    TestSeedingService testSeedingService;

    @BeforeEach
    void setupMocks() {
        // Reset mocks before each test
        Mockito.reset(clusterService, moduleWhitelistService, pipelineConfigService, testSeedingService);

        // Setup default mock behaviors
        setupDefaultMocks();
    }

    private void setupDefaultMocks() {
        // Mock TestSeedingService steps
        when(testSeedingService.seedStep0_ConsulStarted())
            .thenReturn(Uni.createFrom().item(true));

        when(testSeedingService.seedStep1_ClustersCreated())
            .thenReturn(Uni.createFrom().item(true));

        when(testSeedingService.seedStep2_ContainerAccessible())
            .thenReturn(Uni.createFrom().item(true));

        when(testSeedingService.seedStep3_ContainerRegistered())
            .thenReturn(Uni.createFrom().item(true));

        when(testSeedingService.seedStep4_EmptyPipelineCreated())
            .thenReturn(Uni.createFrom().item(true));

        when(testSeedingService.seedStep5_FirstPipelineStepAdded())
            .thenReturn(Uni.createFrom().item(true));

        when(testSeedingService.teardownAll())
            .thenReturn(Uni.createFrom().item(true));

        // Mock ClusterService
        when(clusterService.createCluster(anyString()))
            .thenReturn(Uni.createFrom().item(ValidationResult.success()));

        when(clusterService.listClusters())
            .thenReturn(Uni.createFrom().item(List.of(
                new Cluster(DEFAULT_CLUSTER, "Default cluster", new ClusterMetadata(DEFAULT_CLUSTER, java.time.Instant.now(), null, java.util.Map.of("description", "default-description", "owner", "default-owner"))),
                new Cluster(TEST_CLUSTER, "Test cluster", new ClusterMetadata(TEST_CLUSTER, java.time.Instant.now(), null, java.util.Map.of("description", "test-description", "owner", "test-owner")))
            )));

        // Add more mock setups as needed for other services
    }

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
