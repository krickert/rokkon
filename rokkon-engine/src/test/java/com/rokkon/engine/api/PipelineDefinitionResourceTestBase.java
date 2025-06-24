package com.rokkon.engine.api;

import com.rokkon.pipeline.config.model.PipelineConfig;
import com.rokkon.pipeline.consul.model.PipelineDefinitionSummary;
import io.restassured.http.ContentType;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.Matchers.hasSize;

/**
 * Base test class for PipelineDefinitionResource tests.
 * This abstract class provides common test helper methods and can be extended by both 
 * unit tests (@QuarkusTest with mocks) and integration tests (@QuarkusIntegrationTest without mocks).
 */
public abstract class PipelineDefinitionResourceTestBase {

    /**
     * Helper method to create a test pipeline definition using the DTO endpoint.
     * @param pipelineName The name of the pipeline to create
     * @param description The description of the pipeline
     * @param expectedStatusCode The expected HTTP status code
     * @return The response body as a String
     */
    protected String createTestPipelineDefinition(String pipelineName, String description, int expectedStatusCode) {
        return given()
                .contentType(ContentType.JSON)
                .body("""
                    {
                        "name": "%s",
                        "description": "%s",
                        "steps": [
                            {
                                "name": "step1",
                                "module": "test-module",
                                "config": {
                                    "param1": "value1",
                                    "param2": 42
                                }
                            }
                        ]
                    }
                    """.formatted(pipelineName, description))
                .when()
                .post("/api/v1/pipelines/definitions")
                .then()
                .statusCode(expectedStatusCode)
                .extract()
                .asString();
    }

    /**
     * Helper method to get a pipeline definition.
     * @param pipelineId The ID of the pipeline to get
     * @param expectedStatusCode The expected HTTP status code
     * @return The response body as a String
     */
    protected String getPipelineDefinition(String pipelineId, int expectedStatusCode) {
        return given()
                .when()
                .get("/api/v1/pipelines/definitions/" + pipelineId)
                .then()
                .statusCode(expectedStatusCode)
                .extract()
                .asString();
    }

    /**
     * Helper method to list all pipeline definitions.
     * @param expectedCount The expected number of pipeline definitions
     * @return The response body as a String
     */
    protected String listPipelineDefinitions(int expectedCount) {
        return given()
                .when()
                .get("/api/v1/pipelines/definitions")
                .then()
                .statusCode(200)
                .body("$", hasSize(expectedCount))
                .extract()
                .asString();
    }

    /**
     * Helper method to update a pipeline definition.
     * @param pipelineId The ID of the pipeline to update
     * @param updatedName The updated name of the pipeline
     * @param updatedDescription The updated description of the pipeline
     * @param expectedStatusCode The expected HTTP status code
     * @return The response body as a String
     */
    protected String updatePipelineDefinition(String pipelineId, String updatedName, String updatedDescription, int expectedStatusCode) {
        return given()
                .contentType(ContentType.JSON)
                .body("""
                    {
                        "name": "%s",
                        "description": "%s",
                        "pipelineSteps": {}
                    }
                    """.formatted(updatedName, updatedDescription))
                .when()
                .put("/api/v1/pipelines/definitions/" + pipelineId)
                .then()
                .statusCode(expectedStatusCode)
                .extract()
                .asString();
    }

    /**
     * Helper method to delete a pipeline definition.
     * @param pipelineId The ID of the pipeline to delete
     * @param expectedStatusCode The expected HTTP status code
     * @return The response body as a String
     */
    protected String deletePipelineDefinition(String pipelineId, int expectedStatusCode) {
        return given()
                .when()
                .delete("/api/v1/pipelines/definitions/" + pipelineId)
                .then()
                .statusCode(expectedStatusCode)
                .extract()
                .asString();
    }
}
