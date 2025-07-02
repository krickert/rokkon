package com.rokkon.engine.api;

import com.rokkon.pipeline.commons.model.GlobalModuleRegistryService;
import com.rokkon.pipeline.config.model.PipelineConfig;
import com.rokkon.pipeline.config.model.PipelineStepConfig;
import com.rokkon.pipeline.config.model.StepType;
import com.rokkon.pipeline.validation.ValidationMode;
import com.rokkon.pipeline.validation.ValidationResult;
import com.rokkon.pipeline.validation.ValidationResultFactory;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

/**
 * Tests for validation mode functionality in the pipeline definition REST API.
 */
@QuarkusTest
@TestProfile(NoConsulTestProfile.class)
class PipelineDefinitionValidationModeTest {
    
    @InjectMock
    GlobalModuleRegistryService moduleRegistryService;
    
    @InjectMock
    com.rokkon.pipeline.config.service.PipelineDefinitionService pipelineDefinitionService;
    
    @BeforeEach
    void setup() {
        // Mock the echo module using the correct constructor
        GlobalModuleRegistryService.ModuleRegistration echoModule = new GlobalModuleRegistryService.ModuleRegistration(
            "echo",              // moduleId
            "echo",              // moduleName
            "echo-impl-1",       // implementationId
            "localhost",         // host
            49092,              // port
            "PIPELINE",         // serviceType
            "1.0.0",           // version
            Map.of(),          // metadata
            System.currentTimeMillis(), // registeredAt
            "localhost",        // engineHost
            48082,             // enginePort
            null,              // jsonSchema
            true,              // enabled
            null,              // containerId
            null,              // containerName
            null               // hostname
        );
        
        // Use Quarkus @InjectMock style - just use when() without Mockito prefix
        when(moduleRegistryService.getModule("echo"))
            .thenReturn(Uni.createFrom().item(echoModule));
            
        // Mock the pipeline definition service to return success for all validation modes
        when(pipelineDefinitionService.createDefinition(anyString(), any(PipelineConfig.class), any(ValidationMode.class)))
            .thenReturn(Uni.createFrom().item(ValidationResultFactory.success()));
            
        when(pipelineDefinitionService.createDefinition(anyString(), any(PipelineConfig.class)))
            .thenReturn(Uni.createFrom().item(ValidationResultFactory.success()));
            
        when(pipelineDefinitionService.updateDefinition(anyString(), any(PipelineConfig.class), any(ValidationMode.class)))
            .thenReturn(Uni.createFrom().item(ValidationResultFactory.success()));
    }
    
    @Test
    void testCreateDefinitionWithDesignMode() {
        // Create a pipeline DTO that would fail production validation
        // (missing some details that would be required in production)
        var pipelineRequest = new PipelineDefinitionRequestDTO(
            "design-test-pipeline",
            "A pipeline in design phase",
            List.of(
                new PipelineStepRequestDTO(
                    "step1",
                    "echo",  // Module name
                    Map.of("message", "Hello")
                )
            )
        );
        
        // Should succeed in DESIGN mode
        given()
            .contentType(ContentType.JSON)
            .queryParam("validationMode", "DESIGN")
            .body(pipelineRequest)
            .when()
            .post("/api/v1/pipelines/definitions")
            .then()
            .statusCode(201)
            .body("success", is(true))
            .body("message", containsString("created successfully"));
    }
    
    @Test
    void testCreateDefinitionWithProductionMode() {
        // Create a complete pipeline DTO that passes production validation
        var pipelineRequest = new PipelineDefinitionRequestDTO(
            "production-test-pipeline",
            "A production-ready pipeline",
            List.of(
                new PipelineStepRequestDTO(
                    "step1",
                    "echo",  // Module name
                    Map.of("message", "Hello Production")
                )
            )
        );
        
        // Should succeed in PRODUCTION mode (default)
        given()
            .contentType(ContentType.JSON)
            .body(pipelineRequest)
            .when()
            .post("/api/v1/pipelines/definitions")
            .then()
            .statusCode(201)
            .body("success", is(true))
            .body("message", containsString("created successfully"));
    }
    
    @Test
    void testUpdateDefinitionWithTestingMode() {
        // First create a pipeline
        String pipelineId = "testing-mode-pipeline";
        var initialConfig = new PipelineConfig(
            "Testing Pipeline",
            new HashMap<>()
        );
        
        given()
            .contentType(ContentType.JSON)
            .pathParam("pipelineId", pipelineId)
            .body(initialConfig)
            .when()
            .post("/api/v1/pipelines/definitions/{pipelineId}")
            .then()
            .statusCode(anyOf(is(201), is(409))); // Created or already exists
        
        // Update with incomplete config in TESTING mode
        var updateConfig = new PipelineConfig(
            "Updated Testing Pipeline",
            Map.of("testStep", new PipelineStepConfig(
                "testStep",
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
                new PipelineStepConfig.ProcessorInfo("test-service", null)
            ))
        );
        
        // Should succeed in TESTING mode
        given()
            .contentType(ContentType.JSON)
            .pathParam("pipelineId", pipelineId)
            .queryParam("validationMode", "TESTING")
            .body(updateConfig)
            .when()
            .put("/api/v1/pipelines/definitions/{pipelineId}")
            .then()
            .statusCode(200)
            .body("success", is(true));
    }
    
    @Test
    void testInvalidValidationMode() {
        var pipelineRequest = new PipelineDefinitionRequestDTO(
            "invalid-mode-pipeline",
            "Test invalid mode",
            List.of()
        );
        
        // Should fail with invalid validation mode
        // JAX-RS returns 404 when it can't convert the enum value
        given()
            .contentType(ContentType.JSON)
            .queryParam("validationMode", "INVALID_MODE")
            .body(pipelineRequest)
            .when()
            .post("/api/v1/pipelines/definitions")
            .then()
            .statusCode(404);
    }
}