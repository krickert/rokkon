package com.rokkon.pipeline.consul.api;

import com.rokkon.pipeline.config.model.*;
import com.rokkon.pipeline.consul.service.PipelineConfigService;
import com.rokkon.pipeline.validation.ValidationResult;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@QuarkusTest
class PipelineConfigResourceTest {
    
    @InjectMock
    PipelineConfigService pipelineConfigService;
    
    private PipelineConfig testConfig;
    
    @BeforeEach
    void setup() {
        // Create test pipeline config
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "test-service", null
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "test-step",
            StepType.INITIAL_PIPELINE,
            processorInfo
        );
        
        testConfig = new PipelineConfig(
            "test-pipeline",
            Map.of("test-step", step)
        );
    }
    
    @Test
    void testCreatePipelineSuccess() {
        // Given
        when(pipelineConfigService.createPipeline(eq("test-cluster"), eq("test-pipeline"), any()))
            .thenReturn(CompletableFuture.completedFuture(
                new ValidationResult(true, List.of(), List.of())
            ));
        
        // When & Then
        given()
            .contentType(ContentType.JSON)
            .body(testConfig)
            .when()
            .post("/api/v1/clusters/test-cluster/pipelines/test-pipeline")
            .then()
            .statusCode(201)
            .body("success", equalTo(true))
            .body("message", equalTo("Pipeline created successfully"));
    }
    
    @Test
    void testCreatePipelineValidationFailure() {
        // Given
        when(pipelineConfigService.createPipeline(eq("test-cluster"), eq("test-pipeline"), any()))
            .thenReturn(CompletableFuture.completedFuture(
                new ValidationResult(false, 
                    List.of("Pipeline must have at least one SINK step"), 
                    List.of("Consider adding error handling"))
            ));
        
        // When & Then
        given()
            .contentType(ContentType.JSON)
            .body(testConfig)
            .when()
            .post("/api/v1/clusters/test-cluster/pipelines/test-pipeline")
            .then()
            .statusCode(400)
            .body("success", equalTo(false))
            .body("errors", hasSize(1))
            .body("errors[0]", equalTo("Pipeline must have at least one SINK step"))
            .body("warnings", hasSize(1));
    }
    
    @Test
    void testCreatePipelineAlreadyExists() {
        // Given
        when(pipelineConfigService.createPipeline(eq("test-cluster"), eq("test-pipeline"), any()))
            .thenReturn(CompletableFuture.completedFuture(
                new ValidationResult(false, 
                    List.of("Pipeline 'test-pipeline' already exists"), 
                    List.of())
            ));
        
        // When & Then
        given()
            .contentType(ContentType.JSON)
            .body(testConfig)
            .when()
            .post("/api/v1/clusters/test-cluster/pipelines/test-pipeline")
            .then()
            .statusCode(409)
            .body("success", equalTo(false));
    }
    
    @Test
    void testGetPipelineSuccess() {
        // Given
        when(pipelineConfigService.getPipeline("test-cluster", "test-pipeline"))
            .thenReturn(CompletableFuture.completedFuture(Optional.of(testConfig)));
        
        // When & Then
        given()
            .when()
            .get("/api/v1/clusters/test-cluster/pipelines/test-pipeline")
            .then()
            .statusCode(200)
            .body("name", equalTo("test-pipeline"))
            .body("pipelineSteps.test-step.stepName", equalTo("test-step"));
    }
    
    @Test
    void testGetPipelineNotFound() {
        // Given
        when(pipelineConfigService.getPipeline("test-cluster", "non-existent"))
            .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        
        // When & Then
        given()
            .when()
            .get("/api/v1/clusters/test-cluster/pipelines/non-existent")
            .then()
            .statusCode(404)
            .body("success", equalTo(false))
            .body("message", equalTo("Pipeline not found"));
    }
    
    @Test
    void testUpdatePipelineSuccess() {
        // Given
        when(pipelineConfigService.updatePipeline(eq("test-cluster"), eq("test-pipeline"), any()))
            .thenReturn(CompletableFuture.completedFuture(
                new ValidationResult(true, List.of(), List.of())
            ));
        
        // When & Then
        given()
            .contentType(ContentType.JSON)
            .body(testConfig)
            .when()
            .put("/api/v1/clusters/test-cluster/pipelines/test-pipeline")
            .then()
            .statusCode(200)
            .body("success", equalTo(true))
            .body("message", equalTo("Pipeline updated successfully"));
    }
    
    @Test
    void testUpdatePipelineNotFound() {
        // Given
        when(pipelineConfigService.updatePipeline(eq("test-cluster"), eq("non-existent"), any()))
            .thenReturn(CompletableFuture.completedFuture(
                new ValidationResult(false, 
                    List.of("Pipeline 'non-existent' not found"), 
                    List.of())
            ));
        
        // When & Then
        given()
            .contentType(ContentType.JSON)
            .body(testConfig)
            .when()
            .put("/api/v1/clusters/test-cluster/pipelines/non-existent")
            .then()
            .statusCode(404)
            .body("success", equalTo(false));
    }
    
    @Test
    void testDeletePipelineSuccess() {
        // Given
        when(pipelineConfigService.deletePipeline("test-cluster", "test-pipeline"))
            .thenReturn(CompletableFuture.completedFuture(
                new ValidationResult(true, List.of(), List.of())
            ));
        
        // When & Then
        given()
            .when()
            .delete("/api/v1/clusters/test-cluster/pipelines/test-pipeline")
            .then()
            .statusCode(204);
    }
    
    @Test
    void testDeletePipelineNotFound() {
        // Given
        when(pipelineConfigService.deletePipeline("test-cluster", "non-existent"))
            .thenReturn(CompletableFuture.completedFuture(
                new ValidationResult(false, 
                    List.of("Pipeline 'non-existent' not found"), 
                    List.of())
            ));
        
        // When & Then
        given()
            .when()
            .delete("/api/v1/clusters/test-cluster/pipelines/non-existent")
            .then()
            .statusCode(404)
            .body("success", equalTo(false));
    }
    
    @Test
    void testListPipelines() {
        // Given
        when(pipelineConfigService.listPipelines("test-cluster"))
            .thenReturn(CompletableFuture.completedFuture(
                Map.of("test-pipeline", testConfig)
            ));
        
        // When & Then
        given()
            .when()
            .get("/api/v1/clusters/test-cluster/pipelines")
            .then()
            .statusCode(200)
            .body("test-pipeline.name", equalTo("test-pipeline"));
    }
}