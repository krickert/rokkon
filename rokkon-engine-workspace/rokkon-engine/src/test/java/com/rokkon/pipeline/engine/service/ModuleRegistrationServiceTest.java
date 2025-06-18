package com.rokkon.pipeline.engine.service;

import com.rokkon.pipeline.consul.service.ClusterService;
import com.rokkon.pipeline.engine.model.ModuleRegistrationRequest;
import com.rokkon.pipeline.engine.model.ModuleRegistrationResponse;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@QuarkusTest
class ModuleRegistrationServiceTest {
    
    @Inject
    ModuleRegistrationService registrationService;
    
    @InjectMock
    ClusterService clusterService;
    
    private ModuleRegistrationRequest validRequest;
    
    @BeforeEach
    void setUp() {
        reset(clusterService);
        
        validRequest = new ModuleRegistrationRequest(
            "test-module",
            "localhost",
            9090,
            "test-cluster",
            "PipeStepProcessor",
            Map.of("version", "1.0.0", "type", "processor", "environment", "test")
        );
    }
    
    @Test
    void testRegisterModuleWithNonExistentCluster() {
        // Given: Cluster does not exist
        when(clusterService.clusterExists("test-cluster"))
            .thenReturn(Uni.createFrom().item(false));
        
        // When: Attempting to register module
        ModuleRegistrationResponse response = registrationService.registerModule(validRequest)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .assertCompleted()
            .getItem();
            
        // Then: Registration should fail
        assertThat(response.success()).isFalse();
        assertThat(response.message()).contains("Cluster 'test-cluster' does not exist");
        
        // Then: Should not proceed to other steps
        verify(clusterService).clusterExists("test-cluster");
        verifyNoMoreInteractions(clusterService);
    }
    
    @Test
    void testRegisterModuleWithClusterCheckError() {
        // Given: Cluster check fails
        when(clusterService.clusterExists(anyString()))
            .thenReturn(Uni.createFrom().failure(new RuntimeException("Connection refused")));
        
        // When: Attempting to register module
        ModuleRegistrationResponse response = registrationService.registerModule(validRequest)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .assertCompleted()
            .getItem();
            
        // Then: Should handle error gracefully
        assertThat(response.success()).isFalse();
        assertThat(response.message()).contains("Internal error");
        assertThat(response.message()).contains("Connection refused");
    }
    
    @Test
    void testRegisterModuleValidatesConnectionBeforeHealthCheck() {
        // Given: Cluster exists
        when(clusterService.clusterExists("test-cluster"))
            .thenReturn(Uni.createFrom().item(true));
        
        // When: Attempting to register module with invalid host/port
        ModuleRegistrationRequest invalidRequest = new ModuleRegistrationRequest(
            "test-module",
            "invalid-host",
            99999,  // Invalid port
            "test-cluster",
            "PipeStepProcessor",
            Map.of()
        );
        
        ModuleRegistrationResponse response = registrationService.registerModule(invalidRequest)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .assertCompleted()
            .getItem();
            
        // Then: Should fail on validation
        assertThat(response.success()).isFalse();
        assertThat(response.message()).contains("Invalid port number: 99999");
    }
}