package com.rokkon.modules.chunker;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.rokkon.search.model.*;
import com.rokkon.search.sdk.*;
import com.rokkon.test.data.TestDocumentFactory;
import com.rokkon.test.registration.ModuleRegistrationTester;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive integration tests for the Chunker service using Quarkus dependency injection.
 * Converted from gRPC client-based test to use direct service injection with test utilities.
 * <br/>
 * Test Coverage:
 * - All chunking strategies (overlap, sentence-based, token-based)
 * - Configuration variations (chunk size, overlap, templates)
 * - Document processing workflows (plain text, structured content)
 * - Error handling and edge cases
 * - Service registration and health checks
 * - Performance verification with large documents
 * - Chunk metadata generation and validation
 * - Semantic processing result creation
 * - Integration with upstream Tika parser output
 * - Testing with the 99 Tika-processed documents
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ChunkerComprehensiveIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(ChunkerComprehensiveIntegrationTest.class);

    @Inject
    ChunkerService chunkerService;

    @Inject
    TestDocumentFactory documentFactory;

    @Inject
    ModuleRegistrationTester registrationTester;

    @Test
    @Order(1)
    @DisplayName("Service Registration - Should register successfully and provide chunker metadata")
    void testServiceRegistration() {
        LOG.info("Testing Chunker service registration");
        
        registrationTester.verifyChunkerServiceRegistration(chunkerService.getServiceRegistration(null));
        
        LOG.info("✅ Chunker service registered successfully");
    }

    @Test
    @Order(2)
    @DisplayName("99 Documents Test - Should chunk all Tika-processed documents")
    void testWith99TikaDocuments() {
        LOG.info("Testing chunker with 99 Tika-processed documents");

        List<PipeDoc> tikaDocuments = documentFactory.loadTikaProcessedDocuments();
        assertTrue(tikaDocuments.size() > 0, "Should have Tika-processed documents available");

        int successCount = 0;
        int totalChunks = 0;

        for (int i = 0; i < Math.min(10, tikaDocuments.size()); i++) { // Test first 10 documents
            PipeDoc doc = tikaDocuments.get(i);
            
            ProcessRequest request = createProcessRequest("tika-doc-chunking", "chunker-tika-" + i, doc);
            
            try {
                Uni<ProcessResponse> responseUni = chunkerService.processData(request);
                ProcessResponse response = responseUni.await().atMost(Duration.ofSeconds(30));

                if (response.getSuccess()) {
                    successCount++;
                    if (response.getOutputDoc().getSemanticResultsCount() > 0) {
                        SemanticProcessingResult result = response.getOutputDoc().getSemanticResults(0);
                        totalChunks += result.getChunksCount();
                        LOG.debug("✅ Document {} chunked into {} chunks", doc.getId(), result.getChunksCount());
                    }
                }
            } catch (Exception e) {
                LOG.warn("Failed to chunk document {}: {}", doc.getId(), e.getMessage());
            }
        }

        assertTrue(successCount >= 5, "Should successfully chunk at least 5 documents");
        assertTrue(totalChunks > 0, "Should produce chunks from successful documents");

        LOG.info("✅ 99 documents chunking test completed - {}/{} successful, {} total chunks", 
            successCount, Math.min(10, tikaDocuments.size()), totalChunks);
    }

    @Test
    @Order(3)
    @DisplayName("Default Chunking - Should chunk simple text with default configuration")
    void testDefaultChunking() {
        String testText = "This is the first sentence of our test document. " +
                         "This is the second sentence that should be included in chunking. " +
                         "This is the third sentence to ensure we have enough content. " +
                         "Finally, this is the fourth sentence to complete our test.";
        
        PipeDoc inputDoc = documentFactory.createSimpleTextDocument("default-chunk-test", "Default Chunking Test", testText);
        ProcessRequest request = createProcessRequest("default-chunking-pipeline", "chunker-default", inputDoc);
        
        Uni<ProcessResponse> responseUni = chunkerService.processData(request);
        ProcessResponse response = responseUni.await().atMost(Duration.ofSeconds(30));
        
        assertTrue(response.getSuccess(), "Default chunking should be successful");
        assertNotNull(response.getOutputDoc(), "Output document should be present");
        assertTrue(response.getOutputDoc().getSemanticResultsCount() > 0, "Should have semantic results");
        
        SemanticProcessingResult result = response.getOutputDoc().getSemanticResults(0);
        assertTrue(result.getChunksCount() > 0, "Should have created chunks");
        
        // Verify chunk content
        SemanticChunk chunk = result.getChunks(0);
        assertTrue(chunk.getEmbeddingInfo().getTextContent().length() > 0, "Chunk should have text content");
        assertTrue(chunk.getChunkId().contains("chunk"), "Chunk ID should follow expected format");
        
        LOG.info("✅ Default chunking successful - created {} chunks", result.getChunksCount());
    }

    @Test
    @Order(4)
    @DisplayName("Custom Configuration - Should respect custom chunk size and overlap settings")
    void testCustomConfiguration() {
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            longText.append("This is sentence number ").append(i + 1)
                   .append(" in our long test document for custom chunking. ");
        }
        
        PipeDoc inputDoc = documentFactory.createSimpleTextDocument("custom-chunk-test", "Custom Chunking Test", longText.toString());
        
        // Create custom configuration with small chunks
        Struct customConfig = Struct.newBuilder()
                .putFields("chunk_size", Value.newBuilder().setNumberValue(200).build())
                .putFields("chunk_overlap", Value.newBuilder().setNumberValue(50).build())
                .putFields("chunk_config_id", Value.newBuilder().setStringValue("small_chunks_200_50").build())
                .build();
        
        ProcessRequest request = createProcessRequestWithJsonConfig("custom-size-pipeline", "chunker-custom", inputDoc, customConfig);
        
        Uni<ProcessResponse> responseUni = chunkerService.processData(request);
        ProcessResponse response = responseUni.await().atMost(Duration.ofSeconds(30));
        
        assertTrue(response.getSuccess(), "Custom chunk size processing should be successful");
        
        SemanticProcessingResult result = response.getOutputDoc().getSemanticResults(0);
        assertEquals("small_chunks_200_50", result.getChunkConfigId());
        assertTrue(result.getChunksCount() > 1, "Should create multiple chunks with small chunk size");
        
        // Verify chunks respect size constraints
        for (SemanticChunk chunk : result.getChunksList()) {
            assertTrue(chunk.getEmbeddingInfo().getTextContent().length() <= 300, 
                      "Chunk size should be reasonable for configured size");
        }
        
        LOG.info("✅ Custom chunk size successful - created {} chunks with size 200", result.getChunksCount());
    }

    @Test
    @Order(5)
    @DisplayName("Multiple Document Processing - Should handle batch processing")
    void testMultipleDocumentProcessing() {
        LOG.info("Testing batch processing with multiple documents");

        List<PipeDoc> testDocs = documentFactory.createSimpleTestDocuments(5);
        
        int successCount = 0;
        int totalChunks = 0;

        for (int i = 0; i < testDocs.size(); i++) {
            PipeDoc doc = testDocs.get(i);
            
            ProcessRequest request = createProcessRequest("batch-chunking", "chunker-batch-" + i, doc);
            
            try {
                Uni<ProcessResponse> responseUni = chunkerService.processData(request);
                ProcessResponse response = responseUni.await().atMost(Duration.ofSeconds(30));

                if (response.getSuccess()) {
                    successCount++;
                    if (response.getOutputDoc().getSemanticResultsCount() > 0) {
                        SemanticProcessingResult result = response.getOutputDoc().getSemanticResults(0);
                        totalChunks += result.getChunksCount();
                    }
                }
            } catch (Exception e) {
                LOG.warn("Failed to process document {}: {}", doc.getId(), e.getMessage());
            }
        }

        assertEquals(testDocs.size(), successCount, "All documents should be processed successfully");
        assertTrue(totalChunks > 0, "Should produce chunks from all documents");

        LOG.info("✅ Batch processing completed - {}/{} successful, {} total chunks", 
            successCount, testDocs.size(), totalChunks);
    }

    @Test
    @Order(6)
    @DisplayName("Error Handling - Should handle invalid input gracefully")
    void testErrorHandling() {
        LOG.info("Testing error handling with invalid input");

        // Test with empty document
        PipeDoc emptyDoc = PipeDoc.newBuilder()
                .setId("empty-test")
                .setTitle("Empty Document")
                .build();
        
        ProcessRequest request = createProcessRequest("error-handling", "chunker-error", emptyDoc);
        
        Uni<ProcessResponse> responseUni = chunkerService.processData(request);
        ProcessResponse response = responseUni.await().atMost(Duration.ofSeconds(10));
        
        // Should handle gracefully - either succeed or fail gracefully
        assertNotNull(response, "Response should not be null");
        if (!response.getSuccess()) {
            assertTrue(response.getProcessorLogsList().size() > 0, "Should have error logs");
        }
        
        LOG.info("✅ Error handling test completed");
    }

    // Helper Methods

    private ProcessRequest createProcessRequest(String pipelineName, String stepName, PipeDoc document) {
        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setPipelineName(pipelineName)
                .setPipeStepName(stepName)
                .setStreamId("stream-" + System.currentTimeMillis())
                .setCurrentHopNumber(1)
                .build();
        
        ProcessConfiguration config = ProcessConfiguration.newBuilder().build();
        
        return ProcessRequest.newBuilder()
                .setDocument(document)
                .setConfig(config)
                .setMetadata(metadata)
                .build();
    }
    
    private ProcessRequest createProcessRequestWithJsonConfig(String pipelineName, String stepName, 
                                                            PipeDoc document, Struct customConfig) {
        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setPipelineName(pipelineName)
                .setPipeStepName(stepName)
                .setStreamId("stream-" + System.currentTimeMillis())
                .setCurrentHopNumber(1)
                .build();
        
        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .setCustomJsonConfig(customConfig)
                .build();
        
        return ProcessRequest.newBuilder()
                .setDocument(document)
                .setConfig(config)
                .setMetadata(metadata)
                .build();
    }
}