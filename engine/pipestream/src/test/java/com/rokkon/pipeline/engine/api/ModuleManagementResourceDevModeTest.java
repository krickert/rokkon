package com.rokkon.pipeline.engine.api;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;

/**
 * Tests for ModuleManagementResource in dev mode.
 * These tests use a test profile that simulates dev mode.
 */
@QuarkusTest
@TestProfile(ModuleManagementResourceDevModeTest.DevModeTestProfile.class)
public class ModuleManagementResourceDevModeTest {

    @Test
    public void testDeployModule_InDevMode() {
        given()
            .when()
            .post("/api/v1/module-management/echo/deploy")
            .then()
            .statusCode(202) // Accepted
            .contentType(ContentType.JSON)
            .body("success", is(true))
            .body("message", is("Module deployment initiated"))
            .body("details.module", is("echo"))
            .body("details.status", is("deploying"));
    }

    @Test
    public void testGetModuleLogs_InDevMode() {
        given()
            .when()
            .get("/api/v1/module-management/echo/logs")
            .then()
            .statusCode(200)
            .contentType("text/plain")
            .body(containsString("Sample logs for module: echo"));
    }

    /**
     * Test profile that sets the quarkus.profile to "dev"
     */
    public static class DevModeTestProfile implements io.quarkus.test.junit.QuarkusTestProfile {
        @Override
        public java.util.Map<String, String> getConfigOverrides() {
            return java.util.Map.of("quarkus.profile", "dev");
        }
    }
}