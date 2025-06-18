package com.rokkon.search.config.pipeline.integration;

import com.rokkon.search.config.pipeline.model.*;
import com.rokkon.search.config.pipeline.consul.ConsulResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Full REST API integration tests from HTTP requests to Consul storage.
 * Tests the complete stack including validation, Swagger docs, and error handling.
 */
@QuarkusTest
@QuarkusTestResource(ConsulResource.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PipelineConfigRestApiIntegrationTest {
    
    private static final String API_BASE = "/api/v1/clusters/api-test-cluster/pipelines";
    private static final String VALIDATION_BASE = "/api/v1/validation";
    private static final String TEST_PIPELINE_ID = "api-test-pipeline";
    
    @Test
    @Order(1)
    @DisplayName("Should create pipeline via REST API")
    void testCreatePipelineAPI() {
        PipelineConfig config = createValidPipelineConfig();
        
        given()
            .contentType(ContentType.JSON)
            .header("X-Initiated-By", "api-integration-test")
            .body(config)
        .when()
            .post(API_BASE + "/" + TEST_PIPELINE_ID)
        .then()
            .statusCode(201)
            .body("message", equalTo("Pipeline created successfully"))
            .body("data.clusterName", equalTo("api-test-cluster"))
            .body("data.pipelineId", equalTo(TEST_PIPELINE_ID))
            .body("data.warnings", hasSize(0));
        
        System.out.println("✅ REST API pipeline creation successful");
    }
    
    @Test
    @Order(2)
    @DisplayName("Should retrieve pipeline via REST API")
    void testGetPipelineAPI() {
        given()
        .when()
            .get(API_BASE + "/" + TEST_PIPELINE_ID)
        .then()
            .statusCode(200)
            .body("name", equalTo("api-test-pipeline"))
            .body("pipelineSteps", hasKey("chunker-step"))
            .body("pipelineSteps", hasKey("embedder-step"))
            .body("pipelineSteps.size()", equalTo(2))
            .body("pipelineSteps.'chunker-step'.stepType", equalTo("INITIAL_PIPELINE"))
            .body("pipelineSteps.'embedder-step'.stepType", equalTo("SINK"));
        
        System.out.println("✅ REST API pipeline retrieval successful");
    }
    
    @Test
    @Order(3)
    @DisplayName("Should list pipelines via REST API")
    void testListPipelinesAPI() {
        given()
        .when()
            .get(API_BASE)
        .then()
            .statusCode(200)
            .body("$", hasKey(TEST_PIPELINE_ID))
            .body("'" + TEST_PIPELINE_ID + "'.name", equalTo("api-test-pipeline"));
        
        System.out.println("✅ REST API pipeline listing successful");
    }
    
    @Test
    @Order(4)
    @DisplayName("Should update pipeline via REST API")
    void testUpdatePipelineAPI() {
        PipelineConfig updatedConfig = createUpdatedPipelineConfig();
        
        given()
            .contentType(ContentType.JSON)
            .header("X-Initiated-By", "api-integration-test")
            .body(updatedConfig)
        .when()
            .put(API_BASE + "/" + TEST_PIPELINE_ID)
        .then()
            .statusCode(200)
            .body("message", equalTo("Pipeline updated successfully"))
            .body("data.clusterName", equalTo("api-test-cluster"))
            .body("data.pipelineId", equalTo(TEST_PIPELINE_ID));
        
        // Verify update persisted
        given()
        .when()
            .get(API_BASE + "/" + TEST_PIPELINE_ID)
        .then()
            .statusCode(200)
            .body("pipelineSteps.'chunker-step'.description", equalTo("Updated via API"));
        
        System.out.println("✅ REST API pipeline update successful");
    }
    
    @Test
    @Order(5)
    @DisplayName("Should analyze deletion impact via REST API")
    void testDeletionImpactAnalysisAPI() {
        given()
        .when()
            .get(API_BASE + "/" + TEST_PIPELINE_ID + "/impact-analysis")
        .then()
            .statusCode(200)
            .body("targetId", equalTo(TEST_PIPELINE_ID))
            .body("targetType", equalTo("PIPELINE"))
            .body("canSafelyDelete", equalTo(true))
            .body("affectedPipelines", hasSize(0));
        
        System.out.println("✅ REST API impact analysis successful");
    }
    
    @Test
    @Order(6)
    @DisplayName("Should validate pipeline configuration via REST API")
    void testValidationAPI() {
        PipelineConfig config = createValidPipelineConfig();
        
        // Test valid pipeline validation
        given()
            .contentType(ContentType.JSON)
            .queryParam("pipelineId", "validation-test")
            .body(config)
        .when()
            .post(VALIDATION_BASE + "/pipeline")
        .then()
            .statusCode(200)
            .body("valid", equalTo(true))
            .body("errors", hasSize(0))
            .body("validationType", equalTo("STRUCTURAL_VALIDATION"));
        
        // Test invalid pipeline validation
        PipelineConfig invalidConfig = createInvalidPipelineConfig();
        given()
            .contentType(ContentType.JSON)
            .queryParam("pipelineId", "validation-test")
            .body(invalidConfig)
        .when()
            .post(VALIDATION_BASE + "/pipeline")
        .then()
            .statusCode(200)
            .body("valid", equalTo(false))
            .body("errors", not(empty()))
            .body("validationType", equalTo("STRUCTURAL_VALIDATION"));
        
        System.out.println("✅ REST API validation successful");
    }
    
    @Test
    @Order(7)
    @DisplayName("Should validate service names via REST API")
    void testServiceNameValidationAPI() {
        // Test valid service name
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("serviceName", "my-search-service"))
        .when()
            .post(VALIDATION_BASE + "/service-name")
        .then()
            .statusCode(200)
            .body("valid", equalTo(true))
            .body("errors", hasSize(0));
        
        // Test invalid service name
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("serviceName", "Invalid-Service-Name!"))
        .when()
            .post(VALIDATION_BASE + "/service-name")
        .then()
            .statusCode(200)
            .body("valid", equalTo(false))
            .body("errors", not(empty()));
        
        System.out.println("✅ REST API service name validation successful");
    }
    
    @Test
    @Order(8)
    @DisplayName("Should validate Kafka topic names via REST API")
    void testKafkaTopicValidationAPI() {
        // Test valid topic name
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("topicName", "processed-documents"))
        .when()
            .post(VALIDATION_BASE + "/kafka-topic")
        .then()
            .statusCode(200)
            .body("valid", equalTo(true))
            .body("errors", hasSize(0));
        
        // Test invalid topic name
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("topicName", ".."))
        .when()
            .post(VALIDATION_BASE + "/kafka-topic")
        .then()
            .statusCode(200)
            .body("valid", equalTo(false))
            .body("errors", not(empty()));
        
        System.out.println("✅ REST API Kafka topic validation successful");
    }
    
    @Test
    @Order(9)
    @DisplayName("Should validate JSON syntax via REST API")
    void testJsonValidationAPI() {
        // Test valid JSON
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("jsonContent", "{\"test\": \"value\"}"))
        .when()
            .post(VALIDATION_BASE + "/json")
        .then()
            .statusCode(200)
            .body("valid", equalTo(true));
        
        // Test invalid JSON
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("jsonContent", "{invalid json"))
        .when()
            .post(VALIDATION_BASE + "/json")
        .then()
            .statusCode(200)
            .body("valid", equalTo(false))
            .body("errors", hasItem("Invalid JSON syntax"));
        
        System.out.println("✅ REST API JSON validation successful");
    }
    
    @Test
    @Order(10)
    @DisplayName("Should get JSON schemas via REST API")
    void testGetSchemasAPI() {
        // Test pipeline cluster schema
        given()
        .when()
            .get(VALIDATION_BASE + "/schemas/pipeline-cluster")
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body(containsString("$schema"))
            .body(containsString("clusterName"));
        
        // Test pipeline step schema
        given()
        .when()
            .get(VALIDATION_BASE + "/schemas/pipeline-step")
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body(containsString("$schema"))
            .body(containsString("stepName"));
        
        System.out.println("✅ REST API schema retrieval successful");
    }
    
    @Test
    @Order(11)
    @DisplayName("Should handle API error scenarios correctly")
    void testAPIErrorHandling() {
        // Test creating pipeline with validation errors
        PipelineConfig invalidConfig = createInvalidPipelineConfig();
        
        given()
            .contentType(ContentType.JSON)
            .header("X-Initiated-By", "api-integration-test")
            .body(invalidConfig)
        .when()
            .post(API_BASE + "/invalid-pipeline")
        .then()
            .statusCode(400)
            .body("message", equalTo("Validation failed"))
            .body("errorCode", equalTo("VALIDATION_ERROR"))
            .body("errors", not(empty()));
        
        // Test retrieving non-existent pipeline
        given()
        .when()
            .get(API_BASE + "/non-existent-pipeline")
        .then()
            .statusCode(404)
            .body("message", equalTo("Pipeline not found"))
            .body("errorCode", equalTo("NOT_FOUND"));
        
        // Test updating non-existent pipeline
        PipelineConfig config = createValidPipelineConfig();
        given()
            .contentType(ContentType.JSON)
            .header("X-Initiated-By", "api-integration-test")
            .body(config)
        .when()
            .put(API_BASE + "/non-existent-pipeline")
        .then()
            .statusCode(404)
            .body("message", equalTo("Pipeline not found"))
            .body("errorCode", equalTo("NOT_FOUND"));
        
        // Test deleting non-existent pipeline
        given()
            .header("X-Initiated-By", "api-integration-test")
        .when()
            .delete(API_BASE + "/non-existent-pipeline")
        .then()
            .statusCode(404)
            .body("message", equalTo("Pipeline not found"))
            .body("errorCode", equalTo("NOT_FOUND"));
        
        System.out.println("✅ REST API error handling successful");
    }
    
    @Test
    @Order(12)
    @DisplayName("Should delete pipeline via REST API")
    void testDeletePipelineAPI() {
        given()
            .header("X-Initiated-By", "api-integration-test")
        .when()
            .delete(API_BASE + "/" + TEST_PIPELINE_ID)
        .then()
            .statusCode(200)
            .body("message", equalTo("Pipeline deleted successfully"))
            .body("data.clusterName", equalTo("api-test-cluster"))
            .body("data.pipelineId", equalTo(TEST_PIPELINE_ID));
        
        // Verify pipeline is gone
        given()
        .when()
            .get(API_BASE + "/" + TEST_PIPELINE_ID)
        .then()
            .statusCode(404);
        
        // Verify list is empty
        given()
        .when()
            .get(API_BASE)
        .then()
            .statusCode(200)
            .body("$", anEmptyMap());
        
        System.out.println("✅ REST API pipeline deletion successful");
    }
    
    @Test
    @Order(13)
    @DisplayName("Should test OpenAPI/Swagger documentation is available")
    void testSwaggerDocumentation() {
        // Test that OpenAPI spec is available
        given()
        .when()
            .get("/q/openapi")
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("openapi", not(emptyString()))
            .body("info.title", not(emptyString()))
            .body("paths", hasKey("/api/v1/clusters/{clusterName}/pipelines"))
            .body("paths", hasKey("/api/v1/validation/pipeline"));
        
        // Test that Swagger UI is available (if enabled)
        given()
        .when()
            .get("/q/swagger-ui")
        .then()
            .statusCode(anyOf(is(200), is(404))); // 404 is OK if Swagger UI is disabled in tests
        
        System.out.println("✅ OpenAPI documentation available");
    }
    
    // Helper methods
    
    private PipelineConfig createValidPipelineConfig() {
        // Create chunker step (initial pipeline step)
        PipelineStepConfig.ProcessorInfo chunkerProcessor = new PipelineStepConfig.ProcessorInfo(
                "chunker-service", null);
        
        GrpcTransportConfig grpcTransport = new GrpcTransportConfig(
                "embedder-service", Map.of("timeout", "30s"));
        
        PipelineStepConfig.OutputTarget chunkerOutput = new PipelineStepConfig.OutputTarget(
                "embedder-step", TransportType.GRPC, grpcTransport, null);
        
        PipelineStepConfig chunkerStep = new PipelineStepConfig(
                "chunker-step", StepType.INITIAL_PIPELINE, "Document chunking step",
                "chunker-schema", null, List.of(),
                Map.of("primary", chunkerOutput), 3, 1000L, 30000L, 2.0, 25000L,
                chunkerProcessor);
        
        // Create embedder step (sink step)
        PipelineStepConfig.ProcessorInfo embedderProcessor = new PipelineStepConfig.ProcessorInfo(
                "embedder-service", null);
        
        PipelineStepConfig embedderStep = new PipelineStepConfig(
                "embedder-step", StepType.SINK, "Embedding generation step",
                "embedder-schema", null, List.of(), Map.of(), 2, 2000L, 45000L, 1.5, 60000L,
                embedderProcessor);
        
        return new PipelineConfig("api-test-pipeline", Map.of(
                "chunker-step", chunkerStep,
                "embedder-step", embedderStep
        ));
    }
    
    private PipelineConfig createUpdatedPipelineConfig() {
        PipelineConfig originalConfig = createValidPipelineConfig();
        
        // Update the chunker step description
        PipelineStepConfig originalChunker = originalConfig.pipelineSteps().get("chunker-step");
        PipelineStepConfig updatedChunker = new PipelineStepConfig(
                originalChunker.stepName(),
                originalChunker.stepType(),
                "Updated via API", // Changed description
                originalChunker.customConfigSchemaId(),
                originalChunker.customConfig(),
                originalChunker.kafkaInputs(),
                originalChunker.outputs(),
                originalChunker.maxRetries(),
                originalChunker.retryBackoffMs(),
                originalChunker.maxRetryBackoffMs(),
                originalChunker.retryBackoffMultiplier(),
                originalChunker.stepTimeoutMs(),
                originalChunker.processorInfo()
        );
        
        return new PipelineConfig(originalConfig.name(), Map.of(
                "chunker-step", updatedChunker,
                "embedder-step", originalConfig.pipelineSteps().get("embedder-step")
        ));
    }
    
    private PipelineConfig createInvalidPipelineConfig() {
        // Create a step with missing required fields
        PipelineStepConfig invalidStep = new PipelineStepConfig(
                null, // Missing step name
                null, // Missing step type
                "Invalid step",
                null,
                null,
                List.of(),
                Map.of(),
                null,
                null,
                null,
                null,
                null,
                null // Missing processor info
        );
        
        return new PipelineConfig("invalid-pipeline", Map.of(
                "invalid-step", invalidStep
        ));
    }
}