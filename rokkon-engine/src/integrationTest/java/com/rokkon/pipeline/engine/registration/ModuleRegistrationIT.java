package com.rokkon.pipeline.engine.registration;

import io.quarkus.test.junit.QuarkusIntegrationTest;

/**
 * Integration test for module registration using packaged JAR.
 * This verifies registration works in production mode.
 */
@QuarkusIntegrationTest
public class ModuleRegistrationIT extends ModuleRegistrationTestBase {
    
    @Override
    protected String getBaseUrl() {
        // In integration tests, Quarkus runs on port 8081 by default
        return "http://localhost:8081";
    }
    
    @Override
    protected String getWorkingModuleHost() {
        return "localhost";
    }
    
    @Override
    protected int getWorkingModulePort() {
        // In integration tests, we typically don't have a module running
        // This will fail validation (expected)
        return 9090;
    }
    
    @Override
    protected String getHealthCheckHost() {
        return "localhost";
    }
    
    @Override
    protected int getHealthCheckPort() {
        // Health checks will fail without a real module
        return 9090;
    }
    
    @Override
    protected String getUnreachableHost() {
        return "integration-test-invalid-host-" + System.currentTimeMillis() + ".test";
    }
    
    @Override
    protected int getClosedPort() {
        // High port unlikely to be in use
        return 45678;
    }
    
    @Override
    protected int getNonGrpcPort() {
        // Use the HTTP port (not gRPC)
        return 8081;
    }
}