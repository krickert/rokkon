package com.rokkon.pipeline.consul;

import com.rokkon.pipeline.config.model.Cluster;
import com.rokkon.pipeline.config.model.ClusterMetadata;
import com.rokkon.pipeline.config.model.PipelineConfig;
import com.rokkon.pipeline.config.model.PipelineStepConfig;
// ProcessorInfo is an inner class of PipelineStepConfig
import com.rokkon.pipeline.config.model.StepType;
import com.rokkon.pipeline.validation.ValidationResult;
import com.rokkon.pipeline.validation.ValidationResultFactory;
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
import java.util.Map;
import java.util.Optional;

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
    
    // Track state for mocking
    private boolean pipelineHasStep = false;

    @BeforeEach
    void setupMocks() {
        // Reset mocks before each test
        Mockito.reset(clusterService, moduleWhitelistService, pipelineConfigService, testSeedingService);
        
        // Reset state
        pipelineHasStep = false;

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
            .thenAnswer(invocation -> {
                pipelineHasStep = true;
                return Uni.createFrom().item(true);
            });

        when(testSeedingService.teardownAll())
            .thenReturn(Uni.createFrom().item(true));

        // Mock ClusterService
        when(clusterService.createCluster(anyString()))
            .thenReturn(Uni.createFrom().item(ValidationResultFactory.success()));

        when(clusterService.listClusters())
            .thenReturn(Uni.createFrom().item(List.of(
                new Cluster(DEFAULT_CLUSTER, "Default cluster", new ClusterMetadata(DEFAULT_CLUSTER, java.time.Instant.now(), null, java.util.Map.of("description", "default-description", "owner", "default-owner"))),
                new Cluster(TEST_CLUSTER, "Test cluster", new ClusterMetadata(TEST_CLUSTER, java.time.Instant.now(), null, java.util.Map.of("description", "test-description", "owner", "test-owner")))
            )));

        // Mock PipelineConfigService - return pipeline based on current state
        when(pipelineConfigService.getPipeline(anyString(), anyString()))
            .thenAnswer(invocation -> {
                if (pipelineHasStep) {
                    // After step 5, return pipeline with a step
                    PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
                        TEST_MODULE,  // grpcServiceName
                        null          // internalProcessorBeanName
                    );
                    
                    PipelineStepConfig step = new PipelineStepConfig(
                        "test-step-1",              // stepName
                        StepType.PIPELINE,          // stepType
                        "Test step",                // description
                        null,                       // customConfigSchemaId
                        null,                       // customConfig
                        null,                       // kafkaInputs
                        null,                       // outputs
                        null,                       // maxRetries
                        null,                       // retryBackoffMs
                        null,                       // maxRetryBackoffMs
                        null,                       // retryBackoffMultiplier
                        null,                       // stepTimeoutMs
                        processorInfo               // processorInfo
                    );
                    
                    return Uni.createFrom().item(Optional.of(
                        new PipelineConfig("test-pipeline", Map.of("test-step-1", step))
                    ));
                } else {
                    // Before step 5, return empty pipeline
                    return Uni.createFrom().item(Optional.of(
                        new PipelineConfig("test-pipeline", Map.of())
                    ));
                }
            });

        when(pipelineConfigService.createPipeline(anyString(), anyString(), Mockito.any(PipelineConfig.class)))
            .thenReturn(Uni.createFrom().item(ValidationResultFactory.success()));

        when(pipelineConfigService.updatePipeline(anyString(), anyString(), Mockito.any(PipelineConfig.class)))
            .thenReturn(Uni.createFrom().item(ValidationResultFactory.success()));

        // Mock ModuleWhitelistService
        when(moduleWhitelistService.listWhitelistedModules(anyString()))
            .thenReturn(Uni.createFrom().item(List.of()));
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
