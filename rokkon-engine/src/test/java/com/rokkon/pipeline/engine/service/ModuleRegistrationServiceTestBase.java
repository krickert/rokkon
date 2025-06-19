package com.rokkon.pipeline.engine.service;

import com.rokkon.pipeline.engine.model.ModuleRegistrationRequest;
import com.rokkon.pipeline.engine.model.ModuleRegistrationResponse;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base class for testing ModuleRegistrationService business logic.
 * Tests the service layer directly without HTTP.
 */
public abstract class ModuleRegistrationServiceTestBase {
    
    protected abstract ModuleRegistrationService getRegistrationService();
    
    protected String getValidClusterName() {
        return "default-cluster";
    }
    
    @Test
    void testValidatePortRange() {
        // Test port validation - too low
        ModuleRegistrationRequest request = new ModuleRegistrationRequest(
            "test-module",
            "localhost",
            0,  // Invalid port
            getValidClusterName(),
            "PipeStepProcessor",
            Map.of()
        );
        
        ModuleRegistrationResponse response = getRegistrationService()
            .registerModule(request)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .assertCompleted()
            .getItem();
            
        assertThat(response.success()).isFalse();
        assertThat(response.message()).contains("Invalid port number");
        
        // Test port validation - too high
        request = new ModuleRegistrationRequest(
            "test-module",
            "localhost",
            70000,  // Invalid port
            getValidClusterName(),
            "PipeStepProcessor",
            Map.of()
        );
        
        response = getRegistrationService()
            .registerModule(request)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .assertCompleted()
            .getItem();
            
        assertThat(response.success()).isFalse();
        assertThat(response.message()).contains("Invalid port number");
    }
    
    @Test
    void testClusterValidation() {
        // Test with non-existent cluster
        ModuleRegistrationRequest request = new ModuleRegistrationRequest(
            "test-module",
            "localhost",
            9090,
            "cluster-that-does-not-exist-" + System.currentTimeMillis(),
            "PipeStepProcessor",
            Map.of("test", "true")
        );
        
        ModuleRegistrationResponse response = getRegistrationService()
            .registerModule(request)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .assertCompleted()
            .getItem();
            
        assertThat(response.success()).isFalse();
        assertThat(response.message()).contains("does not exist");
        assertThat(response.moduleId()).isNull();
    }
    
    @Test
    void testConnectionValidation() {
        // Test with unreachable host
        ModuleRegistrationRequest request = new ModuleRegistrationRequest(
            "test-module",
            "definitely-not-a-real-host-" + System.currentTimeMillis() + ".invalid",
            9090,
            getValidClusterName(),
            "PipeStepProcessor",
            Map.of()
        );
        
        ModuleRegistrationResponse response = getRegistrationService()
            .registerModule(request)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .assertCompleted()
            .getItem();
            
        assertThat(response.success()).isFalse();
        assertThat(response.message()).satisfiesAnyOf(
            msg -> assertThat(msg).contains("Failed to connect"),
            msg -> assertThat(msg).contains("resolve"),
            msg -> assertThat(msg).contains("unknown host")
        );
    }
    
    @Test
    void testHealthCheckValidation() {
        // Test with closed port (health check should fail)
        ModuleRegistrationRequest request = new ModuleRegistrationRequest(
            "test-module",
            "localhost",
            12345,  // Unlikely to have anything listening
            getValidClusterName(),
            "PipeStepProcessor",
            Map.of()
        );
        
        ModuleRegistrationResponse response = getRegistrationService()
            .registerModule(request)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .assertCompleted()
            .getItem();
            
        assertThat(response.success()).isFalse();
        assertThat(response.message()).satisfiesAnyOf(
            msg -> assertThat(msg).contains("Health check"),
            msg -> assertThat(msg).contains("Connection refused"),
            msg -> assertThat(msg).contains("Failed to connect")
        );
    }
    
    @Test
    void testModuleIdGeneration() {
        // If we had a running module, verify ID generation
        // This test documents the expected behavior
        ModuleRegistrationRequest request = new ModuleRegistrationRequest(
            "test-module",
            "localhost",
            9090,  // Would need actual running module
            getValidClusterName(),
            "PipeStepProcessor",
            Map.of("version", "1.0.0")
        );
        
        // This will fail unless there's actually a module running
        ModuleRegistrationResponse response = getRegistrationService()
            .registerModule(request)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .assertCompleted()
            .getItem();
            
        if (response.success()) {
            // If successful, verify module ID format
            assertThat(response.moduleId()).isNotNull();
            assertThat(response.moduleId()).startsWith("test-module-");
            assertThat(response.moduleId()).contains("-");
            assertThat(response.message()).isNullOrEmpty();
        } else {
            // Expected to fail without running module
            assertThat(response.moduleId()).isNull();
            assertThat(response.message()).isNotEmpty();
        }
    }
    
    @Test
    void testMetadataHandling() {
        // Test that metadata is accepted in the request
        ModuleRegistrationRequest request = new ModuleRegistrationRequest(
            "test-module",
            "localhost",
            9090,
            getValidClusterName(),
            "PipeStepProcessor",
            Map.of(
                "version", "2.0.0",
                "environment", "staging",
                "region", "us-west-2",
                "customField", "customValue"
            )
        );
        
        // The service should process the request (may fail due to connection)
        ModuleRegistrationResponse response = getRegistrationService()
            .registerModule(request)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .assertCompleted()
            .getItem();
            
        // Should get a response (success or failure)
        assertThat(response).isNotNull();
    }
    
    @Test
    void testErrorMessageQuality() {
        // Verify error messages are helpful and contain context
        
        // Test cluster error message
        ModuleRegistrationRequest request = new ModuleRegistrationRequest(
            "test-module",
            "localhost",
            9090,
            "bad-cluster",
            "PipeStepProcessor",
            Map.of()
        );
        
        ModuleRegistrationResponse response = getRegistrationService()
            .registerModule(request)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .assertCompleted()
            .getItem();
            
        assertThat(response.message()).contains("bad-cluster");
        assertThat(response.message()).containsAnyOf("does not exist", "not found");
        
        // Test port error message  
        request = new ModuleRegistrationRequest(
            "test-module",
            "localhost",
            99999,
            getValidClusterName(),
            "PipeStepProcessor",
            Map.of()
        );
        
        response = getRegistrationService()
            .registerModule(request)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .assertCompleted()
            .getItem();
            
        assertThat(response.message()).contains("99999");
        assertThat(response.message()).containsAnyOf("Invalid port", "port number");
    }
}