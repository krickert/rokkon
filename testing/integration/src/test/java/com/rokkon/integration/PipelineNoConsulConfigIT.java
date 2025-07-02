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
 * Integration test without Consul config to isolate networking issues
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PipelineNoConsulConfigIT {
    
    private static final String COMPOSE_FILE = "docker-compose-no-consul-config.yml";
    private static final int ENGINE_HTTP_PORT = 38082;
    private static final int ENGINE_GRPC_PORT = 48082;
    private static final int CONSUL_PORT = 8500;
    
    private static String engineHost;
    private static Integer enginePort;
    private static String baseUri;
    
    @Container
    static ComposeContainer environment = new ComposeContainer(
            new File(PipelineNoConsulConfigIT.class.getClassLoader()
                    .getResource(COMPOSE_FILE).getFile()))
            .withExposedService("consul-server", CONSUL_PORT, 
                    Wait.forHealthcheck().withStartupTimeout(Duration.ofSeconds(60)))
            .withExposedService("pipeline-engine", ENGINE_HTTP_PORT,
                    Wait.forHealthcheck().withStartupTimeout(Duration.ofSeconds(120)))
            .withLogConsumer("pipeline-engine", outputFrame -> 
                    System.out.print("[ENGINE] " + outputFrame.getUtf8String()));
    
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
            .statusCode(anyOf(is(200), is(404)))
            .log().body();
    }
}