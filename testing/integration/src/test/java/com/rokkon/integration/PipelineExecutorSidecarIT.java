package com.rokkon.integration;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
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
 * Integration test for pipeline execution with Consul sidecars
 * This matches our production deployment pattern
 */
@Testcontainers
public class PipelineExecutorSidecarIT {
    
    private static final String COMPOSE_FILE = "docker-compose-sidecar.yml";
    private static final int ENGINE_HTTP_PORT = 38082;
    private static final int ENGINE_GRPC_PORT = 48082;
    private static final int CONSUL_PORT = 8500;
    
    @Container
    static ComposeContainer environment = new ComposeContainer(
            new File(PipelineExecutorSidecarIT.class.getClassLoader()
                    .getResource(COMPOSE_FILE).getFile()))
            .withExposedService("consul-server", CONSUL_PORT, 
                    Wait.forHealthcheck().withStartupTimeout(Duration.ofSeconds(60)))
            .withExposedService("pipeline-engine", ENGINE_HTTP_PORT,
                    Wait.forHealthcheck().withStartupTimeout(Duration.ofSeconds(120)))
            .withExposedService("echo-module", 39091,
                    Wait.forHealthcheck().withStartupTimeout(Duration.ofSeconds(120)))
            .withExposedService("test-module", 39092,
                    Wait.forHealthcheck().withStartupTimeout(Duration.ofSeconds(120)));
    
    @Test
    void testEngineStartsWithSidecar() {
        // Get the actual mapped ports
        String engineHost = environment.getServiceHost("pipeline-engine", ENGINE_HTTP_PORT);
        Integer enginePort = environment.getServicePort("pipeline-engine", ENGINE_HTTP_PORT);
        
        // Test health endpoint
        given()
            .baseUri(String.format("http://%s:%d", engineHost, enginePort))
        .when()
            .get("/q/health/live")
        .then()
            .statusCode(200)
            .body("status", equalTo("UP"));
    }
    
    @Test
    void testCreateClusterAndPipeline() {
        // Get the actual mapped ports
        String engineHost = environment.getServiceHost("pipeline-engine", ENGINE_HTTP_PORT);
        Integer enginePort = environment.getServicePort("pipeline-engine", ENGINE_HTTP_PORT);
        
        String baseUri = String.format("http://%s:%d", engineHost, enginePort);
        
        // First, create a cluster
        given()
            .baseUri(baseUri)
        .when()
            .post("/api/v1/clusters/test-cluster")
        .then()
            .statusCode(anyOf(is(200), is(201), is(400))) // 400 if already exists
            .log().body();
        
        // Then create a simple echo pipeline
        Map<String, Object> pipelineConfig = Map.of(
            "name", "test-echo-pipeline",
            "description", "Test pipeline with echo module",
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
            .post("/api/v1/clusters/test-cluster/pipelines/test-echo-pipeline")
        .then()
            .statusCode(anyOf(is(200), is(201)))
            .log().body();
    }
    
    @Test
    void testExecutePipeline() {
        // Get the actual mapped ports
        String engineHost = environment.getServiceHost("pipeline-engine", ENGINE_HTTP_PORT);
        Integer enginePort = environment.getServicePort("pipeline-engine", ENGINE_HTTP_PORT);
        
        String baseUri = String.format("http://%s:%d", engineHost, enginePort);
        
        // Create test data
        Map<String, Object> testData = Map.of(
            "filename", "test.txt",
            "content", "Hello from integration test!",
            "metadata", Map.of(
                "source", "integration-test",
                "timestamp", System.currentTimeMillis()
            )
        );
        
        // Execute pipeline
        given()
            .baseUri(baseUri)
            .contentType(ContentType.JSON)
            .body(testData)
        .when()
            .post("/api/v1/pipelines/test-echo-pipeline/execute")
        .then()
            .statusCode(anyOf(is(200), is(202))) // 202 for async execution
            .log().body();
    }
}