package com.rokkon.engine.grpc;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test to verify CDI wiring of Dynamic gRPC components in the engine.
 * 
 * This test uses @QuarkusIntegrationTest which:
 * - Packages and runs the application as a JAR (production mode)
 * - Tests the full CDI wiring across modules
 * - Ensures that engine:dynamic-grpc beans are properly discovered by engine:pipestream
 * 
 * According to TESTING_STRATEGY.md:
 * - Integration tests verify component interactions with real dependencies
 * - Use @QuarkusIntegrationTest for production-like testing
 * - Tests should be in src/integrationTest/java and end with IT
 */
@QuarkusIntegrationTest
@QuarkusTestResource(ConsulTestResource.class)
public class DynamicGrpcCdiIT {
    
    /**
     * Test that the engine health endpoint is accessible.
     * This confirms the application started successfully with all CDI dependencies resolved.
     */
    @Test
    void testEngineHealthEndpoint() {
        // The application must have started successfully for this to work
        RestAssured.when()
            .get("/q/health")
            .then()
            .statusCode(200);
    }
    
    /**
     * Test that we can access the engine's pipeline API.
     * This endpoint depends on various services being properly wired.
     */
    @Test
    void testPipelineApiAccessible() {
        RestAssured.when()
            .get("/api/v1/pipelines")
            .then()
            .statusCode(200);
    }
    
    /**
     * Test module registration endpoint which uses DynamicGrpcClientFactory.
     * This verifies the CDI wiring is working for the dynamic gRPC components.
     */
    @Test
    void testModuleRegistrationEndpoint() {
        // Try to get module registry - this uses services that depend on dynamic gRPC
        RestAssured.when()
            .get("/api/v1/modules")
            .then()
            .statusCode(200);
    }
    
    /**
     * Test that Consul configuration is properly injected.
     * The engine should be configured to use the test container's Consul instance.
     */
    @Test
    void testConsulConfiguration() {
        // The ready endpoint should indicate Consul connectivity
        RestAssured.when()
            .get("/q/health/ready")
            .then()
            .statusCode(200);
    }
}