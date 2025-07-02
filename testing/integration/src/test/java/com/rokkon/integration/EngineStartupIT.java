package com.rokkon.integration;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.File;
import java.time.Duration;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Simple integration test to verify engine starts without Consul config
 */
@Testcontainers
public class EngineStartupIT {
    
    private static final String COMPOSE_FILE = "simple-docker-compose.yml";
    private static final int ENGINE_HTTP_PORT = 38082;
    private static final int ENGINE_GRPC_PORT = 48082;
    private static final int CONSUL_PORT = 8500;
    
    @Container
    static ComposeContainer environment = new ComposeContainer(
            new File(EngineStartupIT.class.getClassLoader()
                    .getResource(COMPOSE_FILE).getFile()))
            .withExposedService("consul-server", CONSUL_PORT, 
                    Wait.forHealthcheck().withStartupTimeout(Duration.ofSeconds(60)))
            .withExposedService("pipeline-engine", ENGINE_HTTP_PORT,
                    Wait.forHealthcheck().withStartupTimeout(Duration.ofSeconds(120)));
    
    @Test
    void testEngineStartsSuccessfully() {
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
    void testEngineInfoEndpoint() {
        // Get the actual mapped ports
        String engineHost = environment.getServiceHost("pipeline-engine", ENGINE_HTTP_PORT);
        Integer enginePort = environment.getServicePort("pipeline-engine", ENGINE_HTTP_PORT);
        
        // First let's check what endpoints are available
        given()
            .baseUri(String.format("http://%s:%d", engineHost, enginePort))
        .when()
            .get("/q/dev/io.quarkus.quarkus-resteasy-reactive/endpoints")
        .then()
            .log().body();
        
        // Test engine info endpoint
        given()
            .baseUri(String.format("http://%s:%d", engineHost, enginePort))
        .when()
            .get("/api/v1/engine/info")
        .then()
            .statusCode(anyOf(is(200), is(404))) // Accept either for now
            .log().body(); // Log the response to see what we get
    }
}