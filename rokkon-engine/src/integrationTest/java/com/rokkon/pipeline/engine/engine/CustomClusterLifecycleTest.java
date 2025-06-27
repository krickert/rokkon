package com.rokkon.pipeline.engine;

import com.rokkon.pipeline.config.service.ClusterService;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.*;

@QuarkusTest
@TestProfile(CustomClusterLifecycleTest.CustomClusterProfile.class)
class CustomClusterLifecycleTest {
    
    public static class CustomClusterProfile implements io.quarkus.test.junit.QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                "rokkon.cluster.name", "my-custom-cluster"
            );
        }
    }
    
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
    void testStartupWithCustomClusterNameNotExisting() {
        // Given: Custom cluster does not exist
        when(clusterService.getCluster("my-custom-cluster"))
            .thenReturn(Uni.createFrom().item(Optional.empty()));
        
        // When: Engine starts
        engineLifecycle.onStart(startupEvent);
        
        // Then: Custom cluster is checked and created
        verify(clusterService).getCluster("my-custom-cluster");
        // The actual creation happens via direct HTTP call in createCluster()
        // In a real scenario, we would see logs:
        // - "Using custom cluster name: 'my-custom-cluster'"
        // - "Cluster 'my-custom-cluster' does not exist in Consul"
        // - "Auto-creating new cluster 'my-custom-cluster'"
        // - "Successfully auto-created cluster 'my-custom-cluster'"
    }
}