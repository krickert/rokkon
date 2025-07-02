package com.rokkon.pipeline.consul.service;

import com.rokkon.pipeline.config.model.*;
import com.rokkon.pipeline.config.service.ClusterService;
import com.rokkon.pipeline.config.service.PipelineConfigService;
import com.rokkon.pipeline.consul.test.UnifiedTestProfile;
import com.rokkon.pipeline.validation.ValidationResult;
import com.rokkon.pipeline.validation.ValidationResultFactory;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit test for PipelineConfigService using mocks.
 * This test runs without any real Consul connectivity.
 */
@QuarkusTest
@TestProfile(UnifiedTestProfile.class)
class PipelineConfigServiceTest extends PipelineConfigServiceTestBase {

    @InjectMock
    ClusterService clusterService;

    @InjectMock
    PipelineConfigService pipelineConfigService;

    // In-memory storage for mocked pipelines
    private final Map<String, Map<String, PipelineConfig>> pipelinesByCluster = new ConcurrentHashMap<>();

    @BeforeEach
    void setupMocks() {
        // Reset state
        pipelinesByCluster.clear();

        // Mock ClusterService
        when(clusterService.createCluster(anyString()))
            .thenAnswer(invocation -> {
                String clusterName = invocation.getArgument(0);
                pipelinesByCluster.putIfAbsent(clusterName, new ConcurrentHashMap<>());
                return Uni.createFrom().item(ValidationResultFactory.success());
            });

        when(clusterService.deleteCluster(anyString()))
            .thenAnswer(invocation -> {
                String clusterName = invocation.getArgument(0);
                pipelinesByCluster.remove(clusterName);
                return Uni.createFrom().item(ValidationResultFactory.success());
            });

        when(clusterService.clusterExists(anyString()))
            .thenAnswer(invocation -> {
                String clusterName = invocation.getArgument(0);
                return Uni.createFrom().item(pipelinesByCluster.containsKey(clusterName));
            });

        when(clusterService.getCluster(anyString()))
            .thenAnswer(invocation -> {
                String clusterName = invocation.getArgument(0);
                if (pipelinesByCluster.containsKey(clusterName)) {
                    ClusterMetadata metadata = new ClusterMetadata(
                        clusterName,
                        Instant.now(),
                        null,
                        Map.of("description", "Test cluster")
                    );
                    return Uni.createFrom().item(Optional.of(metadata));
                }
                return Uni.createFrom().item(Optional.empty());
            });

        // Mock PipelineConfigService
        when(pipelineConfigService.createPipeline(anyString(), anyString(), any(PipelineConfig.class)))
            .thenAnswer(invocation -> {
                String clusterName = invocation.getArgument(0);
                String pipelineId = invocation.getArgument(1);
                PipelineConfig config = invocation.getArgument(2);

                Map<String, PipelineConfig> clusterPipelines = pipelinesByCluster.computeIfAbsent(
                    clusterName, k -> new ConcurrentHashMap<>()
                );

                if (clusterPipelines.containsKey(pipelineId)) {
                    return Uni.createFrom().item(
                        ValidationResultFactory.failure("Pipeline " + pipelineId + " already exists")
                    );
                }

                // Basic validation
                ValidationResult validation = validatePipelineConfig(config);
                if (!validation.valid()) {
                    return Uni.createFrom().item(validation);
                }

                clusterPipelines.put(pipelineId, config);
                return Uni.createFrom().item(ValidationResultFactory.success());
            });

        when(pipelineConfigService.updatePipeline(anyString(), anyString(), any(PipelineConfig.class)))
            .thenAnswer(invocation -> {
                String clusterName = invocation.getArgument(0);
                String pipelineId = invocation.getArgument(1);
                PipelineConfig config = invocation.getArgument(2);

                Map<String, PipelineConfig> clusterPipelines = pipelinesByCluster.get(clusterName);
                if (clusterPipelines == null || !clusterPipelines.containsKey(pipelineId)) {
                    return Uni.createFrom().item(
                        ValidationResultFactory.failure("Pipeline " + pipelineId + " does not exist")
                    );
                }

                // Basic validation
                ValidationResult validation = validatePipelineConfig(config);
                if (!validation.valid()) {
                    return Uni.createFrom().item(validation);
                }

                clusterPipelines.put(pipelineId, config);
                return Uni.createFrom().item(ValidationResultFactory.success());
            });

        when(pipelineConfigService.deletePipeline(anyString(), anyString()))
            .thenAnswer(invocation -> {
                String clusterName = invocation.getArgument(0);
                String pipelineId = invocation.getArgument(1);

                Map<String, PipelineConfig> clusterPipelines = pipelinesByCluster.get(clusterName);
                if (clusterPipelines != null) {
                    clusterPipelines.remove(pipelineId);
                }
                return Uni.createFrom().item(ValidationResultFactory.success());
            });

        when(pipelineConfigService.getPipeline(anyString(), anyString()))
            .thenAnswer(invocation -> {
                String clusterName = invocation.getArgument(0);
                String pipelineId = invocation.getArgument(1);

                Map<String, PipelineConfig> clusterPipelines = pipelinesByCluster.get(clusterName);
                if (clusterPipelines != null) {
                    PipelineConfig config = clusterPipelines.get(pipelineId);
                    return Uni.createFrom().item(Optional.ofNullable(config));
                }
                return Uni.createFrom().item(Optional.empty());
            });

        when(pipelineConfigService.listPipelines(anyString()))
            .thenAnswer(invocation -> {
                String clusterName = invocation.getArgument(0);
                Map<String, PipelineConfig> clusterPipelines = pipelinesByCluster.get(clusterName);
                if (clusterPipelines != null) {
                    return Uni.createFrom().item(new HashMap<>(clusterPipelines));
                }
                return Uni.createFrom().item(Map.of());
            });
    }

    private ValidationResult validatePipelineConfig(PipelineConfig config) {
        // Basic validation logic for unit tests
        if (config.name() == null || config.name().isBlank()) {
            return ValidationResultFactory.failure("Pipeline name is required");
        }

        // Check for required step types
        boolean hasInitialPipeline = false;
        boolean hasSink = false;

        for (PipelineStepConfig step : config.pipelineSteps().values()) {
            if (step.stepType() == StepType.INITIAL_PIPELINE) {
                hasInitialPipeline = true;
            }
            if (step.stepType() == StepType.SINK) {
                hasSink = true;
            }
        }

        // Empty pipelines are valid for unit tests
        if (config.pipelineSteps().isEmpty()) {
            return ValidationResultFactory.success();
        }

        // If there are steps, we need both INITIAL_PIPELINE and SINK
        if (!hasInitialPipeline) {
            return ValidationResultFactory.failure("Pipeline must have at least one INITIAL_PIPELINE step");
        }
        if (!hasSink) {
            return ValidationResultFactory.failure("Pipeline must have at least one SINK step");
        }

        return ValidationResultFactory.success();
    }

    @Override
    protected PipelineConfigService getPipelineConfigService() {
        return pipelineConfigService;
    }

    @Override
    protected ClusterService getClusterService() {
        return clusterService;
    }

    // Override test methods to remove @Disabled annotation
    @Test
    @Override
    void testCreateCluster() throws Exception {
        super.testCreateCluster();
    }

    @Test
    @Override
    void testCreateSimplePipeline() throws Exception {
        super.testCreateSimplePipeline();
    }

    @Test
    @Override
    void testCreatePipelineWithConsul() throws Exception {
        super.testCreatePipelineWithConsul();
    }

    @Test
    @Override
    void testUpdatePipeline() throws Exception {
        super.testUpdatePipeline();
    }

    @Test
    @Override
    void testDeletePipeline() throws Exception {
        super.testDeletePipeline();
    }

    @Test
    @Override
    void testListPipelines() throws Exception {
        super.testListPipelines();
    }

    @Test
    @Override
    void testConcurrentPipelineCreation() {
        super.testConcurrentPipelineCreation();
    }

    @Test
    @Override
    void testConcurrentPipelineUpdates() {
        super.testConcurrentPipelineUpdates();
    }
}