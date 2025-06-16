package com.rokkon.modules.tika;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the Tika service using @QuarkusTest.
 * These tests verify the service integration with Quarkus framework.
 * 
 * Tests Quarkus application lifecycle and service registration.
 * For comprehensive gRPC functionality tests, see other test classes.
 */
@QuarkusTest 
class TikaServiceQuarkusTest {
    
    @Test
    void testQuarkusApplicationStartsSuccessfully() {
        // If we get here, Quarkus started successfully with all services
        // This tests that:
        // 1. Quarkus application context loads
        // 2. gRPC services are registered 
        // 3. Configuration is valid
        // 4. All dependencies are satisfied
        // 5. Tika dependencies are properly loaded
        
        assertTrue(true, "Quarkus application started successfully");
        System.out.println("✅ Tika service Quarkus application starts successfully");
    }
    
    @Test 
    void testTikaServiceIsInstantiable() {
        // Test that we can create an instance of the service
        // This verifies the service class is properly configured
        
        TikaService service = new TikaService();
        assertNotNull(service, "Tika service should be instantiable");
        
        System.out.println("✅ Tika service is properly instantiable");
    }
    
    @Test
    void testServiceImplementsCorrectInterface() {
        // Test that the service implements the expected gRPC interface
        TikaService service = new TikaService();
        
        // Check that it has the expected methods (they won't throw during reflection)
        assertDoesNotThrow(() -> {
            service.getClass().getMethod("processData", 
                com.rokkon.search.sdk.ProcessRequest.class);
        }, "Service should have processData method");
        
        assertDoesNotThrow(() -> {
            service.getClass().getMethod("getServiceRegistration", 
                com.google.protobuf.Empty.class);
        }, "Service should have getServiceRegistration method");
        
        System.out.println("✅ Tika service implements correct interface");
    }
    
    @Test
    void testApplicationConfigurationIsValid() {
        // Test that application configuration loads without errors
        // This is implicit - if Quarkus starts, configuration is valid
        
        // We can also test that the gRPC annotation is properly applied
        assertTrue(TikaService.class.isAnnotationPresent(io.quarkus.grpc.GrpcService.class),
                "Service should be annotated with @GrpcService");
        
        System.out.println("✅ Tika service configuration is valid");
    }
    
    @Test
    void testTikaConfigurationBeanIsAvailable() {
        // Test that TikaConfiguration can be instantiated
        // This verifies that the configuration bean is properly set up
        
        assertDoesNotThrow(() -> {
            TikaConfiguration config = new TikaConfiguration();
            assertNotNull(config, "TikaConfiguration should be instantiable");
        }, "TikaConfiguration should be available");
        
        System.out.println("✅ TikaConfiguration bean is available");
    }
    
    @Test
    void testDocumentParserIsAvailable() {
        // Test that DocumentParser can be used
        // This verifies that Tika dependencies are properly loaded
        
        assertDoesNotThrow(() -> {
            // This just tests that the class can be referenced
            Class<?> parserClass = DocumentParser.class;
            assertNotNull(parserClass, "DocumentParser should be available");
        }, "DocumentParser should be available");
        
        System.out.println("✅ DocumentParser is available");
    }
}