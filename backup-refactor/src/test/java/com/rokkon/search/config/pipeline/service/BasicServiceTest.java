package com.rokkon.search.config.pipeline.service;

import com.rokkon.search.config.pipeline.model.*;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Basic smoke test to verify the service is working.
 */
@QuarkusTest
public class BasicServiceTest {
    
    @Inject
    ConfigValidationService validationService;
    
    @Test
    void testServiceInjection() {
        assertNotNull(validationService, "ConfigValidationService should be injected");
        System.out.println("✅ Service injection working");
    }
    
    @Test
    void testBasicValidation() {
        // Test service name validation (should work without complex objects)
        var result = validationService.validateServiceName("valid-service");
        assertTrue(result.valid(), "Valid service name should pass");
        
        var invalid = validationService.validateServiceName("Invalid!");
        assertFalse(invalid.valid(), "Invalid service name should fail");
        
        System.out.println("✅ Basic validation working");
    }
    
    @Test
    void testModelCreation() {
        // Test that we can create model objects without issues
        assertDoesNotThrow(() -> {
            SchemaReference schema = new SchemaReference("test-schema", 1);
            assertEquals("test-schema", schema.subject());
            assertEquals(1, schema.version());
            
            TransportType transport = TransportType.GRPC;
            assertEquals("GRPC", transport.name());
            
            StepType stepType = StepType.PIPELINE;
            assertEquals("PIPELINE", stepType.name());
            
            System.out.println("✅ Model creation working");
        });
    }
}