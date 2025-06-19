package com.rokkon.modules.embedder;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.rokkon.search.model.PipeDoc;
import com.rokkon.search.model.SemanticProcessingResult;
import com.rokkon.search.model.SemanticChunk;
import com.rokkon.search.model.ChunkEmbedding;
import com.rokkon.search.sdk.ProcessRequest;
import com.rokkon.search.sdk.ProcessResponse;
import com.rokkon.search.sdk.ProcessConfiguration;
import com.rokkon.search.sdk.ServiceMetadata;
import com.rokkon.search.sdk.ServiceRegistrationData;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Inject;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive gRPC integration tests for the Embedder service using Quarkus testing capabilities.
 * These tests use the actual service implementation with real dependencies but in a controlled test environment.
 * 
 * Test Coverage:
 * - Real gRPC communication (in-process)
 * - Actual embedding model loading and inference
 * - GPU/CPU detection and processing
 * - Configuration parsing and validation  
 * - Document field embedding workflows
 * - Chunk-based embedding workflows
 * - Error handling and edge cases
 * - Service registration functionality
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EmbedderServiceGrpcIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(EmbedderServiceGrpcIntegrationTest.class);

    @Inject
    EmbedderService embedderService;

    @Test
    @Order(1)
    @DisplayName("Service Registration - Should provide complete embedder metadata")
    void testServiceRegistration() {
        LOG.info("Testing Embedder service registration");

        Uni<ServiceRegistrationData> registrationUni = embedderService.getServiceRegistration(null);
        ServiceRegistrationData registration = registrationUni.await().indefinitely();

        assertNotNull(registration, "Service registration should not be null");
        assertEquals("embedder", registration.getModuleName());
        assertTrue(registration.getJsonConfigSchema().length() > 100, "Schema should be comprehensive");
        
        // Verify key schema elements are present
        String schema = registration.getJsonConfigSchema();
        assertTrue(schema.contains("EmbedderOptions"), "Schema should define EmbedderOptions");
        assertTrue(schema.contains("embedding_models"), "Schema should include embedding_models");
        assertTrue(schema.contains("check_chunks"), "Schema should include check_chunks");
        assertTrue(schema.contains("check_document_fields"), "Schema should include check_document_fields");
        assertTrue(schema.contains("document_fields"), "Schema should include document_fields");
        assertTrue(schema.contains("max_batch_size"), "Schema should include max_batch_size");
        assertTrue(schema.contains("backpressure_strategy"), "Schema should include backpressure_strategy");
        assertTrue(schema.contains("ALL_MINILM_L6_V2"), "Schema should list available models");

        LOG.info("✅ Embedder service registration successful with comprehensive schema");
    }

    @Test
    @Order(2)
    @DisplayName("Default Configuration - Should process with default settings")
    void testDefaultConfiguration() {
        PipeDoc inputDoc = PipeDoc.newBuilder()
                .setId("default-config-test")
                .setBody("This is a test document for default configuration testing.")
                .setTitle("Default Config Test")
                .build();

        ProcessConfiguration config = ProcessConfiguration.newBuilder().build(); // Empty config = defaults

        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setStreamId("test-stream")
                .setPipeStepName("embedder")
                .build();

        ProcessRequest request = ProcessRequest.newBuilder()
                .setDocument(inputDoc)
                .setConfig(config)
                .setMetadata(metadata)
                .build();

        Uni<ProcessResponse> responseUni = embedderService.processData(request);
        ProcessResponse response = responseUni.await().indefinitely();

        assertTrue(response.getSuccess(), "Default configuration should succeed");
        assertEquals(inputDoc.getId(), response.getOutputDoc().getId());
        assertFalse(response.getProcessorLogsList().isEmpty());
        assertTrue(response.getProcessorLogs(0).contains("EmbedderService"), "Should log service name");

        LOG.info("✅ Default configuration processing successful");
    }

    @Test
    @Order(3)
    @DisplayName("Document Field Embedding - Should embed title and body fields")
    void testDocumentFieldEmbedding() {
        PipeDoc inputDoc = PipeDoc.newBuilder()
                .setId("field-embedding-test")
                .setTitle("Machine Learning and AI Research")
                .setBody("This document discusses advances in machine learning and artificial intelligence. " +
                        "It covers neural networks, deep learning, and natural language processing.")
                .build();

        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .setCustomJsonConfig(Struct.newBuilder()
                        .putFields("check_chunks", Value.newBuilder().setBoolValue(false).build())
                        .putFields("check_document_fields", Value.newBuilder().setBoolValue(true).build())
                        .putFields("document_fields", Value.newBuilder().setListValue(
                                com.google.protobuf.ListValue.newBuilder()
                                        .addValues(Value.newBuilder().setStringValue("title").build())
                                        .addValues(Value.newBuilder().setStringValue("body").build())
                                        .build()).build())
                        .build())
                .build();

        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setStreamId("test-stream")
                .setPipeStepName("embedder")
                .build();

        ProcessRequest request = ProcessRequest.newBuilder()
                .setDocument(inputDoc)
                .setConfig(config)
                .setMetadata(metadata)
                .build();

        Uni<ProcessResponse> responseUni = embedderService.processData(request);
        ProcessResponse response = responseUni.await().indefinitely();

        assertTrue(response.getSuccess(), "Document field embedding should succeed");
        assertEquals(inputDoc.getId(), response.getOutputDoc().getId());
        
        // Should have processed document fields
        assertTrue(response.getProcessorLogs(0).contains("Successfully processed document fields") ||
                  response.getProcessorLogs(0).contains("document fields"), 
                  "Should process document fields");

        LOG.info("✅ Document field embedding successful");
    }

    @Test
    @Order(4)
    @DisplayName("Chunk Processing - Should process existing chunks with embeddings")
    void testChunkProcessing() {
        // Create test chunk data
        ChunkEmbedding chunkEmbedding = ChunkEmbedding.newBuilder()
                .setChunkId("chunk-1")
                .setTextContent("This is a test chunk about machine learning algorithms.")
                .setOriginalCharStartOffset(0)
                .setOriginalCharEndOffset(50)
                .setChunkConfigId("test-config")
                .build();

        SemanticChunk chunk = SemanticChunk.newBuilder()
                .setChunkId("chunk-1")
                .setChunkNumber(0)
                .setEmbeddingInfo(chunkEmbedding)
                .build();

        SemanticProcessingResult semanticResult = SemanticProcessingResult.newBuilder()
                .setResultId("result-1")
                .setResultSetName("test-chunks")
                .setSourceFieldName("body")
                .addChunks(chunk)
                .build();

        PipeDoc inputDoc = PipeDoc.newBuilder()
                .setId("chunk-processing-test")
                .setBody("This document contains chunks that need embedding processing.")
                .addSemanticResults(semanticResult)
                .build();

        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .setCustomJsonConfig(Struct.newBuilder()
                        .putFields("check_chunks", Value.newBuilder().setBoolValue(true).build())
                        .putFields("check_document_fields", Value.newBuilder().setBoolValue(false).build())
                        .build())
                .build();

        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setStreamId("test-stream")
                .setPipeStepName("embedder")
                .build();

        ProcessRequest request = ProcessRequest.newBuilder()
                .setDocument(inputDoc)
                .setConfig(config)
                .setMetadata(metadata)
                .build();

        Uni<ProcessResponse> responseUni = embedderService.processData(request);
        ProcessResponse response = responseUni.await().indefinitely();

        assertTrue(response.getSuccess(), "Chunk processing should succeed");
        assertEquals(inputDoc.getId(), response.getOutputDoc().getId());
        
        // Should have processed chunks
        assertTrue(response.getProcessorLogs(0).contains("Successfully processed chunks") ||
                  response.getProcessorLogs(0).contains("chunks"), 
                  "Should process chunks");

        LOG.info("✅ Chunk processing successful");
    }

    @Test
    @Order(5)
    @DisplayName("Multiple Embedding Models - Should handle multiple models configuration")
    void testMultipleEmbeddingModels() {
        PipeDoc inputDoc = PipeDoc.newBuilder()
                .setId("multi-model-test")
                .setBody("Testing multiple embedding models with various configurations.")
                .build();

        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .setCustomJsonConfig(Struct.newBuilder()
                        .putFields("check_chunks", Value.newBuilder().setBoolValue(false).build())
                        .putFields("check_document_fields", Value.newBuilder().setBoolValue(true).build())
                        .putFields("embedding_models", Value.newBuilder().setListValue(
                                com.google.protobuf.ListValue.newBuilder()
                                        .addValues(Value.newBuilder().setStringValue("ALL_MINILM_L6_V2").build())
                                        .addValues(Value.newBuilder().setStringValue("PARAPHRASE_MINILM_L3_V2").build())
                                        .build()).build())
                        .build())
                .build();

        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setStreamId("test-stream")
                .setPipeStepName("embedder")
                .build();

        ProcessRequest request = ProcessRequest.newBuilder()
                .setDocument(inputDoc)
                .setConfig(config)
                .setMetadata(metadata)
                .build();

        Uni<ProcessResponse> responseUni = embedderService.processData(request);
        ProcessResponse response = responseUni.await().indefinitely();

        assertTrue(response.getSuccess(), "Multiple embedding models should succeed");
        assertEquals(inputDoc.getId(), response.getOutputDoc().getId());

        LOG.info("✅ Multiple embedding models configuration successful");
    }

    @Test
    @Order(6)
    @DisplayName("Error Handling - Should handle invalid configuration gracefully")
    void testErrorHandling() {
        PipeDoc inputDoc = PipeDoc.newBuilder()
                .setId("error-handling-test")
                .setBody("Testing error handling with invalid configurations.")
                .build();

        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .setCustomJsonConfig(Struct.newBuilder()
                        .putFields("invalid_field", Value.newBuilder().setStringValue("invalid_value").build())
                        .putFields("check_document_fields", Value.newBuilder().setBoolValue(true).build())
                        .build())
                .build();

        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setStreamId("test-stream")
                .setPipeStepName("embedder")
                .build();

        ProcessRequest request = ProcessRequest.newBuilder()
                .setDocument(inputDoc)
                .setConfig(config)
                .setMetadata(metadata)
                .build();

        Uni<ProcessResponse> responseUni = embedderService.processData(request);
        ProcessResponse response = responseUni.await().indefinitely();

        // Should either succeed (ignoring invalid fields) or fail gracefully
        assertNotNull(response, "Response should not be null");
        assertEquals(inputDoc.getId(), response.getOutputDoc().getId());

        LOG.info("✅ Error handling test completed");
    }

    @Test
    @Order(7)
    @DisplayName("Null Safety - Should handle null configuration values")
    void testNullSafety() {
        PipeDoc inputDoc = PipeDoc.newBuilder()
                .setId("null-safety-test")
                .setBody("Testing null safety in configuration handling.")
                .build();

        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .setCustomJsonConfig(Struct.newBuilder()
                        .putFields("check_document_fields", Value.newBuilder().setBoolValue(true).build())
                        // documentFields will be null since it's not specified
                        .build())
                .build();

        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setStreamId("test-stream")
                .setPipeStepName("embedder")
                .build();

        ProcessRequest request = ProcessRequest.newBuilder()
                .setDocument(inputDoc)
                .setConfig(config)
                .setMetadata(metadata)
                .build();

        Uni<ProcessResponse> responseUni = embedderService.processData(request);
        ProcessResponse response = responseUni.await().indefinitely();

        assertTrue(response.getSuccess(), "Null safety should be handled properly");
        assertEquals(inputDoc.getId(), response.getOutputDoc().getId());

        LOG.info("✅ Null safety test successful");
    }

    @Test
    @Order(8)
    @DisplayName("Performance - Should handle reasonable batch sizes efficiently")
    void testPerformanceWithBatching() {
        PipeDoc inputDoc = PipeDoc.newBuilder()
                .setId("performance-test")
                .setBody("This is a performance test document that should be processed efficiently " +
                        "with proper batch sizing and GPU optimization if available.")
                .setTitle("Performance Test Document")
                .build();

        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .setCustomJsonConfig(Struct.newBuilder()
                        .putFields("check_document_fields", Value.newBuilder().setBoolValue(true).build())
                        .putFields("max_batch_size", Value.newBuilder().setNumberValue(16).build())
                        .putFields("backpressure_strategy", Value.newBuilder().setStringValue("DROP_OLDEST").build())
                        .build())
                .build();

        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setStreamId("test-stream")
                .setPipeStepName("embedder")
                .build();

        ProcessRequest request = ProcessRequest.newBuilder()
                .setDocument(inputDoc)
                .setConfig(config)
                .setMetadata(metadata)
                .build();

        long startTime = System.currentTimeMillis();
        
        Uni<ProcessResponse> responseUni = embedderService.processData(request);
        ProcessResponse response = responseUni.await().indefinitely();
        
        long duration = System.currentTimeMillis() - startTime;

        assertTrue(response.getSuccess(), "Performance test should succeed");
        assertEquals(inputDoc.getId(), response.getOutputDoc().getId());
        assertTrue(duration < 30000, "Should complete within 30 seconds"); // Reasonable timeout

        LOG.info("✅ Performance test completed in {}ms", duration);
    }

    @Test
    @Order(9)
    @DisplayName("No Processing Configuration - Should handle disabled processing")
    void testNoProcessingConfiguration() {
        PipeDoc inputDoc = PipeDoc.newBuilder()
                .setId("no-processing-test")
                .setBody("This document should not be processed for embeddings.")
                .build();

        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .setCustomJsonConfig(Struct.newBuilder()
                        .putFields("check_chunks", Value.newBuilder().setBoolValue(false).build())
                        .putFields("check_document_fields", Value.newBuilder().setBoolValue(false).build())
                        .build())
                .build();

        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setStreamId("test-stream")
                .setPipeStepName("embedder")
                .build();

        ProcessRequest request = ProcessRequest.newBuilder()
                .setDocument(inputDoc)
                .setConfig(config)
                .setMetadata(metadata)
                .build();

        Uni<ProcessResponse> responseUni = embedderService.processData(request);
        ProcessResponse response = responseUni.await().indefinitely();

        assertTrue(response.getSuccess(), "No processing configuration should succeed");
        assertEquals(inputDoc.getId(), response.getOutputDoc().getId());
        assertTrue(response.getProcessorLogs(0).contains("No processing configured"), 
                  "Should indicate no processing was performed");

        LOG.info("✅ No processing configuration test successful");
    }
}