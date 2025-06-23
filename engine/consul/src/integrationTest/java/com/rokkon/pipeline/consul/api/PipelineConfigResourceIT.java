package com.rokkon.pipeline.consul.api;

import com.rokkon.pipeline.config.model.*;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Integration test for the Pipeline Config REST API.
 * Note: Requires a running Consul instance on localhost:8500
 */
@QuarkusIntegrationTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Disabled("Requires running Consul instance")
class PipelineConfigResourceIT {
    
    private static final String CLUSTER_NAME = "test-cluster-it";
    private static final String PIPELINE_ID = "test-pipeline-it";
    
    private PipelineConfig createTestPipeline() {
        // Create INITIAL_PIPELINE step
        PipelineStepConfig.ProcessorInfo sourceProcessor = new PipelineStepConfig.ProcessorInfo(
            "source-service", null
        );
        
        PipelineStepConfig sourceStep = new PipelineStepConfig(
            "source-step",
            StepType.INITIAL_PIPELINE,
            sourceProcessor
        );
        
        // Create SINK step
        PipelineStepConfig.ProcessorInfo sinkProcessor = new PipelineStepConfig.ProcessorInfo(
            "sink-service", null
        );
        
        PipelineStepConfig sinkStep = new PipelineStepConfig(
            "sink-step",
            StepType.SINK,
            sinkProcessor
        );
        
        return new PipelineConfig(
            PIPELINE_ID,
            Map.of(
                "source-step", sourceStep,
                "sink-step", sinkStep
            )
        );
    }
    
    @Test
    @Order(1)
    void testCreatePipeline() {
        PipelineConfig config = createTestPipeline();
        
        given()
            .contentType(ContentType.JSON)
            .body(config)
            .when()
            .post("/api/v1/clusters/{cluster}/pipelines/{pipeline}", CLUSTER_NAME, PIPELINE_ID)
            .then()
            .statusCode(201)
            .body("success", equalTo(true))
            .body("message", equalTo("Pipeline created successfully"));
    }
    
    @Test
    @Order(2)
    void testGetCreatedPipeline() {
        given()
            .when()
            .get("/api/v1/clusters/{cluster}/pipelines/{pipeline}", CLUSTER_NAME, PIPELINE_ID)
            .then()
            .statusCode(200)
            .body("name", equalTo(PIPELINE_ID))
            .body("pipelineSteps", hasKey("source-step"))
            .body("pipelineSteps", hasKey("sink-step"))
            .body("pipelineSteps.source-step.stepType", equalTo("INITIAL_PIPELINE"))
            .body("pipelineSteps.sink-step.stepType", equalTo("SINK"));
    }
    
    @Test
    @Order(3)
    void testUpdatePipeline() {
        PipelineConfig config = createTestPipeline();
        
        // Add a middle step
        PipelineStepConfig.ProcessorInfo middleProcessor = new PipelineStepConfig.ProcessorInfo(
            "middle-service", null
        );
        
        PipelineStepConfig middleStep = new PipelineStepConfig(
            "middle-step",
            StepType.PIPELINE,
            middleProcessor
        );
        
        config = new PipelineConfig(
            PIPELINE_ID,
            Map.of(
                "source-step", config.pipelineSteps().get("source-step"),
                "middle-step", middleStep,
                "sink-step", config.pipelineSteps().get("sink-step")
            )
        );
        
        given()
            .contentType(ContentType.JSON)
            .body(config)
            .when()
            .put("/api/v1/clusters/{cluster}/pipelines/{pipeline}", CLUSTER_NAME, PIPELINE_ID)
            .then()
            .statusCode(200)
            .body("success", equalTo(true))
            .body("message", equalTo("Pipeline updated successfully"));
        
        // Verify the update
        given()
            .when()
            .get("/api/v1/clusters/{cluster}/pipelines/{pipeline}", CLUSTER_NAME, PIPELINE_ID)
            .then()
            .statusCode(200)
            .body("pipelineSteps", hasKey("middle-step"));
    }
    
    @Test
    @Order(4)
    void testListPipelines() {
        given()
            .when()
            .get("/api/v1/clusters/{cluster}/pipelines", CLUSTER_NAME)
            .then()
            .statusCode(200)
            .body("$", hasKey(PIPELINE_ID))
            .body(PIPELINE_ID + ".name", equalTo(PIPELINE_ID));
    }
    
    @Test
    @Order(5)
    void testCreateDuplicatePipeline() {
        PipelineConfig config = createTestPipeline();
        
        given()
            .contentType(ContentType.JSON)
            .body(config)
            .when()
            .post("/api/v1/clusters/{cluster}/pipelines/{pipeline}", CLUSTER_NAME, PIPELINE_ID)
            .then()
            .statusCode(409)
            .body("success", equalTo(false))
            .body("errors", hasItem(containsString("already exists")));
    }
    
    @Test
    @Order(6)
    void testValidationFailure() {
        // Create invalid pipeline (no SINK step)
        PipelineStepConfig.ProcessorInfo processor = new PipelineStepConfig.ProcessorInfo(
            "service", null
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "only-step",
            StepType.PIPELINE, // Not INITIAL_PIPELINE or SINK
            processor
        );
        
        PipelineConfig invalidConfig = new PipelineConfig(
            "invalid-pipeline",
            Map.of("only-step", step)
        );
        
        given()
            .contentType(ContentType.JSON)
            .body(invalidConfig)
            .when()
            .post("/api/v1/clusters/{cluster}/pipelines/invalid-pipeline", CLUSTER_NAME)
            .then()
            .statusCode(400)
            .body("success", equalTo(false))
            .body("errors", hasSize(greaterThan(0)));
    }
    
    @Test
    @Order(7)
    void testDeletePipeline() {
        given()
            .when()
            .delete("/api/v1/clusters/{cluster}/pipelines/{pipeline}", CLUSTER_NAME, PIPELINE_ID)
            .then()
            .statusCode(204);
        
        // Verify deletion
        given()
            .when()
            .get("/api/v1/clusters/{cluster}/pipelines/{pipeline}", CLUSTER_NAME, PIPELINE_ID)
            .then()
            .statusCode(404)
            .body("success", equalTo(false))
            .body("message", equalTo("Pipeline not found"));
    }
    
    @Test
    @Order(8)
    void testDeleteNonExistentPipeline() {
        given()
            .when()
            .delete("/api/v1/clusters/{cluster}/pipelines/non-existent", CLUSTER_NAME)
            .then()
            .statusCode(404)
            .body("success", equalTo(false))
            .body("errors", hasItem(containsString("not found")));
    }
    
    @Test
    void testOpenApiDocumentation() {
        given()
            .when()
            .get("/openapi")
            .then()
            .statusCode(200)
            .body("info.title", equalTo("Rokkon Pipeline Configuration API"))
            .body("paths", hasKey("/api/v1/clusters/{clusterName}/pipelines/{pipelineId}"));
    }
    
    @Test
    void testSwaggerUI() {
        given()
            .when()
            .get("/swagger-ui/")
            .then()
            .statusCode(200);
    }
}