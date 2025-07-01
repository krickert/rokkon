package com.rokkon.pipeline.consul.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokkon.pipeline.config.model.Cluster;
import com.rokkon.pipeline.config.model.ClusterMetadata;
import com.rokkon.pipeline.config.service.ClusterService;
import com.rokkon.pipeline.consul.service.ClusterServiceImpl;
import com.rokkon.pipeline.validation.ValidationResult;
import com.rokkon.pipeline.validation.ValidationResultFactory;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit test for ClusterResource using mocked dependencies.
 * 
 * This test extends ClusterResourceTestBase and provides mocked implementations
 * of the required dependencies through abstract getters.
 */
@QuarkusTest
public class ClusterResourceTest extends ClusterResourceTestBase {

    private ClusterService mockClusterService;
    private final String testNamespace = "test-namespace-" + UUID.randomUUID().toString().substring(0, 8);

    @BeforeEach
    void setup() {
        // Create mock ClusterService
        mockClusterService = Mockito.mock(ClusterService.class);

        // Configure mock behavior
        setupMockBehavior();
    }

    private void setupMockBehavior() {
        // Keep track of created clusters to simulate duplicate detection
        java.util.Set<String> createdClusters = new java.util.HashSet<>();

        // Mock createCluster
        when(mockClusterService.createCluster(anyString())).thenAnswer(invocation -> {
            String clusterName = invocation.getArgument(0);
            if (clusterName == null || clusterName.isEmpty()) {
                return Uni.createFrom().item(ValidationResultFactory.failure("Cluster name cannot be empty"));
            }

            // Check if cluster already exists
            if (createdClusters.contains(clusterName)) {
                return Uni.createFrom().item(
                    ValidationResultFactory.failure("Cluster '" + clusterName + "' already exists")
                );
            }

            // Add to created clusters
            createdClusters.add(clusterName);
            return Uni.createFrom().item(ValidationResultFactory.success());
        });

        // Mock getCluster
        when(mockClusterService.getCluster(anyString())).thenAnswer(invocation -> {
            String clusterName = invocation.getArgument(0);

            // Check if cluster exists in our set of created clusters
            if (!createdClusters.contains(clusterName) || clusterName.contains("non-existent")) {
                return Uni.createFrom().item(Optional.empty());
            }

            // Create a ClusterMetadata with status=active
            ClusterMetadata metadata = new ClusterMetadata(
                clusterName,
                java.time.Instant.now(),
                null,
                java.util.Map.of("status", "active")
            );

            return Uni.createFrom().item(Optional.of(metadata));
        });

        // Mock deleteCluster
        when(mockClusterService.deleteCluster(anyString())).thenAnswer(invocation -> {
            String clusterName = invocation.getArgument(0);
            // Remove from created clusters to simulate deletion
            createdClusters.remove(clusterName);
            return Uni.createFrom().item(ValidationResultFactory.success());
        });
    }

    @Override
    protected ClusterService getClusterService() {
        return mockClusterService;
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
        // The actual REST API tests are in the base class

        // Verify createCluster works
        ValidationResult result = mockClusterService.createCluster("test-cluster")
            .await().indefinitely();
        assert result.valid();

        // Verify getCluster works
        Optional<ClusterMetadata> cluster = mockClusterService.getCluster("test-cluster")
            .await().indefinitely();
        assert cluster.isPresent();
        assert cluster.get().name().equals("test-cluster");

        // Verify non-existent cluster returns empty
        Optional<ClusterMetadata> nonExistent = mockClusterService.getCluster("non-existent")
            .await().indefinitely();
        assert nonExistent.isEmpty();
    }
}
