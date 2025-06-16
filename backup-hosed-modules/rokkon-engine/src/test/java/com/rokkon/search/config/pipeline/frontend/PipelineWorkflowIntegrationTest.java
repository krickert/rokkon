package com.rokkon.search.config.pipeline.frontend;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Frontend workflow integration tests - simulating actual user workflows
 * through the API that a frontend application would perform.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Disabled("Requires comprehensive pipeline infrastructure, validation services, and gRPC services to be running")
public class PipelineWorkflowIntegrationTest {
    
    private static final String CLUSTER = "frontend-test";
    
    @Test
    @Order(1)
    @DisplayName("Frontend Workflow: Create complete document processing pipeline")
    void testCreateDocumentProcessingPipeline() {
        // Step 1: Validate service names before creating pipeline
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("serviceName", "chunker-service"))
        .when()
            .post("/api/v1/validation/service-name")
        .then()
            .statusCode(200)
            .body("valid", equalTo(true));
        
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("serviceName", "embedder-service"))
        .when()
            .post("/api/v1/validation/service-name")
        .then()
            .statusCode(200)
            .body("valid", equalTo(true));
        
        // Step 2: Validate topic names
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("topicName", "document-chunks"))
        .when()
            .post("/api/v1/validation/kafka-topic")
        .then()
            .statusCode(200)
            .body("valid", equalTo(true));
        
        // Step 3: Create the pipeline
        Map<String, Object> pipeline = createDocumentProcessingPipeline();
        
        given()
            .contentType(ContentType.JSON)
            .header("X-Initiated-By", "frontend-user")
            .body(pipeline)
        .when()
            .post("/api/v1/clusters/" + CLUSTER + "/pipelines/document-processing")
        .then()
            .statusCode(201)
            .body("message", equalTo("Pipeline created successfully"));
        
        System.out.println("✅ Document processing pipeline created via frontend workflow");
    }
    
    @Test
    @Order(2)
    @DisplayName("Frontend Workflow: Create AI pipeline with dependencies")
    void testCreateAIPipeline() {
        Map<String, Object> aiPipeline = createAIPipeline();
        
        given()
            .contentType(ContentType.JSON)
            .header("X-Initiated-By", "frontend-user")
            .body(aiPipeline)
        .when()
            .post("/api/v1/clusters/" + CLUSTER + "/pipelines/ai-processing")
        .then()
            .statusCode(201)
            .body("message", equalTo("Pipeline created successfully"));
        
        System.out.println("✅ AI processing pipeline created");
    }
    
    @Test
    @Order(3)
    @DisplayName("Frontend Workflow: List and verify all pipelines")
    void testListPipelines() {
        given()
        .when()
            .get("/api/v1/clusters/" + CLUSTER + "/pipelines")
        .then()
            .statusCode(200)
            .body("$", hasKey("document-processing"))
            .body("$", hasKey("ai-processing"))
            .body("'document-processing'.name", equalTo("Document Processing Pipeline"))
            .body("'ai-processing'.name", equalTo("AI Processing Pipeline"));
        
        System.out.println("✅ Pipeline listing verified");
    }
    
    @Test
    @Order(4)
    @DisplayName("Frontend Workflow: Update pipeline with validation")
    void testUpdatePipelineWorkflow() {
        // Step 1: Get current pipeline
        var currentPipeline = given()
        .when()
            .get("/api/v1/clusters/" + CLUSTER + "/pipelines/document-processing")
        .then()
            .statusCode(200)
            .extract().jsonPath().getMap("$");
        
        // Step 2: Validate changes before applying
        @SuppressWarnings({"unchecked", "rawtypes"})
        Map<String, Object> currentPipelineTyped = (Map) currentPipeline;
        Map<String, Object> updatedPipeline = createUpdatedPipeline(currentPipelineTyped);
        
        given()
            .contentType(ContentType.JSON)
            .queryParam("pipelineId", "document-processing")
            .body(updatedPipeline)
        .when()
            .post("/api/v1/validation/pipeline")
        .then()
            .statusCode(200)
            .body("valid", equalTo(true));
        
        // Step 3: Apply the update
        given()
            .contentType(ContentType.JSON)
            .header("X-Initiated-By", "frontend-user")
            .body(updatedPipeline)
        .when()
            .put("/api/v1/clusters/" + CLUSTER + "/pipelines/document-processing")
        .then()
            .statusCode(200)
            .body("message", equalTo("Pipeline updated successfully"));
        
        // Step 4: Verify the update
        given()
        .when()
            .get("/api/v1/clusters/" + CLUSTER + "/pipelines/document-processing")
        .then()
            .statusCode(200)
            .body("pipelineSteps.'chunker-step'.description", equalTo("Updated chunker step"));
        
        System.out.println("✅ Pipeline update workflow completed");
    }
    
    @Test
    @Order(5)
    @DisplayName("Frontend Workflow: Impact analysis before deletion")
    void testDeletionWorkflow() {
        // Step 1: Analyze impact of deleting document-processing pipeline
        given()
        .when()
            .get("/api/v1/clusters/" + CLUSTER + "/pipelines/document-processing/impact-analysis")
        .then()
            .statusCode(200)
            .body("targetId", equalTo("document-processing"))
            .body("targetType", equalTo("PIPELINE"))
            .body("canSafelyDelete", equalTo(true))
            .body("affectedPipelines", hasSize(0));
        
        System.out.println("✅ Impact analysis shows safe to delete");
        
        // Step 2: Delete the pipeline
        given()
            .header("X-Initiated-By", "frontend-user")
        .when()
            .delete("/api/v1/clusters/" + CLUSTER + "/pipelines/document-processing")
        .then()
            .statusCode(200)
            .body("message", equalTo("Pipeline deleted successfully"));
        
        // Step 3: Verify it's gone
        given()
        .when()
            .get("/api/v1/clusters/" + CLUSTER + "/pipelines/document-processing")
        .then()
            .statusCode(404);
        
        System.out.println("✅ Pipeline deletion workflow completed");
    }
    
    @Test
    @Order(6)
    @DisplayName("Frontend Workflow: Error handling scenarios")
    void testErrorHandlingWorkflows() {
        // Test 1: Invalid pipeline creation
        Map<String, Object> invalidPipeline = Map.of(
            "name", "",  // Invalid empty name
            "pipelineSteps", Map.of()  // No steps
        );
        
        given()
            .contentType(ContentType.JSON)
            .header("X-Initiated-By", "frontend-user")
            .body(invalidPipeline)
        .when()
            .post("/api/v1/clusters/" + CLUSTER + "/pipelines/invalid-pipeline")
        .then()
            .statusCode(400)
            .body("errorCode", equalTo("VALIDATION_ERROR"))
            .body("errors", not(empty()));
        
        // Test 2: Invalid service name
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("serviceName", "Invalid_Service!"))
        .when()
            .post("/api/v1/validation/service-name")
        .then()
            .statusCode(200)
            .body("valid", equalTo(false))
            .body("errors", not(empty()));
        
        // Test 3: Invalid topic name
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("topicName", ".."))
        .when()
            .post("/api/v1/validation/kafka-topic")
        .then()
            .statusCode(200)
            .body("valid", equalTo(false))
            .body("errors", hasItem(containsString("cannot be")));
        
        System.out.println("✅ Error handling workflows verified");
    }
    
    @Test
    @Order(7)
    @DisplayName("Frontend Workflow: Schema retrieval for forms")
    void testSchemaRetrievalForForms() {
        // Frontend would use these schemas to build dynamic forms
        
        // Get pipeline cluster schema
        given()
        .when()
            .get("/api/v1/validation/schemas/pipeline-cluster")
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body(containsString("clusterName"))
            .body(containsString("$schema"));
        
        // Get pipeline step schema
        given()
        .when()
            .get("/api/v1/validation/schemas/pipeline-step")
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body(containsString("stepName"))
            .body(containsString("stepType"));
        
        System.out.println("✅ Schema retrieval for dynamic forms verified");
    }
    
    @Test
    @Order(8)
    @DisplayName("Frontend Workflow: Clean up remaining resources")
    void testCleanup() {
        // Delete remaining pipeline
        given()
            .header("X-Initiated-By", "frontend-user")
        .when()
            .delete("/api/v1/clusters/" + CLUSTER + "/pipelines/ai-processing")
        .then()
            .statusCode(200);
        
        // Verify cluster is empty
        given()
        .when()
            .get("/api/v1/clusters/" + CLUSTER + "/pipelines")
        .then()
            .statusCode(200)
            .body("$", anEmptyMap());
        
        System.out.println("✅ Cleanup completed");
    }
    
    // Helper methods for creating test data
    
    private Map<String, Object> createDocumentProcessingPipeline() {
        return Map.ofEntries(
            Map.entry("name", "Document Processing Pipeline"),
            Map.entry("pipelineSteps", Map.of(
                "chunker-step", Map.ofEntries(
                    Map.entry("stepName", "chunker-step"),
                    Map.entry("stepType", "INITIAL_PIPELINE"),
                    Map.entry("description", "Document chunking step"),
                    Map.entry("customConfigSchemaId", "chunker-schema"),
                    Map.entry("kafkaInputs", List.of()),
                    Map.entry("outputs", Map.of(
                        "primary", Map.of(
                            "targetStepName", "embedder-step",
                            "transportType", "GRPC",
                            "grpcTransport", Map.of(
                                "serviceName", "embedder-service",
                                "grpcClientProperties", Map.of("timeout", "30s")
                            )
                        )
                    )),
                    Map.entry("maxRetries", 3),
                    Map.entry("retryBackoffMs", 1000),
                    Map.entry("maxRetryBackoffMs", 30000),
                    Map.entry("retryBackoffMultiplier", 2.0),
                    Map.entry("stepTimeoutMs", 25000),
                    Map.entry("processorInfo", Map.of(
                        "grpcServiceName", "chunker-service"
                    ))
                ),
                "embedder-step", Map.ofEntries(
                    Map.entry("stepName", "embedder-step"),
                    Map.entry("stepType", "SINK"),
                    Map.entry("description", "Embedding generation step"),
                    Map.entry("customConfigSchemaId", "embedder-schema"),
                    Map.entry("kafkaInputs", List.of()),
                    Map.entry("outputs", Map.of()),
                    Map.entry("maxRetries", 2),
                    Map.entry("retryBackoffMs", 2000),
                    Map.entry("maxRetryBackoffMs", 45000),
                    Map.entry("retryBackoffMultiplier", 1.5),
                    Map.entry("stepTimeoutMs", 60000),
                    Map.entry("processorInfo", Map.of(
                        "grpcServiceName", "embedder-service"
                    ))
                )
            ))
        );
    }
    
    private Map<String, Object> createAIPipeline() {
        return Map.of(
            "name", "AI Processing Pipeline",
            "pipelineSteps", Map.of(
                "ai-processor", Map.ofEntries(
                    Map.entry("stepName", "ai-processor"),
                    Map.entry("stepType", "INITIAL_PIPELINE"),
                    Map.entry("description", "AI processing step"),
                    Map.entry("customConfigSchemaId", "ai-schema"),
                    Map.entry("kafkaInputs", List.of()),
                    Map.entry("outputs", Map.of(
                        "primary", Map.of(
                            "targetStepName", "results-sink",
                            "transportType", "KAFKA",
                            "kafkaTransport", Map.of(
                                "topic", "ai-results",
                                "producerProperties", Map.of("batch.size", "16384")
                            )
                        )
                    )),
                    Map.entry("maxRetries", 3),
                    Map.entry("retryBackoffMs", 1000),
                    Map.entry("maxRetryBackoffMs", 30000),
                    Map.entry("retryBackoffMultiplier", 2.0),
                    Map.entry("stepTimeoutMs", 120000),
                    Map.entry("processorInfo", Map.of(
                        "grpcServiceName", "ai-service"
                    ))
                ),
                "results-sink", Map.ofEntries(
                    Map.entry("stepName", "results-sink"),
                    Map.entry("stepType", "SINK"),
                    Map.entry("description", "Results storage step"),
                    Map.entry("customConfigSchemaId", "sink-schema"),
                    Map.entry("kafkaInputs", List.of(
                        Map.of(
                            "listenTopics", List.of("ai-results"),
                            "consumerGroupId", "results-processor",
                            "kafkaConsumerProperties", Map.of("max.poll.records", "100")
                        )
                    )),
                    Map.entry("outputs", Map.of()),
                    Map.entry("maxRetries", 5),
                    Map.entry("retryBackoffMs", 3000),
                    Map.entry("maxRetryBackoffMs", 60000),
                    Map.entry("retryBackoffMultiplier", 1.8),
                    Map.entry("stepTimeoutMs", 90000),
                    Map.entry("processorInfo", Map.of(
                        "grpcServiceName", "storage-service"
                    ))
                )
            )
        );
    }
    
    private Map<String, Object> createUpdatedPipeline(Map<String, Object> original) {
        @SuppressWarnings("unchecked")
        Map<String, Object> pipelineSteps = (Map<String, Object>) original.get("pipelineSteps");
        @SuppressWarnings("unchecked")
        Map<String, Object> chunkerStep = (Map<String, Object>) pipelineSteps.get("chunker-step");
        
        // Create a mutable copy and update the description
        Map<String, Object> updatedChunkerStep = new java.util.HashMap<>(chunkerStep);
        updatedChunkerStep.put("description", "Updated chunker step");
        
        Map<String, Object> updatedPipelineSteps = new java.util.HashMap<>(pipelineSteps);
        updatedPipelineSteps.put("chunker-step", updatedChunkerStep);
        
        return Map.of(
            "name", original.get("name"),
            "pipelineSteps", updatedPipelineSteps
        );
    }
}