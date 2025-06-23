package com.rokkon.pipeline.engine;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

/**
 * Basic integration test to verify the application is running.
 */
@QuarkusIntegrationTest
public class BasicHealthCheckIT {

    @Test
    void testHealthEndpoint() {
        given()
            .when().get("/q/health")
            .then()
            .statusCode(200)
            .body("status", is("UP"));
    }
    
    @Test
    void testOpenAPIEndpoint() {
        given()
            .when().get("/q/openapi")
            .then()
            .statusCode(200);
    }
}