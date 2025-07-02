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
 * Basic integration test with Consul seeding before engine startup
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PipelineBasicSeedingIT {
    
    private static final String COMPOSE_FILE = "docker-compose-basic-seeding.yml";
    private static final int ENGINE_HTTP_PORT = 38082;
    private static final int ENGINE_GRPC_PORT = 48082;
    private static final int CONSUL_PORT = 8500;
    
    private static String engineHost;
    private static Integer enginePort;
    private static String baseUri;
    
    @Container
    static ComposeContainer environment = new ComposeContainer(
            new File(PipelineBasicSeedingIT.class.getClassLoader()
                    .getResource(COMPOSE_FILE).getFile()))
            .withExposedService("consul-server", CONSUL_PORT, 
                    Wait.forHealthcheck().withStartupTimeout(Duration.ofSeconds(60)))
            .withExposedService("pipeline-engine", ENGINE_HTTP_PORT,
                    Wait.forHealthcheck().withStartupTimeout(Duration.ofSeconds(300)))
            .withLogConsumer("pipeline-engine", outputFrame -> 
                    System.out.print("[ENGINE] " + outputFrame.getUtf8String()))
            .withLogConsumer("consul-seeder-app", outputFrame -> 
                    System.out.print("[SEEDER-APP] " + outputFrame.getUtf8String()))
            .withLogConsumer("consul-seeder-cluster", outputFrame -> 
                    System.out.print("[SEEDER-CLUSTER] " + outputFrame.getUtf8String()))
            .withLogConsumer("consul-verify", outputFrame -> 
                    System.out.print("[VERIFY] " + outputFrame.getUtf8String()));
    
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
}