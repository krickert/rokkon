package com.rokkon.testmodule.health;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;

import static org.hamcrest.CoreMatchers.is;

/**
 * Simple test to verify health endpoints are working.
 * This tests the HTTP health endpoint first before testing gRPC.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Order(1) // Run this test class first to avoid startup issues
class SimpleHealthCheckTest {
    
    @Test
    @Order(1)
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
    @Order(2)
    void testHttpHealthLiveEndpoint() {
        RestAssured.given()
            .when()
            .get("/q/health/live")
            .then()
            .statusCode(200)
            .body("status", is("UP"));
    }
    
    @Test
    @Order(3)
    void testHttpHealthReadyEndpoint() {
        RestAssured.given()
            .when()
            .get("/q/health/ready")
            .then()
            .statusCode(200)
            .body("status", is("UP"));
    }
}