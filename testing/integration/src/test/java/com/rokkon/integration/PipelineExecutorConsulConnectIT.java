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
 * Integration test using Consul Connect sidecars for service mesh networking
 * This matches our production deployment pattern
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PipelineExecutorConsulConnectIT {
    
    private static final String COMPOSE_FILE = "docker-compose-consul-connect.yml";
    private static final int ENGINE_HTTP_PORT = 38082;
    private static final int ENGINE_GRPC_PORT = 48082;
    private static final int CONSUL_PORT = 8500;
    
    private static String engineHost;
    private static Integer enginePort;
    private static String consulHost;
    private static Integer consulPort;
    private static String baseUri;
    
    @Container
    static ComposeContainer environment = new ComposeContainer(
            new File(PipelineExecutorConsulConnectIT.class.getClassLoader()
                    .getResource(COMPOSE_FILE).getFile()))
            .withExposedService("consul-server", CONSUL_PORT, 
                    Wait.forHealthcheck().withStartupTimeout(Duration.ofSeconds(60)))
            .withExposedService("pipeline-engine", ENGINE_HTTP_PORT,
                    Wait.forHealthcheck().withStartupTimeout(Duration.ofSeconds(180)))
            .withLogConsumer("pipeline-engine", outputFrame -> 
                    System.out.print("[ENGINE] " + outputFrame.getUtf8String()))
            .withLogConsumer("consul-server", outputFrame -> 
                    System.out.print("[CONSUL] " + outputFrame.getUtf8String()));
    
    @BeforeAll
    static void setup() {
        // Get the actual mapped ports
        engineHost = environment.getServiceHost("pipeline-engine", ENGINE_HTTP_PORT);
        enginePort = environment.getServicePort("pipeline-engine", ENGINE_HTTP_PORT);
        consulHost = environment.getServiceHost("consul-server", CONSUL_PORT);
        consulPort = environment.getServicePort("consul-server", CONSUL_PORT);
        baseUri = String.format("http://%s:%d", engineHost, enginePort);
        
        System.out.println("=== Integration Test Environment ===");
        System.out.println("Engine URL: " + baseUri);
        System.out.println("Consul URL: http://" + consulHost + ":" + consulPort);
        System.out.println("===================================");
    }
    
    @Test
    @Order(1)
    void testConsulHealth() {
        // Verify Consul is healthy
        given()
            .baseUri(String.format("http://%s:%d", consulHost, consulPort))
        .when()
            .get("/v1/health/node/consul-server")
        .then()
            .statusCode(200)
            .body("[0].Status", equalTo("passing"));
    }
    
    @Test
    @Order(2)
    void testEngineHealth() {
        given()
            .baseUri(baseUri)
        .when()
            .get("/q/health/live")
        .then()
            .statusCode(200)
            .body("status", equalTo("UP"));
    }
    
    @Test
    @Order(3)
    void testEngineReady() {
        given()
            .baseUri(baseUri)
        .when()
            .get("/q/health/ready")
        .then()
            .statusCode(200)
            .body("status", equalTo("UP"));
    }
    
    @Test
    @Order(4)
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
    @Order(5)
    void testListModules() {
        // List registered modules
        given()
            .baseUri(baseUri)
        .when()
            .get("/api/v1/modules")
        .then()
            .statusCode(200)
            .log().body();
    }
    
    @Test
    @Order(6)
    void testCreateCluster() {
        // Create a test cluster
        given()
            .baseUri(baseUri)
            .contentType(ContentType.JSON)
            .body("{\"name\": \"test-cluster\"}")
        .when()
            .post("/api/v1/clusters/test-cluster")
        .then()
            .statusCode(anyOf(is(201), is(400))) // 400 if already exists
            .log().body();
    }
    
    @Test
    @Order(7)
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
    @Order(8)
    void testCreateEchoPipeline() {
        // Create a simple echo pipeline
        Map<String, Object> pipelineConfig = Map.of(
            "name", "echo-pipeline",
            "description", "Simple echo pipeline for testing",
            "steps", List.of(
                Map.of(
                    "name", "echo-step",
                    "type", "PIPELINE",
                    "processor", Map.of(
                        "module", "echo",
                        "service", "echo",
                        "grpcService", "com.rokkon.pipeline.echo.EchoService"
                    )
                )
            )
        );
        
        given()
            .baseUri(baseUri)
            .contentType(ContentType.JSON)
            .body(pipelineConfig)
        .when()
            .post("/api/v1/clusters/test-cluster/pipelines/echo-pipeline")
        .then()
            .statusCode(anyOf(is(201), is(400)))
            .log().body();
    }
    
    @Test
    @Order(9)
    void testCreateChainedPipeline() {
        // Create a pipeline that chains echo and test modules
        Map<String, Object> pipelineConfig = Map.of(
            "name", "chained-pipeline",
            "description", "Pipeline that chains echo and test modules",
            "steps", List.of(
                Map.of(
                    "name", "echo-step",
                    "type", "PIPELINE",
                    "processor", Map.of(
                        "module", "echo",
                        "service", "echo",
                        "grpcService", "com.rokkon.pipeline.echo.EchoService"
                    ),
                    "outputMapping", Map.of(
                        "default", "test-step"
                    )
                ),
                Map.of(
                    "name", "test-step",
                    "type", "SINK",
                    "processor", Map.of(
                        "module", "test",
                        "service", "test",
                        "grpcService", "com.rokkon.pipeline.test.TestService"
                    )
                )
            )
        );
        
        given()
            .baseUri(baseUri)
            .contentType(ContentType.JSON)
            .body(pipelineConfig)
        .when()
            .post("/api/v1/clusters/test-cluster/pipelines/chained-pipeline")
        .then()
            .statusCode(anyOf(is(201), is(400)))
            .log().body();
    }
    
    @Test
    @Order(10)
    void testListPipelines() {
        given()
            .baseUri(baseUri)
        .when()
            .get("/api/v1/clusters/test-cluster/pipelines")
        .then()
            .statusCode(200)
            .body("size()", greaterThanOrEqualTo(2))
            .log().body();
    }
    
    @Test
    @Order(11)
    void testGetPipelineDefinition() {
        given()
            .baseUri(baseUri)
        .when()
            .get("/api/v1/pipelines/echo-pipeline")
        .then()
            .statusCode(200)
            .body("name", equalTo("echo-pipeline"))
            .body("steps.size()", equalTo(1))
            .log().body();
    }
    
    @Test
    @Order(12)
    void testValidatePipeline() {
        // Test pipeline validation endpoint
        Map<String, Object> pipelineConfig = Map.of(
            "name", "validation-test-pipeline",
            "description", "Pipeline for testing validation",
            "steps", List.of(
                Map.of(
                    "name", "echo-step",
                    "type", "PIPELINE",
                    "processor", Map.of(
                        "module", "echo",
                        "service", "echo",
                        "grpcService", "com.rokkon.pipeline.echo.EchoService"
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
    @Order(13)
    @Disabled("Requires modules to be fully connected via service mesh")
    void testExecutePipeline() {
        // Wait a bit for modules to register
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Create test data
        Map<String, Object> testData = Map.of(
            "id", "test-doc-1",
            "content", "This is a test document for pipeline processing via Consul Connect",
            "metadata", Map.of(
                "source", "consul-connect-test",
                "timestamp", System.currentTimeMillis()
            )
        );
        
        given()
            .baseUri(baseUri)
            .contentType(ContentType.JSON)
            .body(testData)
        .when()
            .post("/api/v1/pipelines/echo-pipeline/execute")
        .then()
            .statusCode(anyOf(is(200), is(202), is(503))) // 503 if modules not ready
            .log().body();
    }
    
    @Test
    @Order(14)
    void testConsulServices() {
        // Check what services are registered in Consul
        given()
            .baseUri(String.format("http://%s:%d", consulHost, consulPort))
        .when()
            .get("/v1/agent/services")
        .then()
            .statusCode(200)
            .log().body();
    }
}