package com.rokkon.pipeline.engine.api;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.Matchers.hasSize;

@QuarkusTest
public class ModuleManagementResourceTest {

    @Test
    public void testListAvailableModules() {
        given()
            .when()
            .get("/api/v1/module-management/available")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("$", hasSize(5))
            .body("[0].name", is("echo"))
            .body("[0].type", is("PROCESSOR"))
            .body("[0].image", is("rokkon/echo-module:latest"))
            .body("[0].memory", is("1G"))
            .body("[0].available", is(true));
    }

    @Test
    public void testListDeployedModules() {
        given()
            .when()
            .get("/api/v1/module-management/deployed")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("$", hasSize(0)); // Initially empty
    }

    @Test
    public void testGetModuleStatus_NotFound() {
        given()
            .when()
            .get("/api/v1/module-management/echo/status")
            .then()
            .statusCode(404)
            .contentType(ContentType.JSON)
            .body("error", is("Module not found: echo"));
    }

    @Test
    public void testDeployModule_NotInDevMode() {
        // This test will pass in normal test mode (not dev)
        given()
            .when()
            .post("/api/v1/module-management/echo/deploy")
            .then()
            .statusCode(503)
            .contentType(ContentType.JSON)
            .body("success", is(false))
            .body("message", containsString("only available in dev mode"));
    }

    @Test
    public void testStopModule_NotFound() {
        given()
            .when()
            .delete("/api/v1/module-management/echo")
            .then()
            .statusCode(404)
            .contentType(ContentType.JSON)
            .body("success", is(false))
            .body("message", is("Module not found: echo"));
    }

    @Test
    public void testTestModule() {
        given()
            .when()
            .post("/api/v1/module-management/echo/test")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("success", is(true))
            .body("message", is("Module test completed"))
            .body("details.module", is("echo"))
            .body("details.status", is("passed"))
            .body("details.tests", is(3));
    }

    @Test
    public void testGetModuleLogs_NotInDevMode() {
        given()
            .when()
            .get("/api/v1/module-management/echo/logs")
            .then()
            .statusCode(503)
            .contentType("text/plain")
            .body(is("Log retrieval is only available in dev mode"));
    }

    @Test
    public void testGetModuleLogs_WithLineParameter() {
        given()
            .queryParam("lines", 50)
            .when()
            .get("/api/v1/module-management/echo/logs")
            .then()
            .statusCode(503); // Still blocked in non-dev mode
    }
}