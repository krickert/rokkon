package com.rokkon.pipeline.consul.api;

import com.rokkon.pipeline.config.model.PipelineConfig;
import com.rokkon.pipeline.config.service.PipelineConfigService;
import com.rokkon.pipeline.validation.ValidationResult;
import com.rokkon.pipeline.validation.ValidationResultFactory;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit test for PipelineConfigResource using mocked dependencies.
 * 
 * This test extends PipelineConfigResourceTestBase and provides mocked implementations
 * of the required dependencies through abstract getters.
 */
@QuarkusTest
public class PipelineConfigResourceTest extends PipelineConfigResourceTestBase {

    private PipelineConfigService mockPipelineConfigService;
    private final String testNamespace = "test-namespace-" + UUID.randomUUID().toString().substring(0, 8);
    
    // Keep track of created pipelines to simulate duplicate detection
    private final Map<String, PipelineConfig> pipelines = new HashMap<>();

    @BeforeEach
    void setup() {
        // Create mock PipelineConfigService
        mockPipelineConfigService = Mockito.mock(PipelineConfigService.class);

        // Configure mock behavior
        setupMockBehavior();
    }

    private void setupMockBehavior() {
        // Mock createPipeline
        when(mockPipelineConfigService.createPipeline(anyString(), anyString(), any())).thenAnswer(invocation -> {
            String clusterName = invocation.getArgument(0);
            String pipelineId = invocation.getArgument(1);
            PipelineConfig config = invocation.getArgument(2);

            // Validate inputs
            if (clusterName == null || clusterName.isEmpty()) {
                return Uni.createFrom().item(ValidationResultFactory.failure("Cluster name cannot be empty"));
            }
            if (pipelineId == null || pipelineId.isEmpty()) {
                return Uni.createFrom().item(ValidationResultFactory.failure("Pipeline ID cannot be empty"));
            }
            if (config == null) {
                return Uni.createFrom().item(ValidationResultFactory.failure("Pipeline config cannot be null"));
            }

            // Check if pipeline already exists
            String key = clusterName + "/" + pipelineId;
            if (pipelines.containsKey(key)) {
                return Uni.createFrom().item(
                    ValidationResultFactory.failure("Pipeline '" + pipelineId + "' already exists in cluster '" + clusterName + "'")
                );
            }

            // Store the pipeline
            pipelines.put(key, config);
            return Uni.createFrom().item(ValidationResultFactory.success());
        });

        // Mock updatePipeline
        when(mockPipelineConfigService.updatePipeline(anyString(), anyString(), any())).thenAnswer(invocation -> {
            String clusterName = invocation.getArgument(0);
            String pipelineId = invocation.getArgument(1);
            PipelineConfig config = invocation.getArgument(2);

            // Validate inputs
            if (clusterName == null || clusterName.isEmpty()) {
                return Uni.createFrom().item(ValidationResultFactory.failure("Cluster name cannot be empty"));
            }
            if (pipelineId == null || pipelineId.isEmpty()) {
                return Uni.createFrom().item(ValidationResultFactory.failure("Pipeline ID cannot be empty"));
            }
            if (config == null) {
                return Uni.createFrom().item(ValidationResultFactory.failure("Pipeline config cannot be null"));
            }

            // Check if pipeline exists
            String key = clusterName + "/" + pipelineId;
            if (!pipelines.containsKey(key)) {
                return Uni.createFrom().item(
                    ValidationResultFactory.failure("Pipeline '" + pipelineId + "' not found in cluster '" + clusterName + "'")
                );
            }

            // Update the pipeline
            pipelines.put(key, config);
            return Uni.createFrom().item(ValidationResultFactory.success());
        });

        // Mock deletePipeline
        when(mockPipelineConfigService.deletePipeline(anyString(), anyString())).thenAnswer(invocation -> {
            String clusterName = invocation.getArgument(0);
            String pipelineId = invocation.getArgument(1);

            // Validate inputs
            if (clusterName == null || clusterName.isEmpty()) {
                return Uni.createFrom().item(ValidationResultFactory.failure("Cluster name cannot be empty"));
            }
            if (pipelineId == null || pipelineId.isEmpty()) {
                return Uni.createFrom().item(ValidationResultFactory.failure("Pipeline ID cannot be empty"));
            }

            // Check if pipeline exists
            String key = clusterName + "/" + pipelineId;
            if (!pipelines.containsKey(key)) {
                return Uni.createFrom().item(
                    ValidationResultFactory.failure("Pipeline '" + pipelineId + "' not found in cluster '" + clusterName + "'")
                );
            }

            // Delete the pipeline
            pipelines.remove(key);
            return Uni.createFrom().item(ValidationResultFactory.success());
        });

        // Mock getPipeline
        when(mockPipelineConfigService.getPipeline(anyString(), anyString())).thenAnswer(invocation -> {
            String clusterName = invocation.getArgument(0);
            String pipelineId = invocation.getArgument(1);

            // Validate inputs
            if (clusterName == null || clusterName.isEmpty() || pipelineId == null || pipelineId.isEmpty()) {
                return Uni.createFrom().item(Optional.empty());
            }

            // Check if pipeline exists
            String key = clusterName + "/" + pipelineId;
            if (!pipelines.containsKey(key)) {
                return Uni.createFrom().item(Optional.empty());
            }

            // Return the pipeline
            return Uni.createFrom().item(Optional.of(pipelines.get(key)));
        });

        // Mock listPipelines
        when(mockPipelineConfigService.listPipelines(anyString())).thenAnswer(invocation -> {
            String clusterName = invocation.getArgument(0);

            // Validate inputs
            if (clusterName == null || clusterName.isEmpty()) {
                return Uni.createFrom().item(Map.of());
            }

            // Filter pipelines by cluster
            Map<String, PipelineConfig> result = new HashMap<>();
            for (Map.Entry<String, PipelineConfig> entry : pipelines.entrySet()) {
                String key = entry.getKey();
                if (key.startsWith(clusterName + "/")) {
                    String pipelineId = key.substring(clusterName.length() + 1);
                    result.put(pipelineId, entry.getValue());
                }
            }

            return Uni.createFrom().item(result);
        });
    }

    @Override
    protected PipelineConfigService getPipelineConfigService() {
        return mockPipelineConfigService;
    }

    @Override
    protected int getServerPort() {
        return 8081; // Default test port
    }

    @Override
    protected String getTestNamespace() {
        return testNamespace;
    }

    // Additional unit tests specific to this class

    @Test
    void testMockBehaviorWorks() {
        // This test verifies that our mocks are working correctly
        // The actual tests are in the base class

        // Verify createPipeline works
        PipelineConfig config = createTestPipelineConfig();
        ValidationResult result = mockPipelineConfigService.createPipeline("test-cluster", "test-pipeline", config)
            .await().indefinitely();
        assert result.valid();

        // Verify getPipeline works
        Optional<PipelineConfig> pipeline = mockPipelineConfigService.getPipeline("test-cluster", "test-pipeline")
            .await().indefinitely();
        assert pipeline.isPresent();
        assert pipeline.get().name().equals("test-pipeline");

        // Verify non-existent pipeline returns empty
        Optional<PipelineConfig> nonExistent = mockPipelineConfigService.getPipeline("test-cluster", "non-existent")
            .await().indefinitely();
        assert nonExistent.isEmpty();
    }
}