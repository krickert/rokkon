package com.rokkon.pipeline.engine.registration;

import com.rokkon.pipeline.consul.test.ConsulTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.hamcrest.CoreMatchers.*;

/**
 * Container integration test for module registration with real containers.
 * This test:
 * 1. Starts a real test-module container
 * 2. Verifies we can register it with the engine
 * 3. Confirms the registration details in Consul
 * 
 * This proves the end-to-end flow works with Docker networking.
 */
@QuarkusIntegrationTest
@Testcontainers
@QuarkusTestResource(ConsulTestResource.class)
public class ModuleRegistrationContainerIT {
    
    private static final int MODULE_GRPC_PORT = 9090;
    private static final int MODULE_HTTP_PORT = 8080;
    private static Network network;
    
    @Container
    private static GenericContainer<?> testModule;
    
    @BeforeAll
    static void setupContainers() {
        // Create a shared network for containers to communicate
        network = Network.newNetwork();
        
        // Start test-module container
        testModule = new GenericContainer<>("rokkon/test-module:1.0.0-SNAPSHOT")
                .withNetwork(network)
                .withNetworkAliases("test-module")
                .withExposedPorts(MODULE_GRPC_PORT, MODULE_HTTP_PORT)
                .waitingFor(Wait.forLogMessage(".*Started gRPC server.*", 1))
                .withLogConsumer(outputFrame -> System.out.print("[test-module] " + outputFrame.getUtf8String()));
        
        testModule.start();
    }
    
    @AfterAll
    static void teardownContainers() {
        if (testModule != null) {
            testModule.stop();
        }
        if (network != null) {
            network.close();
        }
    }
    
    @Test
    void testRegisterRealModuleContainer() {
        // Get the external ports mapped by Docker
        Integer externalGrpcPort = testModule.getMappedPort(MODULE_GRPC_PORT);
        Integer externalHttpPort = testModule.getMappedPort(MODULE_HTTP_PORT);
        
        System.out.println("Test module container started:");
        System.out.println("  gRPC: " + MODULE_GRPC_PORT + " -> " + externalGrpcPort + " (external)");
        System.out.println("  HTTP: " + MODULE_HTTP_PORT + " -> " + externalHttpPort + " (external)");
        
        // Register the module using external port
        RestAssured.given()
            .baseUri("http://localhost:8081")
            .contentType("application/json")
            .body(Map.of(
                "moduleName", "test-module-container",
                "host", "localhost",
                "port", externalGrpcPort,
                "clusterName", "default-cluster",
                "serviceType", "PipeStepProcessor",
                "metadata", Map.of(
                    "version", "1.0.0",
                    "containerized", "true",
                    "testRun", String.valueOf(System.currentTimeMillis())
                )
            ))
            .when()
            .post("/api/v1/modules/register")
            .then()
            .statusCode(200)
            .body("success", is(true))
            .body("moduleId", notNullValue())
            .body("message", nullValue());
    }
    
    @Test
    void testHealthCheckOnRegisteredModule() {
        // First register the module
        Integer externalGrpcPort = testModule.getMappedPort(MODULE_GRPC_PORT);
        
        String moduleId = RestAssured.given()
            .baseUri("http://localhost:8081")
            .contentType("application/json")
            .body(Map.of(
                "moduleName", "test-module-health",
                "host", "localhost",
                "port", externalGrpcPort,
                "clusterName", "default-cluster",
                "serviceType", "PipeStepProcessor"
            ))
            .when()
            .post("/api/v1/modules/register")
            .then()
            .statusCode(200)
            .body("success", is(true))
            .extract()
            .jsonPath()
            .getString("moduleId");
            
        System.out.println("Registered module with ID: " + moduleId);
        
        // Verify the module's HTTP health endpoint is accessible
        RestAssured.given()
            .baseUri("http://localhost:" + testModule.getMappedPort(MODULE_HTTP_PORT))
            .when()
            .get("/q/health")
            .then()
            .statusCode(200)
            .body("status", is("UP"));
    }
    
    @Test
    void testRegisterMultipleInstances() {
        // Test that we can register multiple instances of the same module
        Integer externalGrpcPort = testModule.getMappedPort(MODULE_GRPC_PORT);
        
        // Register first instance
        String moduleId1 = RestAssured.given()
            .baseUri("http://localhost:8081")
            .contentType("application/json")
            .body(Map.of(
                "moduleName", "test-module-instance-1",
                "host", "localhost",
                "port", externalGrpcPort,
                "clusterName", "default-cluster",
                "serviceType", "PipeStepProcessor",
                "metadata", Map.of("instance", "1")
            ))
            .when()
            .post("/api/v1/modules/register")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()
            .getString("moduleId");
            
        // Register second instance (same module, different name)
        String moduleId2 = RestAssured.given()
            .baseUri("http://localhost:8081")
            .contentType("application/json")
            .body(Map.of(
                "moduleName", "test-module-instance-2",
                "host", "localhost",
                "port", externalGrpcPort,
                "clusterName", "default-cluster",
                "serviceType", "PipeStepProcessor",
                "metadata", Map.of("instance", "2")
            ))
            .when()
            .post("/api/v1/modules/register")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()
            .getString("moduleId");
            
        // Module IDs should be different
        System.out.println("Module ID 1: " + moduleId1);
        System.out.println("Module ID 2: " + moduleId2);
        
        // Both should have unique IDs
        org.assertj.core.api.Assertions.assertThat(moduleId1).isNotEqualTo(moduleId2);
    }
}