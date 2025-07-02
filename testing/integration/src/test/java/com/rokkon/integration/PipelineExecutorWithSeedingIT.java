package com.rokkon.integration;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Integration test with proper Consul seeding before engine startup
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PipelineExecutorWithSeedingIT {
    
    private static final String COMPOSE_FILE = "docker-compose-with-seeding.yml";
    private static final int ENGINE_HTTP_PORT = 38082;
    private static final int ENGINE_GRPC_PORT = 48082;
    private static final int CONSUL_PORT = 8500;
    
    private static String engineHost;
    private static Integer enginePort;
    private static String baseUri;
    
    @Container
    static ComposeContainer environment = new ComposeContainer(
            new File(PipelineExecutorWithSeedingIT.class.getClassLoader()
                    .getResource(COMPOSE_FILE).getFile()))
            .withExposedService("consul-server", CONSUL_PORT, 
                    Wait.forHealthcheck().withStartupTimeout(Duration.ofSeconds(60)))
            .withExposedService("pipeline-engine", ENGINE_HTTP_PORT,
                    Wait.forHealthcheck().withStartupTimeout(Duration.ofSeconds(180)));
    
    @BeforeAll
    static void setup() {
        // Get the actual mapped ports
        engineHost = environment.getServiceHost("pipeline-engine", ENGINE_HTTP_PORT);
        enginePort = environment.getServicePort("pipeline-engine", ENGINE_HTTP_PORT);
        baseUri = String.format("http://%s:%d", engineHost, enginePort);
        
        System.out.println("Engine URL: " + baseUri);
    }
    
    @Test
    @Order(1)
    void testHealthCheck() {
        given()
            .baseUri(baseUri)
        .when()
            .get("/q/health/live")
        .then()
            .statusCode(200)
            .body("status", equalTo("UP"));
    }
    
    @Test
    @Order(2)
    void testEngineInfo() {
        given()
            .baseUri(baseUri)
        .when()
            .get("/api/v1/engine/info")
        .then()
            .statusCode(200)
            .body("engineName", equalTo("test-engine"))
            .body("clusterName", equalTo("test-cluster"))
            .log().body();
    }
    
    @Test
    @Order(3)
    void testCreateCluster() {
        // Create a test cluster
        given()
            .baseUri(baseUri)
            .contentType(ContentType.JSON)
            .body("{}")  // Empty JSON body
        .when()
            .post("/api/v1/clusters/test-cluster")
        .then()
            .statusCode(anyOf(is(201), is(400))) // 400 if already exists
            .log().body();
    }
    
    @Test
    @Order(4)
    void testListClusters() {
        given()
            .baseUri(baseUri)
        .when()
            .get("/api/v1/clusters")
        .then()
            .statusCode(200)
            .body("$", hasItem("test-cluster"))
            .log().body();
    }
    
    @Test
    @Order(5)
    void testCreateSimplePipeline() {
        // Create a simple echo pipeline
        Map<String, Object> pipelineConfig = Map.of(
            "name", "simple-echo-pipeline",
            "description", "A simple pipeline that just echoes input",
            "steps", List.of(
                Map.of(
                    "name", "echo-step",
                    "type", "PIPELINE",
                    "processor", Map.of(
                        "module", "echo",
                        "service", "echo"
                    )
                )
            )
        );
        
        given()
            .baseUri(baseUri)
            .contentType(ContentType.JSON)
            .body(pipelineConfig)
        .when()
            .post("/api/v1/clusters/test-cluster/pipelines/simple-echo-pipeline")
        .then()
            .statusCode(anyOf(is(201), is(400)))
            .log().body();
    }
    
    @Test
    @Order(6)
    void testListPipelines() {
        given()
            .baseUri(baseUri)
        .when()
            .get("/api/v1/clusters/test-cluster/pipelines")
        .then()
            .statusCode(200)
            .body("size()", greaterThanOrEqualTo(1))
            .log().body();
    }
    
    @Test
    @Order(7)
    void testGetPipelineDefinition() {
        given()
            .baseUri(baseUri)
        .when()
            .get("/api/v1/pipelines/simple-echo-pipeline")
        .then()
            .statusCode(200)
            .body("name", equalTo("simple-echo-pipeline"))
            .body("steps.size()", equalTo(1))
            .log().body();
    }
    
    @Test
    @Order(8)
    void testValidatePipeline() {
        // Test pipeline validation endpoint
        Map<String, Object> pipelineConfig = Map.of(
            "name", "validation-test-pipeline",
            "description", "Pipeline for testing validation",
            "steps", List.of(
                Map.of(
                    "name", "test-step",
                    "type", "PIPELINE",
                    "processor", Map.of(
                        "module", "echo",
                        "service", "echo"
                    )
                )
            )
        );
        
        given()
            .baseUri(baseUri)
            .contentType(ContentType.JSON)
            .body(pipelineConfig)
        .when()
            .post("/api/v1/pipelines/validate")
        .then()
            .statusCode(200)
            .body("valid", is(true))
            .log().body();
    }
    
    @Test
    @Order(9)
    @Disabled("Requires echo and test modules to be running")
    void testExecutePipeline() {
        // Create test data
        Map<String, Object> testData = Map.of(
            "id", "test-doc-1",
            "content", "This is a test document for pipeline processing",
            "metadata", Map.of(
                "source", "integration-test",
                "timestamp", System.currentTimeMillis()
            )
        );
        
        given()
            .baseUri(baseUri)
            .contentType(ContentType.JSON)
            .body(testData)
        .when()
            .post("/api/v1/pipelines/simple-echo-pipeline/execute")
        .then()
            .statusCode(anyOf(is(200), is(202), is(503))) // 503 if modules not available
            .log().body();
    }
}