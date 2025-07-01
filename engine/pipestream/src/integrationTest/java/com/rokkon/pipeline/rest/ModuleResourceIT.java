package com.rokkon.pipeline.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Simple REST API test for module registration endpoints.
 * Tests the basic API contract without complex setup.
 */
@QuarkusTest
class ModuleResourceTest {

    @Test
    void testListModulesEndpoint() {
        given()
            .when()
            .get("/api/v1/modules")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("$", is(notNullValue()));
    }

    @Test
    void testListAllServicesEndpoint() {
        given()
            .when()
            .get("/api/v1/modules/all-services")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("$", is(notNullValue()));
    }

    @Test
    void testRegisterModuleValidation() {
        // Test that missing required fields return 400
        given()
            .contentType(ContentType.JSON)
            .body("{}")
            .when()
            .post("/api/v1/modules")
            .then()
            .statusCode(400);
    }

    @Test
    void testModuleNotFound() {
        given()
            .when()
            .delete("/api/v1/modules/non-existent-module")
            .then()
            .statusCode(404);
    }
}