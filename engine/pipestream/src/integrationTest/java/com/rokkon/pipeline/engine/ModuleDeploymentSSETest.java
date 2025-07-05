package com.rokkon.pipeline.engine;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;

/**
 * Integration test for SSE deployment notifications.
 * Only runs in dev profile.
 */
@QuarkusIntegrationTest
@TestProfile(DevModeIntegrationTestProfile.class)
@EnabledIfSystemProperty(named = "test.integration.devmode", matches = "true")
public class ModuleDeploymentSSETest {

    @Test
    public void testSSEEndpoint() {
        // Test that SSE endpoint is available in dev mode
        RestAssured.given()
            .header("Accept", "text/event-stream")
            .when()
            .get("/api/v1/module-deployment/events")
            .then()
            .statusCode(200)
            .contentType("text/event-stream");
    }
    
    @Test
    public void testDeploymentNotifications() {
        // This would need a more complex setup to actually test SSE events
        // For now, just verify the deployment endpoint works
        RestAssured.given()
            .when()
            .get("/api/v1/module-management/available")
            .then()
            .statusCode(200)
            .body(containsString("echo"));
    }
}

class DevModeIntegrationTestProfile implements io.quarkus.test.junit.QuarkusTestProfile {
    @Override
    public String getConfigProfile() {
        return "dev";
    }
}