package com.rokkon.testmodule.health;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;

/**
 * Simple test to verify health endpoints are working.
 * This tests the HTTP health endpoint first before testing gRPC.
 */
@QuarkusTest
class SimpleHealthCheckTest {
    
    @Test
    void testHttpHealthEndpoint() {
        // First verify HTTP health endpoint works
        RestAssured.given()
            .when()
            .get("/q/health")
            .then()
            .statusCode(200)
            .body("status", is("UP"));
    }
    
    @Test
    void testHttpHealthLiveEndpoint() {
        RestAssured.given()
            .when()
            .get("/q/health/live")
            .then()
            .statusCode(200)
            .body("status", is("UP"));
    }
    
    @Test
    void testHttpHealthReadyEndpoint() {
        RestAssured.given()
            .when()
            .get("/q/health/ready")
            .then()
            .statusCode(200)
            .body("status", is("UP"));
    }
}