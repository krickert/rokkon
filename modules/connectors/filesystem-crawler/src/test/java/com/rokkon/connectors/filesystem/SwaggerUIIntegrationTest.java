package com.rokkon.connectors.filesystem;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;

/**
 * Integration test for the Swagger UI in the filesystem crawler connector.
 * This test verifies that the Swagger UI endpoint is accessible and returns the expected OpenAPI documentation.
 */
@QuarkusTest
public class SwaggerUIIntegrationTest {

    /**
     * Test that the OpenAPI endpoint returns a valid OpenAPI document.
     */
    @Test
    public void testOpenAPIEndpoint() {
        given()
            .when()
            .get("/q/openapi")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("openapi", is("3.0.3"))
            .body("info.title", containsString("Filesystem Crawler API"))
            .body("paths./api/crawler/status.get.summary", is("Get crawler status"))
            .body("paths./api/crawler/crawl.post.summary", is("Trigger a crawl"));
    }

    /**
     * Test that the Swagger UI endpoint is accessible.
     */
    @Test
    public void testSwaggerUIEndpoint() {
        given()
            .when()
            .get("/q/swagger-ui")
            .then()
            .statusCode(200)
            .contentType(ContentType.HTML)
            .body(containsString("Swagger UI"))
            .body(containsString("OpenAPI"));
    }

    /**
     * Test that the health endpoint is accessible and returns the expected status.
     */
    @Test
    public void testHealthEndpoint() {
        given()
            .when()
            .get("/q/health")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("status", is("UP"));
    }
}