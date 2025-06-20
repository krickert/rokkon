package com.rokkon.pipeline.engine.service;

import com.rokkon.pipeline.consul.service.ClusterService;
import com.rokkon.pipeline.engine.model.ModuleRegistrationRequest;
import com.rokkon.pipeline.engine.model.ModuleRegistrationResponse;
import com.rokkon.pipeline.consul.test.ConsulTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real integration test for ModuleRegistrationService.
 * No mocks - testing real validation and business logic.
 */
@QuarkusTest
@QuarkusTestResource(ConsulTestResource.class)
class ModuleRegistrationServiceTest extends ModuleRegistrationServiceTestBase {
    
    @Inject
    ModuleRegistrationService registrationService;
    
    @Inject
    ClusterService clusterService;
    
    @Override
    protected ModuleRegistrationService getRegistrationService() {
        return registrationService;
    }
    
    @BeforeEach
    void setUp() {
        // Note: Real cluster is auto-created by engine startup
        // We'll test validation using the default cluster
    }
    
    @Test
    void testRegisterModuleWithNonExistentCluster() {
        // Test with a cluster that doesn't exist
        ModuleRegistrationRequest request = new ModuleRegistrationRequest(
            "test-module",
            "localhost",
            9090,
            "non-existent-cluster",  // This cluster doesn't exist
            "PipeStepProcessor",
            Map.of("version", "1.0.0", "type", "processor", "environment", "test")
        );
        
        // When: Attempting to register module
        ModuleRegistrationResponse response = registrationService.registerModule(request)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .assertCompleted()
            .getItem();
            
        // Then: Registration should fail with cluster validation error
        assertThat(response.success()).isFalse();
        assertThat(response.message()).contains("does not exist");
        assertThat(response.moduleId()).isNull();
    }
    
    @Test
    void testRegisterModuleWithValidClusterButUnreachableService() {
        // Test with valid cluster but unreachable service
        ModuleRegistrationRequest request = new ModuleRegistrationRequest(
            "test-module",
            "localhost",
            9999,  // Nothing listening on this port
            "default-cluster",  // This cluster exists
            "PipeStepProcessor",
            Map.of("version", "1.0.0", "type", "processor")
        );
        
        // When: Attempting to register module
        ModuleRegistrationResponse response = registrationService.registerModule(request)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .assertCompleted()
            .getItem();
            
        // Then: Should fail on health check
        assertThat(response.success()).isFalse();
        assertThat(response.message()).satisfiesAnyOf(
            msg -> assertThat(msg).containsIgnoringCase("health check"),
            msg -> assertThat(msg).containsIgnoringCase("connection refused"),
            msg -> assertThat(msg).containsIgnoringCase("cannot connect")
        );
        assertThat(response.moduleId()).isNull();
    }
    
    @Test
    void testRegisterModuleValidatesPortRange() {
        // Test with invalid port (too high)
        ModuleRegistrationRequest request = new ModuleRegistrationRequest(
            "test-module", 
            "localhost",
            99999,  // Invalid port number
            "default-cluster",
            "PipeStepProcessor",
            Map.of()
        );
        
        // When: Attempting to register module
        ModuleRegistrationResponse response = registrationService.registerModule(request)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .assertCompleted()
            .getItem();
            
        // Then: Should fail validation
        assertThat(response.success()).isFalse();
        assertThat(response.message()).containsIgnoringCase("Invalid port number");
    }
    
    @Test 
    void testRegisterModuleValidatesHostFormat() {
        // Test with invalid host format  
        ModuleRegistrationRequest request = new ModuleRegistrationRequest(
            "test-module",
            "invalid-host-that-does-not-exist",
            9090,
            "default-cluster",
            "PipeStepProcessor", 
            Map.of()
        );
        
        // When: Attempting to register module
        ModuleRegistrationResponse response = registrationService.registerModule(request)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .assertCompleted()
            .getItem();
            
        // Then: Should fail on connection/DNS resolution
        assertThat(response.success()).isFalse();
        assertThat(response.message()).satisfiesAnyOf(
            msg -> assertThat(msg).containsIgnoringCase("host"),
            msg -> assertThat(msg).containsIgnoringCase("connection"),
            msg -> assertThat(msg).containsIgnoringCase("resolve")
        );
    }
}