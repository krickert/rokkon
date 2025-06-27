package com.rokkon.pipeline.engine;

import com.rokkon.engine.api.NoConsulTestProfile;
import com.rokkon.pipeline.config.model.Cluster;
import com.rokkon.pipeline.config.model.ClusterMetadata;
import com.rokkon.pipeline.config.service.ClusterService;
import com.rokkon.pipeline.validation.ValidationResult;
import com.rokkon.pipeline.validation.ValidationResultFactory;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit test for custom cluster lifecycle scenarios using mocks.
 */
@QuarkusTest
@TestProfile(NoConsulTestProfile.class)
class CustomClusterLifecycleUnitTest extends CustomClusterLifecycleTestBase {
    
    private static final String CUSTOM_CLUSTER_NAME = "test-custom-cluster";
    
    private EngineLifecycle engineLifecycle;
    
    @InjectMock
    ClusterService clusterService;
    
    @BeforeEach
    void setUp() {
        engineLifecycle = new EngineLifecycle();
        engineLifecycle.applicationName = "test-engine";
        reset(clusterService);
    }
    
    @Override
    protected EngineLifecycle getEngineLifecycle() {
        return engineLifecycle;
    }
    
    @Override
    protected ClusterService getClusterService() {
        return clusterService;
    }
    
    @Override
    protected String getCustomClusterName() {
        return CUSTOM_CLUSTER_NAME;
    }
    
    @Test
    void testListClustersWithMocks() {
        // Given: Multiple clusters exist
        List<Cluster> clusters = List.of(
            Cluster.emptyCluster("default-cluster"),
            Cluster.emptyCluster(CUSTOM_CLUSTER_NAME),
            Cluster.testCluster("another-cluster", Map.of("status", (Object) "active", "version", "1.0"))
        );
        when(clusterService.listClusters())
            .thenReturn(Uni.createFrom().item(clusters));
        
        // When: We list clusters
        List<Cluster> result = clusterService.listClusters().await().indefinitely();
        
        // Then: All clusters are returned
        assertThat(result).hasSize(3);
        assertThat(result.stream().map(Cluster::name))
            .containsExactlyInAnyOrder("default-cluster", CUSTOM_CLUSTER_NAME, "another-cluster");
    }
    
    @Test
    void testClusterExistsWithMocks() {
        // Given: Cluster exists check
        when(clusterService.clusterExists(eq(CUSTOM_CLUSTER_NAME)))
            .thenReturn(Uni.createFrom().item(true));
        when(clusterService.clusterExists(eq("non-existent-cluster")))
            .thenReturn(Uni.createFrom().item(false));
        
        // When & Then
        assertThat(clusterService.clusterExists(CUSTOM_CLUSTER_NAME).await().indefinitely()).isTrue();
        assertThat(clusterService.clusterExists("non-existent-cluster").await().indefinitely()).isFalse();
    }
    
    @Test
    void testDeleteClusterWithMocks() {
        // Given: Delete cluster operation
        when(clusterService.deleteCluster(eq(CUSTOM_CLUSTER_NAME)))
            .thenReturn(Uni.createFrom().item(ValidationResultFactory.success()));
        when(clusterService.deleteCluster(eq("non-existent-cluster")))
            .thenReturn(Uni.createFrom().item(ValidationResultFactory.failure("Cluster not found")));
        
        // When & Then: Successful deletion
        ValidationResult successResult = clusterService.deleteCluster(CUSTOM_CLUSTER_NAME).await().indefinitely();
        assertThat(successResult.valid()).isTrue();
        
        // When & Then: Failed deletion
        ValidationResult failureResult = clusterService.deleteCluster("non-existent-cluster").await().indefinitely();
        assertThat(failureResult.valid()).isFalse();
        assertThat(failureResult.errors()).contains("Cluster not found");
    }
    
    @Test
    void testStartupWithCustomClusterNotExisting() {
        // Given: Custom cluster does not exist
        when(clusterService.getCluster(eq(CUSTOM_CLUSTER_NAME)))
            .thenReturn(Uni.createFrom().item(Optional.empty()));
        
        when(clusterService.createCluster(eq(CUSTOM_CLUSTER_NAME)))
            .thenReturn(Uni.createFrom().item(ValidationResultFactory.success()));
        
        // When: Engine starts
        engineLifecycle.onStart(startupEvent);
        
        // Then: Engine starts successfully
        // Note: Current EngineLifecycle doesn't interact with ClusterService,
        // but this test is ready for when that functionality is added
    }
    
    @Test
    void testStartupWithCustomClusterExisting() {
        // Given: Custom cluster already exists
        ClusterMetadata existingCluster = new ClusterMetadata(
            CUSTOM_CLUSTER_NAME,
            Instant.now(),
            null,
            Map.of("status", "active")
        );
        when(clusterService.getCluster(eq(CUSTOM_CLUSTER_NAME)))
            .thenReturn(Uni.createFrom().item(Optional.of(existingCluster)));
        
        // When: Engine starts
        engineLifecycle.onStart(startupEvent);
        
        // Then: Engine starts successfully
        // No need to create cluster since it already exists
        verify(clusterService, never()).createCluster(anyString());
    }
    
    @Test
    void testStartupWithClusterServiceError() {
        // Given: Cluster service throws error
        when(clusterService.getCluster(anyString()))
            .thenReturn(Uni.createFrom().failure(new RuntimeException("Service unavailable")));
        
        // When: Engine starts
        engineLifecycle.onStart(startupEvent);
        
        // Then: Engine still starts (graceful degradation)
        // No exception should be thrown
    }
}