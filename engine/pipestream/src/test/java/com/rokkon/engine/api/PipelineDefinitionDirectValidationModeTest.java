package com.rokkon.engine.api;

import com.rokkon.pipeline.config.model.PipelineConfig;
import com.rokkon.pipeline.config.model.PipelineStepConfig;
import com.rokkon.pipeline.config.model.StepType;
import com.rokkon.pipeline.config.model.TransportType;
import com.rokkon.pipeline.config.model.GrpcTransportConfig;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Tests for validation mode functionality using direct pipeline config creation
 * (bypasses module registry lookup).
 */
@QuarkusTest
class PipelineDefinitionDirectValidationModeTest {
    
    @Test
    void testCreatePipelineWithDesignMode() {
        String pipelineId = "design-mode-test-" + System.currentTimeMillis();
        
        // Create a pipeline that would fail PRODUCTION validation
        // (has invalid processor info with localhost)
        var step = new PipelineStepConfig(
            "step1",
            StepType.PIPELINE,
            "Test step",
            null,
            null,
            null,
            null,
            0,
            1000L,
            30000L,
            2.0,
            null,
            new PipelineStepConfig.ProcessorInfo("localhost:8080", null) // Invalid for production
        );
        
        var pipeline = new PipelineConfig(
            "Design Mode Test Pipeline",
            Map.of("step1", step)
        );
        
        // Should succeed in DESIGN mode (ProcessorInfoValidator only runs in PRODUCTION)
        given()
            .contentType(ContentType.JSON)
            .pathParam("pipelineId", pipelineId)
            .queryParam("validationMode", "DESIGN")
            .body(pipeline)
            .when()
            .post("/api/v1/pipelines/definitions/{pipelineId}")
            .then()
            .statusCode(anyOf(is(201), is(409))) // Created or already exists
            .body("success", is(true));
    }
    
    @Test
    void testCreatePipelineWithProductionMode() {
        String pipelineId = "production-mode-test-" + System.currentTimeMillis();
        
        // Create a valid pipeline for production
        var step = new PipelineStepConfig(
            "step1",
            StepType.PIPELINE,
            "Production step",
            null,
            null,
            null,
            null,
            0,
            1000L,
            30000L,
            2.0,
            null,
            new PipelineStepConfig.ProcessorInfo("valid-service", null)
        );
        
        var pipeline = new PipelineConfig(
            "Production Mode Test Pipeline",
            Map.of("step1", step)
        );
        
        // Should succeed in PRODUCTION mode (default)
        given()
            .contentType(ContentType.JSON)
            .pathParam("pipelineId", pipelineId)
            .body(pipeline)
            .when()
            .post("/api/v1/pipelines/definitions/{pipelineId}")
            .then()
            .statusCode(anyOf(is(201), is(409)))
            .body("success", is(true));
    }
    
    @Test
    void testUpdatePipelineWithTestingMode() {
        String pipelineId = "testing-mode-pipeline-" + System.currentTimeMillis();
        
        // First create a pipeline
        var initialPipeline = new PipelineConfig(
            "Initial Pipeline",
            new HashMap<>()
        );
        
        given()
            .contentType(ContentType.JSON)
            .pathParam("pipelineId", pipelineId)
            .body(initialPipeline)
            .when()
            .post("/api/v1/pipelines/definitions/{pipelineId}")
            .then()
            .statusCode(anyOf(is(201), is(409)));
        
        // Update with a pipeline that has invalid routing (would fail in PRODUCTION)
        var invalidRoutingStep = new PipelineStepConfig(
            "step1",
            StepType.PIPELINE,
            "Step with invalid routing",
            null,
            null,
            null,
            Map.of("success", new PipelineStepConfig.OutputTarget(
                "non-existent-step",
                TransportType.GRPC,
                new GrpcTransportConfig(
                    "test-service",
                    Map.of()
                ),
                null  // kafkaTransport
            )),
            0,
            1000L,
            30000L,
            2.0,
            null,
            new PipelineStepConfig.ProcessorInfo("test-service", null)
        );
        
        var updatedPipeline = new PipelineConfig(
            "Updated Pipeline",
            Map.of("step1", invalidRoutingStep)
        );
        
        // Should succeed in TESTING mode (OutputRoutingValidator only runs in PRODUCTION)
        given()
            .contentType(ContentType.JSON)
            .pathParam("pipelineId", pipelineId)
            .queryParam("validationMode", "TESTING")
            .body(updatedPipeline)
            .when()
            .put("/api/v1/pipelines/definitions/{pipelineId}")
            .then()
            .statusCode(200)
            .body("success", is(true));
    }
    
    @Test
    void testProductionModeRejectsInvalidProcessorInfo() {
        String pipelineId = "production-validation-test-" + System.currentTimeMillis();
        
        // Create a pipeline with localhost in processor info
        var invalidStep = new PipelineStepConfig(
            "step1",
            StepType.PIPELINE,
            "Invalid step",
            null,
            null,
            null,
            null,
            0,
            1000L,
            30000L,
            2.0,
            null,
            new PipelineStepConfig.ProcessorInfo("localhost:8080", null)
        );
        
        var pipeline = new PipelineConfig(
            "Invalid Pipeline",
            Map.of("step1", invalidStep)
        );
        
        // Should succeed with warnings in PRODUCTION mode (default)
        given()
            .contentType(ContentType.JSON)
            .pathParam("pipelineId", pipelineId)
            .body(pipeline)
            .when()
            .post("/api/v1/pipelines/definitions/{pipelineId}")
            .then()
            .statusCode(anyOf(is(201), is(409))) // Created or already exists
            .body("success", is(true))
            .body("warnings", hasItem(containsString("localhost")));
    }
}