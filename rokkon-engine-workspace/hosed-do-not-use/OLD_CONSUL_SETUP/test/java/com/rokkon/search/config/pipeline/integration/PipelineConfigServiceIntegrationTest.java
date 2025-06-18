package com.rokkon.search.config.pipeline.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokkon.search.config.pipeline.model.*;
import com.rokkon.search.config.pipeline.service.PipelineConfigService;
import com.rokkon.search.config.pipeline.service.events.ConfigChangeEvent;
import com.rokkon.search.config.pipeline.service.events.ConfigChangeType;
import com.rokkon.search.config.pipeline.service.validation.ValidationResult;
import com.rokkon.search.config.pipeline.consul.ConsulResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.*;

import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive integration test for the PipelineConfigService.
 * Tests the full stack from service layer down to Consul storage.
 */
@QuarkusTest
@QuarkusTestResource(ConsulResource.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PipelineConfigServiceIntegrationTest {
    
    @Inject
    PipelineConfigService pipelineConfigService;
    
    @Inject
    ObjectMapper objectMapper;
    
    private static final String TEST_CLUSTER = "integration-test-cluster";
    private static final String TEST_PIPELINE_ID = "test-document-processing";
    private static final String TEST_USER = "integration-test-user";
    
    // Event capturing for testing
    private final List<ConfigChangeEvent> capturedEvents = new CopyOnWriteArrayList<>();
    
    void onConfigChange(@Observes ConfigChangeEvent event) {
        capturedEvents.add(event);
        System.out.println("Captured event: " + event.changeType() + " for pipeline: " + event.pipelineId());
    }
    
    @BeforeEach
    void setUp() {
        capturedEvents.clear();
    }
    
    @Test
    @Order(1)
    @DisplayName("Should create a valid pipeline configuration")
    void testCreateValidPipeline() throws Exception {
        // Given: A valid pipeline configuration
        PipelineConfig config = createValidPipelineConfig();
        
        // When: Creating the pipeline
        ValidationResult result = pipelineConfigService.createPipeline(
                TEST_CLUSTER, TEST_PIPELINE_ID, config, TEST_USER
        ).toCompletableFuture().get(10, TimeUnit.SECONDS);
        
        // Then: Pipeline should be created successfully
        assertTrue(result.valid(), "Pipeline creation should succeed: " + result.errors());
        assertTrue(result.errors().isEmpty(), "Should have no errors");
        
        // And: Event should be fired
        assertEquals(1, capturedEvents.size(), "Should have fired one event");
        ConfigChangeEvent event = capturedEvents.get(0);
        assertEquals(ConfigChangeType.PIPELINE_CREATED, event.changeType());
        assertEquals(TEST_CLUSTER, event.clusterName());
        assertEquals(TEST_PIPELINE_ID, event.pipelineId());
        assertEquals(TEST_USER, event.initiatedBy());
        assertNotNull(event.newConfig());
        assertNull(event.oldConfig());
        
        System.out.println("✅ Pipeline created successfully with event fired");
    }
    
    @Test
    @Order(2)
    @DisplayName("Should retrieve the created pipeline")
    void testRetrievePipeline() throws Exception {
        // When: Retrieving the pipeline
        var optionalConfig = pipelineConfigService.getPipeline(TEST_CLUSTER, TEST_PIPELINE_ID)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);
        
        // Then: Pipeline should be found
        assertTrue(optionalConfig.isPresent(), "Pipeline should be found");
        
        PipelineConfig config = optionalConfig.get();
        assertEquals("test-document-processing", config.name());
        assertEquals(2, config.pipelineSteps().size());
        assertTrue(config.pipelineSteps().containsKey("chunker-step"));
        assertTrue(config.pipelineSteps().containsKey("embedder-step"));
        
        // Verify step details
        PipelineStepConfig chunkerStep = config.pipelineSteps().get("chunker-step");
        assertEquals(StepType.INITIAL_PIPELINE, chunkerStep.stepType());
        assertEquals("chunker-service", chunkerStep.processorInfo().grpcServiceName());
        
        PipelineStepConfig embedderStep = config.pipelineSteps().get("embedder-step");
        assertEquals(StepType.SINK, embedderStep.stepType());
        assertEquals("embedder-service", embedderStep.processorInfo().grpcServiceName());
        
        System.out.println("✅ Pipeline retrieved successfully with correct structure");
    }
    
    @Test
    @Order(3)
    @DisplayName("Should list pipelines in cluster")
    void testListPipelines() throws Exception {
        // When: Listing pipelines
        Map<String, PipelineConfig> pipelines = pipelineConfigService.listPipelines(TEST_CLUSTER)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);
        
        // Then: Should contain our test pipeline
        assertEquals(1, pipelines.size(), "Should have exactly one pipeline");
        assertTrue(pipelines.containsKey(TEST_PIPELINE_ID), "Should contain our test pipeline");
        
        PipelineConfig config = pipelines.get(TEST_PIPELINE_ID);
        assertEquals("test-document-processing", config.name());
        
        System.out.println("✅ Pipeline listing works correctly");
    }
    
    @Test
    @Order(4)
    @DisplayName("Should update pipeline configuration")
    void testUpdatePipeline() throws Exception {
        // Given: An updated pipeline configuration
        PipelineConfig updatedConfig = createUpdatedPipelineConfig();
        capturedEvents.clear(); // Clear previous events
        
        // When: Updating the pipeline
        ValidationResult result = pipelineConfigService.updatePipeline(
                TEST_CLUSTER, TEST_PIPELINE_ID, updatedConfig, TEST_USER
        ).toCompletableFuture().get(10, TimeUnit.SECONDS);
        
        // Then: Update should succeed
        assertTrue(result.valid(), "Pipeline update should succeed: " + result.errors());
        
        // And: Event should be fired
        assertEquals(1, capturedEvents.size(), "Should have fired one update event");
        ConfigChangeEvent event = capturedEvents.get(0);
        assertEquals(ConfigChangeType.PIPELINE_UPDATED, event.changeType());
        assertEquals(TEST_CLUSTER, event.clusterName());
        assertEquals(TEST_PIPELINE_ID, event.pipelineId());
        assertNotNull(event.newConfig());
        assertNotNull(event.oldConfig());
        
        // Verify the update persisted
        var retrievedConfig = pipelineConfigService.getPipeline(TEST_CLUSTER, TEST_PIPELINE_ID)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertTrue(retrievedConfig.isPresent());
        assertEquals("Updated Description", retrievedConfig.get().pipelineSteps().get("chunker-step").description());
        
        System.out.println("✅ Pipeline updated successfully with event fired");
    }
    
    @Test
    @Order(5)
    @DisplayName("Should analyze deletion impact")
    void testDeletionImpactAnalysis() throws Exception {
        // When: Analyzing deletion impact
        var analysis = pipelineConfigService.analyzeDeletionImpact(
                TEST_CLUSTER, TEST_PIPELINE_ID, PipelineConfigService.DependencyType.PIPELINE
        ).toCompletableFuture().get(5, TimeUnit.SECONDS);
        
        // Then: Analysis should be complete
        assertEquals(TEST_PIPELINE_ID, analysis.targetId());
        assertEquals(PipelineConfigService.DependencyType.PIPELINE, analysis.targetType());
        assertTrue(analysis.canSafelyDelete(), "Should be safe to delete");
        assertTrue(analysis.affectedPipelines().isEmpty(), "No other pipelines should be affected");
        
        System.out.println("✅ Deletion impact analysis works correctly");
    }
    
    @Test
    @Order(6)
    @DisplayName("Should delete pipeline")
    void testDeletePipeline() throws Exception {
        // Given: Clear events
        capturedEvents.clear();
        
        // When: Deleting the pipeline
        ValidationResult result = pipelineConfigService.deletePipeline(
                TEST_CLUSTER, TEST_PIPELINE_ID, TEST_USER
        ).toCompletableFuture().get(10, TimeUnit.SECONDS);
        
        // Then: Deletion should succeed
        assertTrue(result.valid(), "Pipeline deletion should succeed: " + result.errors());
        
        // And: Event should be fired
        assertEquals(1, capturedEvents.size(), "Should have fired one deletion event");
        ConfigChangeEvent event = capturedEvents.get(0);
        assertEquals(ConfigChangeType.PIPELINE_DELETED, event.changeType());
        assertEquals(TEST_CLUSTER, event.clusterName());
        assertEquals(TEST_PIPELINE_ID, event.pipelineId());
        assertNull(event.newConfig());
        assertNotNull(event.oldConfig());
        
        // Verify the pipeline is gone
        var retrievedConfig = pipelineConfigService.getPipeline(TEST_CLUSTER, TEST_PIPELINE_ID)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertTrue(retrievedConfig.isEmpty(), "Pipeline should be deleted");
        
        // Verify list is empty
        Map<String, PipelineConfig> pipelines = pipelineConfigService.listPipelines(TEST_CLUSTER)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertTrue(pipelines.isEmpty(), "Cluster should have no pipelines");
        
        System.out.println("✅ Pipeline deleted successfully with event fired");
    }
    
    @Test
    @Order(7)
    @DisplayName("Should handle validation errors for invalid pipeline")
    void testValidationErrors() throws Exception {
        // Given: An invalid pipeline configuration
        PipelineConfig invalidConfig = createInvalidPipelineConfig();
        
        // When: Attempting to create invalid pipeline
        ValidationResult result = pipelineConfigService.createPipeline(
                TEST_CLUSTER, "invalid-pipeline", invalidConfig, TEST_USER
        ).toCompletableFuture().get(10, TimeUnit.SECONDS);
        
        // Then: Should fail validation
        assertFalse(result.valid(), "Invalid pipeline should fail validation");
        assertFalse(result.errors().isEmpty(), "Should have validation errors");
        
        // Should contain specific validation errors
        assertTrue(result.errors().stream().anyMatch(error -> 
                error.contains("Step name is required")), 
                "Should have step name validation error");
        
        // And: No events should be fired for failed creation
        assertTrue(capturedEvents.isEmpty(), "No events should be fired for failed validation");
        
        System.out.println("✅ Validation errors handled correctly: " + result.errors());
    }
    
    @Test
    @Order(8)
    @DisplayName("Should handle concurrent operations safely")
    void testConcurrentOperations() throws Exception {
        // Given: Multiple pipeline configurations
        String pipeline1 = "concurrent-test-1";
        String pipeline2 = "concurrent-test-2";
        PipelineConfig config1 = createValidPipelineConfig();
        PipelineConfig config2 = createValidPipelineConfig();
        
        // When: Creating pipelines concurrently
        CompletableFuture<ValidationResult> future1 = pipelineConfigService.createPipeline(
                TEST_CLUSTER, pipeline1, config1, TEST_USER).toCompletableFuture();
        CompletableFuture<ValidationResult> future2 = pipelineConfigService.createPipeline(
                TEST_CLUSTER, pipeline2, config2, TEST_USER).toCompletableFuture();
        
        ValidationResult result1 = future1.get(10, TimeUnit.SECONDS);
        ValidationResult result2 = future2.get(10, TimeUnit.SECONDS);
        
        // Then: Both should succeed
        assertTrue(result1.valid(), "First pipeline should be created successfully");
        assertTrue(result2.valid(), "Second pipeline should be created successfully");
        
        // And: Both should be retrievable
        var retrieved1 = pipelineConfigService.getPipeline(TEST_CLUSTER, pipeline1)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);
        var retrieved2 = pipelineConfigService.getPipeline(TEST_CLUSTER, pipeline2)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);
        
        assertTrue(retrieved1.isPresent(), "First pipeline should be retrievable");
        assertTrue(retrieved2.isPresent(), "Second pipeline should be retrievable");
        
        // Cleanup
        pipelineConfigService.deletePipeline(TEST_CLUSTER, pipeline1, TEST_USER)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);
        pipelineConfigService.deletePipeline(TEST_CLUSTER, pipeline2, TEST_USER)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);
        
        System.out.println("✅ Concurrent operations handled safely");
    }
    
    @Test
    @Order(9)
    @DisplayName("Should handle not found scenarios gracefully")
    void testNotFoundScenarios() throws Exception {
        // When: Retrieving non-existent pipeline
        var retrieveResult = pipelineConfigService.getPipeline(TEST_CLUSTER, "non-existent")
                .toCompletableFuture().get(5, TimeUnit.SECONDS);
        
        // Then: Should return empty optional
        assertTrue(retrieveResult.isEmpty(), "Non-existent pipeline should return empty");
        
        // When: Updating non-existent pipeline
        ValidationResult updateResult = pipelineConfigService.updatePipeline(
                TEST_CLUSTER, "non-existent", createValidPipelineConfig(), TEST_USER
        ).toCompletableFuture().get(5, TimeUnit.SECONDS);
        
        // Then: Should fail with not found
        assertFalse(updateResult.valid(), "Update of non-existent pipeline should fail");
        assertTrue(updateResult.errors().stream().anyMatch(error -> 
                error.contains("not found")), "Should have 'not found' error");
        
        // When: Deleting non-existent pipeline
        ValidationResult deleteResult = pipelineConfigService.deletePipeline(
                TEST_CLUSTER, "non-existent", TEST_USER
        ).toCompletableFuture().get(5, TimeUnit.SECONDS);
        
        // Then: Should fail with not found
        assertFalse(deleteResult.valid(), "Delete of non-existent pipeline should fail");
        assertTrue(deleteResult.errors().stream().anyMatch(error -> 
                error.contains("not found")), "Should have 'not found' error");
        
        System.out.println("✅ Not found scenarios handled gracefully");
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
        
        return new PipelineConfig("test-document-processing", Map.of(
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
                "Updated Description", // Changed description
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
        // Create a step that passes constructor validation but fails business validation
        PipelineStepConfig invalidStep = new PipelineStepConfig(
                "", // Empty step name (passes null check but fails business validation)
                StepType.PIPELINE, // Valid step type
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
                null // Missing processor info (will fail business validation)
        );
        
        return new PipelineConfig("invalid-pipeline", Map.of(
                "invalid-step", invalidStep
        ));
    }
}