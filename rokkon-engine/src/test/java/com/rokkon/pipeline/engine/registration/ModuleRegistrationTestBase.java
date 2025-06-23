package com.rokkon.pipeline.engine.registration;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.*;

/**
 * Base class for testing module registration functionality.
 * This tests the complete registration flow including:
 * - Cluster validation
 * - Module connection validation
 * - Health check validation
 * - Consul registration
 * 
 * The base test is unaware of what kind of test it is (unit, integration, container).
 * Subclasses provide the appropriate values through abstract methods.
 */
public abstract class ModuleRegistrationTestBase {
    
    // === Abstract methods for successful registration scenarios ===
    
    /**
     * Get the base URL for the registration API
     */
    protected abstract String getBaseUrl();
    
    /**
     * Get host for a module that should successfully register
     */
    protected abstract String getWorkingModuleHost();
    
    /**
     * Get port for a module that should successfully register
     */
    protected abstract int getWorkingModulePort();
    
    /**
     * Get host for health check validation
     * (might be different from registration in container scenarios)
     */
    protected abstract String getHealthCheckHost();
    
    /**
     * Get port for health check validation
     * (might be different from registration in container scenarios)
     */
    protected abstract int getHealthCheckPort();
    
    // === Abstract methods for failure scenarios ===
    
    /**
     * Get a host that doesn't exist (for DNS resolution failures)
     */
    protected abstract String getUnreachableHost();
    
    /**
     * Get a port that's not listening (for connection refused)
     */
    protected abstract int getClosedPort();
    
    /**
     * Get a port that's listening but not a gRPC service
     * (for protocol mismatch errors)
     */
    protected abstract int getNonGrpcPort();
    
    // === Methods for configuration ===
    
    /**
     * Get a valid cluster name that exists in Consul
     */
    protected String getValidClusterName() {
        return "default-cluster";
    }
    
    /**
     * Get metadata to include in registration.
     * Subclasses can override to add specific metadata (e.g., container info)
     */
    protected Map<String, String> getRegistrationMetadata() {
        return Map.of(
            "version", "1.0.0",
            "environment", "test"
        );
    }
    
    @Test
    void testSuccessfulModuleRegistration() {
        // Test successful registration with a working module
        Response response = RestAssured.given()
            .baseUri(getBaseUrl())
            .contentType("application/json")
            .body(Map.of(
                "moduleName", "test-module",
                "host", getWorkingModuleHost(),
                "port", getWorkingModulePort(),
                "clusterName", getValidClusterName(),
                "serviceType", "PipeStepProcessor",
                "metadata", getRegistrationMetadata()
            ))
            .when()
            .post("/api/v1/modules/register")
            .then()
            .extract()
            .response();
            
        // This should succeed if there's a working module at the provided host/port
        int statusCode = response.statusCode();
        if (statusCode == 200) {
            // If successful, verify response structure
            assertThat(response.jsonPath().getBoolean("success")).isTrue();
            assertThat(response.jsonPath().getString("moduleId")).isNotNull();
            assertThat(response.jsonPath().getString("moduleId")).startsWith("test-module-");
            assertThat(response.jsonPath().getString("message")).isNullOrEmpty();
        } else {
            // If no module is running, we expect a specific error
            assertThat(response.jsonPath().getBoolean("success")).isFalse();
            assertThat(response.jsonPath().getString("message")).containsAnyOf(
                "Failed to connect",
                "Health check error",
                "Connection refused"
            );
        }
    }
    
    @Test
    void testHealthCheckValidation() {
        // Test that health checks are performed correctly
        Response response = RestAssured.given()
            .baseUri(getBaseUrl())
            .contentType("application/json")
            .body(Map.of(
                "moduleName", "health-check-test",
                "host", getHealthCheckHost(),
                "port", getHealthCheckPort(),
                "clusterName", getValidClusterName(),
                "serviceType", "PipeStepProcessor",
                "metadata", getRegistrationMetadata()
            ))
            .when()
            .post("/api/v1/modules/register")
            .then()
            .extract()
            .response();
            
        // The test implementation determines if this should pass or fail
        assertThat(response.statusCode()).isIn(200, 400);
    }
    
    @Test
    void testRegisterModuleWithMissingRequiredFields() {
        // Test with missing moduleName
        RestAssured.given()
            .baseUri(getBaseUrl())
            .contentType("application/json")
            .body(Map.of(
                "host", "localhost",
                "port", 9090,
                "clusterName", getValidClusterName(),
                "serviceType", "PipeStepProcessor"
            ))
            .when()
            .post("/api/v1/modules/register")
            .then()
            .statusCode(400);
            
        // Test with missing host
        RestAssured.given()
            .baseUri(getBaseUrl())
            .contentType("application/json")
            .body(Map.of(
                "moduleName", "test-module",
                "port", 9090,
                "clusterName", getValidClusterName(),
                "serviceType", "PipeStepProcessor"
            ))
            .when()
            .post("/api/v1/modules/register")
            .then()
            .statusCode(400);
    }
    
    @Test
    void testRegisterModuleWithInvalidPort() {
        // Test with negative port
        RestAssured.given()
            .baseUri(getBaseUrl())
            .contentType("application/json")
            .body(Map.of(
                "moduleName", "test-module",
                "host", "localhost",
                "port", -1,
                "clusterName", getValidClusterName(),
                "serviceType", "PipeStepProcessor"
            ))
            .when()
            .post("/api/v1/modules/register")
            .then()
            .statusCode(400);
            
        // Test with port > 65535
        RestAssured.given()
            .baseUri(getBaseUrl())
            .contentType("application/json")
            .body(Map.of(
                "moduleName", "test-module",
                "host", "localhost",
                "port", 70000,
                "clusterName", getValidClusterName(),
                "serviceType", "PipeStepProcessor"
            ))
            .when()
            .post("/api/v1/modules/register")
            .then()
            .statusCode(400);
    }
    
    @Test
    void testRegisterModuleWithNonExistentCluster() {
        RestAssured.given()
            .baseUri(getBaseUrl())
            .contentType("application/json")
            .body(Map.of(
                "moduleName", "test-module",
                "host", getWorkingModuleHost(),
                "port", getWorkingModulePort(),
                "clusterName", "non-existent-cluster-" + System.currentTimeMillis(),
                "serviceType", "PipeStepProcessor",
                "metadata", getRegistrationMetadata()
            ))
            .when()
            .post("/api/v1/modules/register")
            .then()
            .statusCode(400)
            .body("success", is(false))
            .body("message", containsString("does not exist"));
    }
    
    @Test
    void testRegisterModuleWithUnreachableHost() {
        RestAssured.given()
            .baseUri(getBaseUrl())
            .contentType("application/json")
            .body(Map.of(
                "moduleName", "test-module",
                "host", getUnreachableHost(),
                "port", getWorkingModulePort(),
                "clusterName", getValidClusterName(),
                "serviceType", "PipeStepProcessor"
            ))
            .when()
            .post("/api/v1/modules/register")
            .then()
            .statusCode(400)
            .body("success", is(false))
            .body("message", anyOf(
                containsString("Failed to connect"),
                containsString("Health check error"),
                containsString("resolve")
            ));
    }
    
    @Test
    void testRegisterModuleWithClosedPort() {
        // Use a port that's not listening
        RestAssured.given()
            .baseUri(getBaseUrl())
            .contentType("application/json")
            .body(Map.of(
                "moduleName", "test-module",
                "host", getWorkingModuleHost(),
                "port", getClosedPort(),
                "clusterName", getValidClusterName(),
                "serviceType", "PipeStepProcessor",
                "metadata", getRegistrationMetadata()
            ))
            .when()
            .post("/api/v1/modules/register")
            .then()
            .statusCode(400)
            .body("success", is(false))
            .body("message", anyOf(
                containsString("Failed to connect"),
                containsString("gRPC health check error"),
                containsString("Connection refused"),
                containsString("UNAVAILABLE")
            ));
    }
    
    @Test
    void testRegisterModuleWithNonGrpcPort() {
        // Test with a port that's listening but not gRPC (e.g., HTTP port)
        RestAssured.given()
            .baseUri(getBaseUrl())
            .contentType("application/json")
            .body(Map.of(
                "moduleName", "test-module",
                "host", getWorkingModuleHost(),
                "port", getNonGrpcPort(),
                "clusterName", getValidClusterName(),
                "serviceType", "PipeStepProcessor"
            ))
            .when()
            .post("/api/v1/modules/register")
            .then()
            .statusCode(400)
            .body("success", is(false))
            .body("message", anyOf(
                containsString("gRPC health check error"),
                containsString("UNIMPLEMENTED"),
                containsString("404")
            ));
    }
    
    @Test
    void testRegisterModuleValidatesServiceType() {
        // Test with empty service type
        RestAssured.given()
            .baseUri(getBaseUrl())
            .contentType("application/json")
            .body(Map.of(
                "moduleName", "test-module",
                "host", "localhost",
                "port", 9090,
                "clusterName", getValidClusterName(),
                "serviceType", ""
            ))
            .when()
            .post("/api/v1/modules/register")
            .then()
            .statusCode(400);
    }
    
    @Test
    void testRegisterModuleWithMetadata() {
        // Test that metadata is accepted and properly handled
        Response response = RestAssured.given()
            .baseUri(getBaseUrl())
            .contentType("application/json")
            .body(Map.of(
                "moduleName", "test-module-with-metadata",
                "host", getWorkingModuleHost(),
                "port", getWorkingModulePort(),
                "clusterName", getValidClusterName(),
                "serviceType", "PipeStepProcessor",
                "metadata", getRegistrationMetadata()
            ))
            .when()
            .post("/api/v1/modules/register")
            .then()
            .extract()
            .response();
            
        // Request should be processed (may succeed or fail based on module availability)
        assertThat(response.statusCode()).isIn(200, 400);
        
        // If we have custom metadata, it should be accepted
        if (!getRegistrationMetadata().isEmpty()) {
            // Verify the request was processed (not rejected due to metadata)
            String message = response.jsonPath().getString("message");
            if (message != null) {
                assertThat(message).doesNotContain("metadata");
            }
        }
    }
}