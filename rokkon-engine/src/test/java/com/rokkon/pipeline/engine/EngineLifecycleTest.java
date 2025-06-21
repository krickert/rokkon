package com.rokkon.pipeline.engine;

import com.rokkon.pipeline.consul.service.ClusterService;
import com.rokkon.pipeline.consul.service.ClusterMetadata;
import com.rokkon.pipeline.validation.ValidationResult;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@QuarkusTest
class EngineLifecycleTest {

    @Inject
    EngineLifecycle engineLifecycle;

    @InjectMock
    ClusterService clusterService;

    private StartupEvent startupEvent;

    @BeforeEach
    void setUp() {
        startupEvent = mock(StartupEvent.class);
        reset(clusterService);
    }

    @Test
    @Disabled("Test is outdated - EngineLifecycle no longer interacts with ClusterService")
    void testStartupWithExistingCluster() {
        // TODO: This test is testing functionality that no longer exists in EngineLifecycle
        // Consider refactoring to use mocks or update to match current implementation
        /*
        // Given: An existing cluster
        ClusterMetadata existingCluster = new ClusterMetadata(
            "default-cluster",
            Instant.now(),
            null,
            Map.of("status", "active", "version", "1.0")
        );

        when(clusterService.getCluster("default-cluster"))
            .thenReturn(Uni.createFrom().item(Optional.of(existingCluster)));

        // When: Engine starts
        engineLifecycle.onStart(startupEvent);

        // Then: Cluster is not created again
        verify(clusterService).getCluster("default-cluster");
        verify(clusterService, never()).createCluster(anyString());
        */
    }

    @Test
    @Disabled("Test is outdated - EngineLifecycle no longer interacts with ClusterService")
    void testStartupWithoutExistingCluster() {
        // TODO: This test is testing functionality that no longer exists in EngineLifecycle
        // Consider refactoring to use mocks or update to match current implementation
        /*
        // Given: No existing cluster
        when(clusterService.getCluster("default-cluster"))
            .thenReturn(Uni.createFrom().item(Optional.empty()));

        // When: Engine starts
        engineLifecycle.onStart(startupEvent);

        // Then: Cluster is created
        verify(clusterService).getCluster("default-cluster");
        // Note: Direct HTTP call in createCluster method won't be captured here
        // We would need to refactor to use ClusterService.createCluster for better testability
        */
    }

    @Test
    @Disabled("Test is outdated - EngineLifecycle no longer interacts with ClusterService")
    void testStartupWithConsulError() {
        // TODO: This test is testing functionality that no longer exists in EngineLifecycle
        // Consider refactoring to use mocks or update to match current implementation
        /*
        // Given: Consul is unavailable
        when(clusterService.getCluster("default-cluster"))
            .thenReturn(Uni.createFrom().failure(new RuntimeException("Connection refused")));

        // When: Engine starts
        engineLifecycle.onStart(startupEvent);

        // Then: Engine continues to start (graceful degradation)
        verify(clusterService).getCluster("default-cluster");
        // Engine should not crash, just log warning
        */
    }
}
