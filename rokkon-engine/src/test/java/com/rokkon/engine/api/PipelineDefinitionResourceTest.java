package com.rokkon.engine.api;

import com.rokkon.pipeline.config.model.PipelineConfig;
import com.rokkon.pipeline.consul.model.PipelineDefinitionSummary;
import com.rokkon.pipeline.consul.service.PipelineDefinitionService;
import com.rokkon.pipeline.consul.service.GlobalModuleRegistryService;
import com.rokkon.pipeline.consul.service.GlobalModuleRegistryService.ModuleRegistration;
import com.rokkon.pipeline.validation.ValidationResult;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class PipelineDefinitionResourceTest {

    @InjectMock
    PipelineDefinitionService pipelineDefinitionService;

    @InjectMock
    GlobalModuleRegistryService moduleRegistryService;

    @BeforeEach
    void setup() {
        // Set up common mock responses
    }

    @Test
    void testListDefinitions() {
        // Mock the service to return a list of pipeline definitions
        PipelineDefinitionSummary def1 = new PipelineDefinitionSummary(
            "pipeline-1", "Test Pipeline 1", "Data processing pipeline", 
            5, "2024-01-15T10:00:00Z", "2024-01-15T12:00:00Z", 2
        );
        PipelineDefinitionSummary def2 = new PipelineDefinitionSummary(
            "pipeline-2", "Test Pipeline 2", "ETL pipeline", 
            3, "2024-01-16T10:00:00Z", "2024-01-16T12:00:00Z", 1
        );

        when(pipelineDefinitionService.listDefinitions())
            .thenReturn(Uni.createFrom().item(List.of(def1, def2)));

        // List pipeline definitions
        given()
            .when()
            .get("/api/v1/pipelines/definitions")
            .then()
            .statusCode(200)
            .body("$", hasSize(2))
            .body("[0].id", equalTo("pipeline-1"))
            .body("[0].name", equalTo("Test Pipeline 1"))
            .body("[1].id", equalTo("pipeline-2"))
            .body("[1].name", equalTo("Test Pipeline 2"));
    }

    @Test
    void testGetDefinition() {
        String pipelineId = "test-pipeline-123";

        // Create a mock pipeline config using record constructor
        PipelineConfig mockConfig = new PipelineConfig(
            "Test Pipeline",
            Map.of() // Empty pipeline steps for this test
        );

        when(pipelineDefinitionService.getDefinition(pipelineId))
            .thenReturn(Uni.createFrom().item(mockConfig));

        given()
            .when()
            .get("/api/v1/pipelines/definitions/" + pipelineId)
            .then()
            .statusCode(200)
            .body("name", equalTo("Test Pipeline"))
            .body("pipelineSteps", notNullValue());
    }

    @Test
    void testGetNonExistentDefinition() {
        String pipelineId = "non-existent-pipeline";

        when(pipelineDefinitionService.getDefinition(pipelineId))
            .thenReturn(Uni.createFrom().nullItem());

        given()
            .when()
            .get("/api/v1/pipelines/definitions/" + pipelineId)
            .then()
            .statusCode(404)
            .body("error", equalTo("Pipeline definition not found"));
    }

    @Test
    void testCreateDefinition() {
        String pipelineId = "new-pipeline";

        // Create a pipeline config to send
        Map<String, Object> pipelineConfig = Map.of(
            "name", "New Pipeline",
            "description", "A new pipeline definition",
            "version", "1.0.0",
            "steps", List.of()
        );

        ValidationResult successResult = new ValidationResult(true, List.of(), List.of());

        when(pipelineDefinitionService.createDefinition(
            org.mockito.ArgumentMatchers.eq(pipelineId),
            org.mockito.ArgumentMatchers.any(PipelineConfig.class)
        )).thenReturn(Uni.createFrom().item(successResult));

        given()
            .contentType(ContentType.JSON)
            .body(pipelineConfig)
            .when()
            .post("/api/v1/pipelines/definitions/" + pipelineId)
            .then()
            .statusCode(201)
            .body("success", is(true))
            .body("message", equalTo("Pipeline definition created successfully"));
    }

    @Test
    void testCreateDuplicateDefinition() {
        String pipelineId = "existing-pipeline";

        Map<String, Object> pipelineConfig = Map.of(
            "name", "Existing Pipeline",
            "description", "An existing pipeline",
            "version", "1.0.0"
        );

        ValidationResult failureResult = new ValidationResult(
            false, 
            List.of("Pipeline definition already exists"), 
            List.of()
        );

        when(pipelineDefinitionService.createDefinition(
            org.mockito.ArgumentMatchers.eq(pipelineId),
            org.mockito.ArgumentMatchers.any(PipelineConfig.class)
        )).thenReturn(Uni.createFrom().item(failureResult));

        given()
            .contentType(ContentType.JSON)
            .body(pipelineConfig)
            .when()
            .post("/api/v1/pipelines/definitions/" + pipelineId)
            .then()
            .statusCode(409)
            .body("success", is(false))
            .body("message", equalTo("Validation failed"))
            .body("errors[0]", equalTo("Pipeline definition already exists"));
    }

    @Test
    void testUpdateDefinition() {
        String pipelineId = "update-pipeline";

        Map<String, Object> pipelineConfig = Map.of(
            "name", "Updated Pipeline",
            "description", "An updated pipeline definition",
            "version", "2.0.0"
        );

        ValidationResult successResult = new ValidationResult(true, List.of(), List.of());

        when(pipelineDefinitionService.updateDefinition(
            org.mockito.ArgumentMatchers.eq(pipelineId),
            org.mockito.ArgumentMatchers.any(PipelineConfig.class)
        )).thenReturn(Uni.createFrom().item(successResult));

        given()
            .contentType(ContentType.JSON)
            .body(pipelineConfig)
            .when()
            .put("/api/v1/pipelines/definitions/" + pipelineId)
            .then()
            .statusCode(200)
            .body("success", is(true))
            .body("message", equalTo("Pipeline definition updated successfully"));
    }

    @Test
    void testUpdateNonExistentDefinition() {
        String pipelineId = "non-existent-pipeline";

        Map<String, Object> pipelineConfig = Map.of(
            "name", "Updated Pipeline",
            "version", "2.0.0"
        );

        ValidationResult failureResult = new ValidationResult(
            false, 
            List.of("Pipeline definition not found"), 
            List.of()
        );

        when(pipelineDefinitionService.updateDefinition(
            org.mockito.ArgumentMatchers.eq(pipelineId),
            org.mockito.ArgumentMatchers.any(PipelineConfig.class)
        )).thenReturn(Uni.createFrom().item(failureResult));

        given()
            .contentType(ContentType.JSON)
            .body(pipelineConfig)
            .when()
            .put("/api/v1/pipelines/definitions/" + pipelineId)
            .then()
            .statusCode(404)
            .body("success", is(false))
            .body("message", equalTo("Update failed"))
            .body("errors[0]", equalTo("Pipeline definition not found"));
    }

    @Test
    void testDeleteDefinition() {
        String pipelineId = "delete-pipeline";

        ValidationResult successResult = new ValidationResult(true, List.of(), List.of());

        when(pipelineDefinitionService.deleteDefinition(pipelineId))
            .thenReturn(Uni.createFrom().item(successResult));

        given()
            .when()
            .delete("/api/v1/pipelines/definitions/" + pipelineId)
            .then()
            .statusCode(204);
    }

    @Test
    void testDeleteNonExistentDefinition() {
        String pipelineId = "non-existent-pipeline";

        ValidationResult failureResult = new ValidationResult(
            false, 
            List.of("Pipeline definition not found"), 
            List.of()
        );

        when(pipelineDefinitionService.deleteDefinition(pipelineId))
            .thenReturn(Uni.createFrom().item(failureResult));

        given()
            .when()
            .delete("/api/v1/pipelines/definitions/" + pipelineId)
            .then()
            .statusCode(404)
            .body("success", is(false))
            .body("message", equalTo("Pipeline definition not found"))
            .body("errors[0]", equalTo("Pipeline definition not found"));
    }

    @Test
    void testDeleteDefinitionWithActiveInstances() {
        String pipelineId = "active-pipeline";

        ValidationResult failureResult = new ValidationResult(
            false, 
            List.of("Cannot delete: pipeline has active instances"), 
            List.of()
        );

        when(pipelineDefinitionService.deleteDefinition(pipelineId))
            .thenReturn(Uni.createFrom().item(failureResult));

        given()
            .when()
            .delete("/api/v1/pipelines/definitions/" + pipelineId)
            .then()
            .statusCode(409)
            .body("success", is(false))
            .body("message", equalTo("Cannot delete pipeline with active instances"))
            .body("errors[0]", equalTo("Cannot delete: pipeline has active instances"));
    }

    // Tests for the new DTO endpoint
    @Test
    void testCreatePipelineFromDTO() {
        // Given: A module is registered
        ModuleRegistration mockModule = new ModuleRegistration(
                "test-module-id",      // moduleId
                "test-module",         // moduleName
                "impl-1",              // implementationId
                "test-hostname",       // host
                8080,                  // port
                "PIPELINE",            // serviceType
                "1.0.0",               // version
                Map.of(),              // metadata
                System.currentTimeMillis(), // registeredAt
                "localhost",           // engineHost
                18080,                 // enginePort
                null,                  // jsonSchema
                true,                  // enabled
                null,                  // containerId
                null,                  // containerName
                "test-hostname"        // hostname
        );
        when(moduleRegistryService.getModule("test-module"))
                .thenReturn(Uni.createFrom().item(mockModule));

        // And: Pipeline creation will succeed
        ValidationResult successResult = new ValidationResult(true, List.of(), List.of());
        when(pipelineDefinitionService.createDefinition(
                org.mockito.ArgumentMatchers.anyString(), 
                org.mockito.ArgumentMatchers.any(PipelineConfig.class)))
                .thenReturn(Uni.createFrom().item(successResult));

        // When: We POST a pipeline definition using the new DTO endpoint
        given()
                .contentType(ContentType.JSON)
                .body("""
                    {
                        "name": "test-pipeline",
                        "description": "Test pipeline for DTO endpoint",
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
                    """)
                .when()
                .post("/api/v1/pipelines/definitions")
                .then()
                .statusCode(201)
                .body("success", is(true))
                .body("message", equalTo("Pipeline definition created successfully."));

        // Then: Verify the pipeline was created with correct transformation
        ArgumentCaptor<PipelineConfig> configCaptor = ArgumentCaptor.forClass(PipelineConfig.class);
        verify(pipelineDefinitionService).createDefinition(
                org.mockito.ArgumentMatchers.eq("test-pipeline"), 
                configCaptor.capture());

        PipelineConfig capturedConfig = configCaptor.getValue();
        assert capturedConfig.name().equals("test-pipeline");
        assert capturedConfig.pipelineSteps().containsKey("step1");
        assert capturedConfig.pipelineSteps().get("step1").processorInfo().grpcServiceName().equals("test-module");
    }

    @Test
    void testCreatePipelineFromDTO_MissingName() {
        // When: We POST without a name
        given()
                .contentType(ContentType.JSON)
                .body("""
                    {
                        "description": "Test pipeline",
                        "steps": []
                    }
                    """)
                .when()
                .post("/api/v1/pipelines/definitions")
                .then()
                .statusCode(400)
                .body("success", is(false))
                .body("message", containsString("Pipeline name is required"));
    }

    @Test
    void testCreatePipelineFromDTO_ModuleNotFound() {
        // Given: Module is not registered
        when(moduleRegistryService.getModule("nonexistent-module"))
                .thenReturn(Uni.createFrom().nullItem());

        // When: We POST with a non-existent module
        given()
                .contentType(ContentType.JSON)
                .body("""
                    {
                        "name": "test-pipeline",
                        "description": "Test pipeline",
                        "steps": [
                            {
                                "name": "step1",
                                "module": "nonexistent-module",
                                "config": {}
                            }
                        ]
                    }
                    """)
                .when()
                .post("/api/v1/pipelines/definitions")
                .then()
                .statusCode(400)
                .body("message", containsString("Module nonexistent-module not found"));
    }

    @Test
    void testCreatePipelineFromDTO_PipelineAlreadyExists() {
        // Given: Module exists
        ModuleRegistration mockModule = new ModuleRegistration(
                "test-module-id",      // moduleId
                "test-module",         // moduleName
                "impl-1",              // implementationId
                "test-hostname",       // host
                8080,                  // port
                "PIPELINE",            // serviceType
                "1.0.0",               // version
                Map.of(),              // metadata
                System.currentTimeMillis(), // registeredAt
                "localhost",           // engineHost
                18080,                 // enginePort
                null,                  // jsonSchema
                true,                  // enabled
                null,                  // containerId
                null,                  // containerName
                "test-hostname"        // hostname
        );
        when(moduleRegistryService.getModule("test-module"))
                .thenReturn(Uni.createFrom().item(mockModule));

        // And: Pipeline already exists
        ValidationResult conflictResult = new ValidationResult(
                false, 
                List.of("Pipeline 'test-pipeline' already exists"),
                List.of()
        );
        when(pipelineDefinitionService.createDefinition(
                org.mockito.ArgumentMatchers.anyString(), 
                org.mockito.ArgumentMatchers.any(PipelineConfig.class)))
                .thenReturn(Uni.createFrom().item(conflictResult));

        // When: We try to create a duplicate
        given()
                .contentType(ContentType.JSON)
                .body("""
                    {
                        "name": "test-pipeline",
                        "description": "Duplicate pipeline",
                        "steps": [
                            {
                                "name": "step1",
                                "module": "test-module",
                                "config": {}
                            }
                        ]
                    }
                    """)
                .when()
                .post("/api/v1/pipelines/definitions")
                .then()
                .statusCode(409)
                .body("success", is(false))
                .body("errors", hasItem(containsString("already exists")));
    }
}
