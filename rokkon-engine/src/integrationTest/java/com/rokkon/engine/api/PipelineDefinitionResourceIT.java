package com.rokkon.engine.api;

import com.rokkon.pipeline.engine.test.ConsulTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.*;
import static io.restassured.RestAssured.given;

/**
 * Integration tests for PipelineDefinitionResource.
 * Extends PipelineDefinitionResourceTestBase for common test functionality.
 * Uses real service implementations instead of mocks.
 * 
 * NOTE: These tests require a complex Docker setup with Consul and the Rokkon Engine
 * running in the same network with specific hostnames and ports. See the
 * INTEGRATION_TEST_SETUP.md document for details on the required setup.
 * 
 * Currently, these tests may fail due to incomplete Docker setup. A more comprehensive
 * test resource that sets up the complete Docker environment is needed.
 */
@QuarkusIntegrationTest
@QuarkusTestResource(ConsulTestResource.class)
@TestProfile(PipelineDefinitionResourceIT.RandomClusterProfile.class)
@TestInstance(Lifecycle.PER_CLASS)
public class PipelineDefinitionResourceIT extends PipelineDefinitionResourceTestBase {

    public static class RandomClusterProfile implements io.quarkus.test.junit.QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                "rokkon.cluster.name", "test-cluster-" + UUID.randomUUID()
            );
        }
    }

    private static final String TEST_PIPELINE_NAME = "integration-test-pipeline";
    private static final String TEST_PIPELINE_DESCRIPTION = "Pipeline for integration testing";

    @BeforeEach
    void setup() {
        // Clean up any test pipelines from previous test runs
        try {
            deletePipelineDefinition(TEST_PIPELINE_NAME, 204);
        } catch (Exception e) {
            // Ignore errors if the pipeline doesn't exist
        }
    }

    @Test
    void testCreateAndGetPipeline() {
        // Create a test pipeline
        createTestPipelineDefinition(TEST_PIPELINE_NAME, TEST_PIPELINE_DESCRIPTION, 201);

        // Get the pipeline and verify it exists
        given()
            .when()
            .get("/api/v1/pipelines/definitions/" + TEST_PIPELINE_NAME)
            .then()
            .statusCode(200)
            .body("name", equalTo(TEST_PIPELINE_NAME));
    }

    @Test
    void testListPipelines() {
        // Create a test pipeline
        createTestPipelineDefinition(TEST_PIPELINE_NAME, TEST_PIPELINE_DESCRIPTION, 201);

        // List pipelines and verify the test pipeline is included
        given()
            .when()
            .get("/api/v1/pipelines/definitions")
            .then()
            .statusCode(200)
            .body("find { it.id == '" + TEST_PIPELINE_NAME + "' }.name", equalTo(TEST_PIPELINE_NAME));
    }

    @Test
    void testUpdatePipeline() {
        // Create a test pipeline
        createTestPipelineDefinition(TEST_PIPELINE_NAME, TEST_PIPELINE_DESCRIPTION, 201);

        // Update the pipeline
        String updatedDescription = "Updated description";
        given()
            .contentType("application/json")
            .body("""
                {
                    "name": "%s",
                    "description": "%s",
                    "pipelineSteps": {}
                }
                """.formatted(TEST_PIPELINE_NAME, updatedDescription))
            .when()
            .put("/api/v1/pipelines/definitions/" + TEST_PIPELINE_NAME)
            .then()
            .statusCode(200)
            .body("success", is(true));

        // Get the pipeline and verify it was updated
        given()
            .when()
            .get("/api/v1/pipelines/definitions/" + TEST_PIPELINE_NAME)
            .then()
            .statusCode(200)
            .body("name", equalTo(TEST_PIPELINE_NAME));
    }

    @Test
    void testDeletePipeline() {
        // Create a test pipeline
        createTestPipelineDefinition(TEST_PIPELINE_NAME, TEST_PIPELINE_DESCRIPTION, 201);

        // Delete the pipeline
        given()
            .when()
            .delete("/api/v1/pipelines/definitions/" + TEST_PIPELINE_NAME)
            .then()
            .statusCode(204);

        // Verify the pipeline is gone
        given()
            .when()
            .get("/api/v1/pipelines/definitions/" + TEST_PIPELINE_NAME)
            .then()
            .statusCode(404);
    }

    @Test
    void testCreateDuplicatePipeline() {
        // Create a test pipeline
        createTestPipelineDefinition(TEST_PIPELINE_NAME, TEST_PIPELINE_DESCRIPTION, 201);

        // Try to create it again, should fail with 409 Conflict
        createTestPipelineDefinition(TEST_PIPELINE_NAME, TEST_PIPELINE_DESCRIPTION, 409);
    }
}
