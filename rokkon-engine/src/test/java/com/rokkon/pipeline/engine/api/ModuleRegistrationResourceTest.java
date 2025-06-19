package com.rokkon.pipeline.engine.api;

import com.rokkon.pipeline.consul.test.ConsulTestResource;
import com.rokkon.pipeline.consul.service.ClusterService;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real integration test using actual Consul and services.
 * No mocks - testing real functionality and validation.
 */
@QuarkusTest
@QuarkusTestResource(ConsulTestResource.class)
class ModuleRegistrationResourceTest {
    
    @Inject
    ClusterService clusterService;
    
    @BeforeEach 
    void setUp() {
        // Note: Real cluster is auto-created by engine startup
        // We'll test validation without relying on async cluster creation
    }
    
    @Test
    void testRegisterModuleWithMissingServiceType() {
        // Test validation failure - missing required serviceType field
        given()
            .contentType("application/json")
            .body(Map.of(
                "moduleName", "test-module",
                "host", "localhost",
                "port", 9090,
                "clusterName", "default-cluster"  // Use auto-created cluster
                // Missing serviceType - should fail validation
            ))
            .when().post("/api/v1/modules/register")
            .then()
            .statusCode(400); // Bean validation will reject this
    }
    
    @Test
    void testRegisterModuleWithInvalidPort() {
        // Test validation failure - invalid port
        given()
            .contentType("application/json")
            .body(Map.of(
                "moduleName", "test-module",
                "host", "localhost",
                "port", -1,  // Invalid port
                "clusterName", "default-cluster",
                "serviceType", "PipeStepProcessor"
            ))
            .when().post("/api/v1/modules/register")
            .then()
            .statusCode(400); // Bean validation will reject negative port
    }
    
    @Test
    void testRegisterModuleWithNonExistentCluster() {
        // Test business logic validation - cluster doesn't exist
        given()
            .contentType("application/json")
            .body(Map.of(
                "moduleName", "test-module",
                "host", "localhost",
                "port", 9090,
                "clusterName", "non-existent-cluster",  // This cluster doesn't exist
                "serviceType", "PipeStepProcessor",
                "metadata", Map.of("version", "1.0.0")
            ))
            .when().post("/api/v1/modules/register")
            .then()
            .statusCode(400)
            .body("success", is(false))
            .body("message", containsString("does not exist"));
    }
    
    @Test
    void testRegisterModuleHealthCheckFailure() {
        // Test with valid cluster but unreachable service (health check will fail)
        given()
            .contentType("application/json")
            .body(Map.of(
                "moduleName", "test-module",
                "host", "localhost", 
                "port", 9999,  // Nothing listening on this port
                "clusterName", "default-cluster",  // This cluster exists
                "serviceType", "PipeStepProcessor",
                "metadata", Map.of("version", "1.0.0", "env", "test")
            ))
            .when().post("/api/v1/modules/register")
            .then()
            .statusCode(400)  // Health check will fail
            .body("success", is(false))
            .body("message", containsString("Health check error"));
    }
}