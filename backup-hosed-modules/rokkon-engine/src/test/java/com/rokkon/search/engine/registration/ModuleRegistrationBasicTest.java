package com.rokkon.search.engine.registration;

import com.rokkon.search.config.pipeline.service.SchemaValidationService;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Basic test for module registration service functionality
 */
@QuarkusTest  
class ModuleRegistrationBasicTest {
    
    @Inject
    SchemaValidationService schemaValidationService;
    
    @Test
    void testSchemaValidationService_ValidSchema() {
        // Test with a valid JSON Schema v7
        String validSchema = """
            {
                "$schema": "https://json-schema.org/draft-07/schema#",
                "type": "object",
                "properties": {
                    "language": {
                        "type": "string",
                        "enum": ["en", "es", "fr"],
                        "default": "en"
                    },
                    "maxLength": {
                        "type": "integer",
                        "minimum": 1,
                        "maximum": 10000,
                        "default": 1000
                    }
                },
                "additionalProperties": true
            }
            """;
        
        var result = schemaValidationService.validateJsonSchema(validSchema);
        
        assertTrue(result.valid(), "Valid schema should pass validation");
        assertTrue(result.errors().isEmpty(), "Valid schema should have no errors");
        
        System.out.println("✅ Valid JSON Schema v7 validation passed");
    }
    
    @Test 
    void testSchemaValidationService_InvalidSchema() {
        // Test with invalid JSON Schema
        String invalidSchema = """
            {
                "type": "object",
                "properties": {
                    "invalidProperty": {
                        "type": "unknownType"
                    }
                },
                "invalid": syntax here
            }
            """;
        
        var result = schemaValidationService.validateJsonSchema(invalidSchema);
        
        assertFalse(result.valid(), "Invalid schema should fail validation");
        assertFalse(result.errors().isEmpty(), "Invalid schema should have errors");
        
        System.out.println("✅ Invalid JSON Schema correctly rejected");
        System.out.println("Errors: " + result.errors());
    }
    
    @Test
    void testSchemaValidationService_NoSchema() {
        // Test that empty/null schema is handled gracefully
        String emptySchema = "";
        
        var result = schemaValidationService.validateJsonSchema(emptySchema);
        
        assertFalse(result.valid(), "Empty schema should fail validation");
        assertFalse(result.errors().isEmpty(), "Empty schema should have errors");
        
        System.out.println("✅ Empty schema correctly rejected");
    }
}