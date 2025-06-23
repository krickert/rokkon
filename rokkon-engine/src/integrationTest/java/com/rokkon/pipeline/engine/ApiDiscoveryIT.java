package com.rokkon.pipeline.engine;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

/**
 * Test to discover what APIs are available in the application.
 */
@QuarkusIntegrationTest  
public class ApiDiscoveryIT {

    @Test
    void testSwaggerUIAvailable() {
        given()
            .when().get("/q/swagger-ui")
            .then()
            .statusCode(200);
    }
    
    @Test
    void testListAPIs() {
        // Print the OpenAPI spec to see what endpoints are available
        String openApiSpec = given()
            .when().get("/q/openapi")
            .then()
            .statusCode(200)
            .extract().asString();
            
        System.out.println("Available APIs:");
        System.out.println(openApiSpec);
    }
}