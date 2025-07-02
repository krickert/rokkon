package com.rokkon.integration;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end test for Dynamic gRPC functionality.
 * 
 * This test:
 * 1. Uses the DevModuleResource to deploy test modules
 * 2. Creates a pipeline using PipelineDefinitionResource
 * 3. Sends data through the pipeline using dynamic gRPC routing
 * 
 * This tests the full integration of:
 * - Module deployment and registration
 * - Pipeline configuration
 * - Dynamic service discovery via Consul
 * - gRPC routing through the engine
 */
@QuarkusTest
@QuarkusTestResource(ConsulTestResource.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DynamicGrpcEndToEndTest {
    
    @BeforeAll
    static void setup() {
        RestAssured.baseURI = "http://localhost:8080";
    }
    
    @Test
    @Order(1)
    void testEngineHealth() {
        given()
            .when()
            .get("/q/health")
            .then()
            .statusCode(200)
            .body("status", equalTo("UP"));
    }
    
    @Test
    @Order(2)
    void testDeployEchoModule() {
        // Deploy the echo module using DevModuleResource
        given()
            .contentType(ContentType.JSON)
            .body("{\"buildImage\": false}")
            .when()
            .post("/api/v1/dev/modules/echo/deploy")
            .then()
            .statusCode(200)
            .body("status", equalTo("deployed"))
            .body("module", equalTo("echo"))
            .body("grpcPort", equalTo(49091));
        
        // Wait for module to be healthy
        await().atMost(10, java.util.concurrent.TimeUnit.SECONDS)
            .until(() -> {
                var response = given()
                    .when()
                    .get("/api/v1/dev/modules")
                    .then()
                    .extract()
                    .response();
                
                var modules = response.jsonPath().getMap("$");
                return modules.containsKey("echo") && 
                       ((Map<?, ?>)modules.get("echo")).get("status").equals("healthy");
            });
    }
    
    @Test
    @Order(3)
    void testCreatePipelineDefinition() {
        // Create a simple pipeline that uses the echo module
        String pipelineDefinition = """
            {
                "name": "test-echo-pipeline",
                "description": "Test pipeline for dynamic gRPC",
                "metadata": {
                    "version": "1.0",
                    "author": "test"
                },
                "steps": [
                    {
                        "name": "echo-step",
                        "module": "echo",
                        "config": {
                            "prefix": "Echoed: "
                        }
                    }
                ]
            }
            """;
        
        given()
            .contentType(ContentType.JSON)
            .body(pipelineDefinition)
            .when()
            .post("/api/v1/pipelines/definitions")
            .then()
            .statusCode(201)
            .body("success", equalTo(true))
            .body("message", containsString("Pipeline definition created successfully"));
    }
    
    @Test
    @Order(4) 
    void testDeployPipelineToCluster() {
        // First, ensure we have a default cluster
        given()
            .contentType(ContentType.JSON)
            .body("{\"name\": \"default\", \"description\": \"Default test cluster\"}")
            .when()
            .post("/api/v1/clusters")
            .then()
            .statusCode(anyOf(is(201), is(409))); // Created or already exists
        
        // Deploy the pipeline to the cluster
        given()
            .contentType(ContentType.JSON)
            .body("{}")
            .when()
            .post("/api/v1/clusters/default/pipelines/test-echo-pipeline")
            .then()
            .statusCode(201)
            .body("success", equalTo(true));
    }
    
    @Test
    @Order(5)
    void testProcessDataThroughPipeline() {
        // Send data through the pipeline
        String testData = """
            {
                "content": "Hello, Dynamic gRPC!",
                "metadata": {
                    "source": "integration-test",
                    "timestamp": "%s"
                }
            }
            """.formatted(java.time.Instant.now().toString());
        
        // Process data through the pipeline
        given()
            .contentType(ContentType.JSON)
            .body(testData)
            .when()
            .post("/api/v1/clusters/default/pipelines/test-echo-pipeline/process")
            .then()
            .statusCode(200)
            .body("success", equalTo(true))
            .body("result", containsString("Echoed: Hello, Dynamic gRPC!"));
    }
    
    @Test
    @Order(6)
    void testModuleDiscovery() {
        // Test the module discovery endpoint
        given()
            .when()
            .get("/api/v1/modules/discover")
            .then()
            .statusCode(200)
            .body("modules", hasItem(
                allOf(
                    hasEntry("name", "echo"),
                    hasEntry("grpcPort", 49091),
                    hasEntry("status", "healthy")
                )
            ));
    }
    
    @Test
    @Order(7)
    void testMultiStepPipeline() {
        // Deploy another module (if available)
        given()
            .contentType(ContentType.JSON)
            .body("{\"buildImage\": false}")
            .when()
            .post("/api/v1/dev/modules/chunker/deploy")
            .then()
            .statusCode(anyOf(is(200), is(409)));
        
        // Create a multi-step pipeline
        String multiStepPipeline = """
            {
                "name": "multi-step-pipeline",
                "description": "Pipeline with multiple steps",
                "steps": [
                    {
                        "name": "chunk-step",
                        "module": "chunker",
                        "config": {
                            "chunkSize": 100
                        }
                    },
                    {
                        "name": "echo-step",
                        "module": "echo",
                        "config": {
                            "prefix": "Processed: "
                        }
                    }
                ]
            }
            """;
        
        given()
            .contentType(ContentType.JSON)
            .body(multiStepPipeline)
            .when()
            .post("/api/v1/pipelines/definitions")
            .then()
            .statusCode(201);
        
        // Deploy and test the multi-step pipeline
        given()
            .contentType(ContentType.JSON)
            .body("{}")
            .when()
            .post("/api/v1/clusters/default/pipelines/multi-step-pipeline")
            .then()
            .statusCode(201);
    }
    
    @AfterAll
    static void cleanup() {
        // Clean up deployed modules
        given()
            .when()
            .delete("/api/v1/dev/modules/echo")
            .then()
            .statusCode(anyOf(is(200), is(404)));
        
        given()
            .when()
            .delete("/api/v1/dev/modules/chunker")
            .then()
            .statusCode(anyOf(is(200), is(404)));
    }
}