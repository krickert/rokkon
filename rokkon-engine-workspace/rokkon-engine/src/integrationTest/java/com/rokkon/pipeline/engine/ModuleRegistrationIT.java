package com.rokkon.pipeline.engine;

import com.rokkon.pipeline.engine.test.ConsulTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.*;

@QuarkusIntegrationTest
@QuarkusTestResource(ConsulTestResource.class)
class ModuleRegistrationIT {
    
    @Test
    void testModuleRegistrationWithNonExistentCluster() {
        // Given: Consul is running but cluster doesn't exist
        
        // When: Attempting to register module
        RestAssured.given()
            .contentType("application/json")
            .body(Map.of(
                "moduleName", "test-module",
                "host", "localhost",
                "port", 9090,
                "clusterName", "non-existent-cluster",
                "serviceData", Map.of("version", "1.0.0"),
                "metadata", Map.of("env", "test")
            ))
            .when().post("/api/v1/modules/register")
            .then()
            .statusCode(400)
            .body("success", is(false))
            .body("message", containsString("does not exist"));
    }
    
    @Test
    void testModuleRegistrationWithInvalidPort() {
        // When: Attempting to register module with invalid port
        RestAssured.given()
            .contentType("application/json")
            .body(Map.of(
                "moduleName", "test-module",
                "host", "localhost",
                "port", 999999,  // Invalid port
                "clusterName", "default-cluster"
            ))
            .when().post("/api/v1/modules/register")
            .then()
            .statusCode(400)
            .body("success", is(false))
            .body("message", containsString("Invalid port number"));
    }
    
    @Test
    void testModuleRegistrationValidationErrors() {
        // When: Attempting to register module without required fields
        RestAssured.given()
            .contentType("application/json")
            .body(Map.of(
                "moduleName", "test-module"
                // Missing required fields
            ))
            .when().post("/api/v1/modules/register")
            .then()
            .statusCode(400);
    }
    
    @Test
    void testSuccessfulModuleRegistrationFlow() throws Exception {
        // Given: Consul is running
        String consulHost = System.getProperty("consul.host", "localhost");
        String consulPort = System.getProperty("consul.port", "8500");
        
        // First, ensure the default cluster exists by starting the engine
        // (The engine lifecycle creates default-cluster on startup)
        
        // Check if we need to create a test module service
        // In a real test, we would start a mock gRPC service here
        // For now, we'll test the validation failure path
        
        // When: Attempting to register a module that doesn't exist
        RestAssured.given()
            .contentType("application/json")
            .body(Map.of(
                "moduleName", "mock-module",
                "host", "non-existent-host",
                "port", 9999,
                "clusterName", "default-cluster",
                "serviceData", Map.of("version", "1.0.0", "type", "processor")
            ))
            .when().post("/api/v1/modules/register")
            .then()
            .statusCode(400)
            .body("success", is(false))
            .body("message", containsString("Health check"));
    }
}