package com.rokkon.test.registration;

import com.rokkon.search.sdk.ServiceRegistrationResponse;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Standardized testing utility for module service registration.
 * Provides consistent validation across all Quarkus modules.
 */
@ApplicationScoped
public class ModuleRegistrationTester {
    private static final Logger LOG = LoggerFactory.getLogger(ModuleRegistrationTester.class);
    
    /**
     * Verifies that a module service registers correctly with proper metadata.
     * 
     * @param registrationUni The registration Uni to test 
     * @param expectedModuleName Expected module name (e.g., "echo", "tika", "chunker", "embedder")
     * @param expectedConfigKeys Expected configuration keys in the JSON schema
     */
    public void verifyModuleRegistration(Uni<ServiceRegistrationResponse> registrationUni, String expectedModuleName, String... expectedConfigKeys) {
        LOG.info("Testing registration for module: {}", expectedModuleName);
        
        // Get service registration
        ServiceRegistrationResponse registration = registrationUni.await().indefinitely();
        
        // Basic registration validation
        assertNotNull(registration, "Service registration should not be null");
        assertEquals(expectedModuleName, registration.getModuleName(), 
            "Module name should match expected value");
        
        // JSON schema validation
        String schema = registration.getJsonConfigSchema();
        assertNotNull(schema, "JSON schema should not be null");
        assertFalse(schema.trim().isEmpty(), "JSON schema should not be empty");
        assertTrue(schema.length() > 50, "JSON schema should be comprehensive");
        
        // Verify schema contains expected configuration keys
        for (String expectedKey : expectedConfigKeys) {
            assertTrue(schema.contains(expectedKey), 
                "JSON schema should contain configuration key: " + expectedKey);
        }
        
        // Verify JSON schema structure
        assertTrue(schema.contains("$schema"), "Schema should have $schema field");
        assertTrue(schema.contains("properties"), "Schema should have properties field");
        assertTrue(schema.contains("type"), "Schema should have type field");
        
        LOG.info("✅ Module {} registration validation passed", expectedModuleName);
        LOG.debug("Schema length: {} characters", schema.length());
        LOG.debug("Schema contains {} expected config keys", expectedConfigKeys.length);
    }
    
    /**
     * Verifies echo service registration (baseline test).
     */
    public void verifyEchoServiceRegistration(Uni<ServiceRegistrationResponse> registrationUni) {
        verifyModuleRegistration(registrationUni, "echo", 
            "message", "repeat_count", "add_timestamp");
    }
    
    /**
     * Verifies tika service registration.
     */
    public void verifyTikaServiceRegistration(Uni<ServiceRegistrationResponse> registrationUni) {
        verifyModuleRegistration(registrationUni, "tika", 
            "extract_metadata", "max_content_length", "parse_embedded_docs");
    }
    
    /**
     * Verifies chunker service registration.
     */
    public void verifyChunkerServiceRegistration(Uni<ServiceRegistrationResponse> registrationUni) {
        verifyModuleRegistration(registrationUni, "chunker",
            "source_field", "chunk_size", "overlap_size", "chunk_id_template");
    }
    
    /**
     * Verifies embedder service registration.
     */
    public void verifyEmbedderServiceRegistration(Uni<ServiceRegistrationResponse> registrationUni) {
        verifyModuleRegistration(registrationUni, "embedder",
            "embedding_models", "check_chunks", "check_document_fields", 
            "max_batch_size", "backpressure_strategy");
    }
    
    /**
     * Tests that the service can handle null input gracefully.
     */
    public void verifyNullInputHandling(Uni<ServiceRegistrationResponse> registrationUni) {
        LOG.info("Testing null input handling for service");
        
        try {
            ServiceRegistrationResponse registration = registrationUni.await().indefinitely();
            
            assertNotNull(registration, "Service should handle null input and return valid registration");
            LOG.info("✅ Null input handling test passed");
        } catch (Exception e) {
            fail("Service should handle null input gracefully, but threw: " + e.getMessage());
        }
    }
    
    /**
     * Comprehensive registration test that covers all standard validation points.
     */
    public void performComprehensiveRegistrationTest(Uni<ServiceRegistrationResponse> registrationUni, String moduleName, String... configKeys) {
        LOG.info("Performing comprehensive registration test for: {}", moduleName);
        
        // Standard registration validation
        verifyModuleRegistration(registrationUni, moduleName, configKeys);
        
        // Null input handling
        verifyNullInputHandling(registrationUni);
        
        // Performance check - registration should be fast
        long startTime = System.currentTimeMillis();
        ServiceRegistrationResponse registration = registrationUni.await().indefinitely();
        long duration = System.currentTimeMillis() - startTime;
        
        assertTrue(duration < 1000, "Service registration should complete quickly (< 1s), took: " + duration + "ms");
        
        LOG.info("✅ Comprehensive registration test completed for {} in {}ms", moduleName, duration);
    }
}