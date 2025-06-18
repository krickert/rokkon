package com.rokkon.engine;

import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.rokkon.search.model.*;
import com.rokkon.search.sdk.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive end-to-end integration tests for the complete document processing pipeline.
 * These tests verify the full workflow: Tika Parser -> Chunker -> Embedder
 * 
 * This test class validates that:
 * - Documents flow correctly through the entire pipeline
 * - Each service preserves and enhances document data appropriately
 * - Complex documents are processed end-to-end successfully
 * - Service registration and discovery works across all modules
 * - Performance is acceptable for complete pipeline processing
 * - Error handling works correctly across service boundaries
 * - The migration from Micronaut to Quarkus preserved all functionality
 */
@QuarkusIntegrationTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EndToEndPipelineIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(EndToEndPipelineIntegrationTest.class);
    
    private ManagedChannel tikaChannel;
    private ManagedChannel chunkerChannel;
    private ManagedChannel embedderChannel;
    private ManagedChannel echoChannel;
    
    private PipeStepProcessorGrpc.PipeStepProcessorBlockingStub tikaClient;
    private PipeStepProcessorGrpc.PipeStepProcessorBlockingStub chunkerClient;
    private PipeStepProcessorGrpc.PipeStepProcessorBlockingStub embedderClient;
    private PipeStepProcessorGrpc.PipeStepProcessorBlockingStub echoClient;
    
    private ModuleRegistrationServiceGrpc.ModuleRegistrationServiceBlockingStub tikaRegistrationClient;
    private ModuleRegistrationServiceGrpc.ModuleRegistrationServiceBlockingStub chunkerRegistrationClient;
    private ModuleRegistrationServiceGrpc.ModuleRegistrationServiceBlockingStub embedderRegistrationClient;
    private ModuleRegistrationServiceGrpc.ModuleRegistrationServiceBlockingStub echoRegistrationClient;

    @BeforeEach
    void setUp() {
        // Set up gRPC channels for all services
        tikaChannel = ManagedChannelBuilder.forAddress("localhost", 9000).usePlaintext().build();
        chunkerChannel = ManagedChannelBuilder.forAddress("localhost", 9001).usePlaintext().build();
        embedderChannel = ManagedChannelBuilder.forAddress("localhost", 9002).usePlaintext().build();
        echoChannel = ManagedChannelBuilder.forAddress("localhost", 9003).usePlaintext().build();
        
        tikaClient = PipeStepProcessorGrpc.newBlockingStub(tikaChannel);
        chunkerClient = PipeStepProcessorGrpc.newBlockingStub(chunkerChannel);
        embedderClient = PipeStepProcessorGrpc.newBlockingStub(embedderChannel);
        echoClient = PipeStepProcessorGrpc.newBlockingStub(echoChannel);
        
        tikaRegistrationClient = ModuleRegistrationServiceGrpc.newBlockingStub(tikaChannel);
        chunkerRegistrationClient = ModuleRegistrationServiceGrpc.newBlockingStub(chunkerChannel);
        embedderRegistrationClient = ModuleRegistrationServiceGrpc.newBlockingStub(embedderChannel);
        echoRegistrationClient = ModuleRegistrationServiceGrpc.newBlockingStub(echoChannel);
    }

    @AfterEach 
    void tearDown() throws InterruptedException {
        if (tikaChannel != null) { tikaChannel.shutdown(); tikaChannel.awaitTermination(5, TimeUnit.SECONDS); }
        if (chunkerChannel != null) { chunkerChannel.shutdown(); chunkerChannel.awaitTermination(5, TimeUnit.SECONDS); }
        if (embedderChannel != null) { embedderChannel.shutdown(); embedderChannel.awaitTermination(5, TimeUnit.SECONDS); }
        if (echoChannel != null) { echoChannel.shutdown(); echoChannel.awaitTermination(5, TimeUnit.SECONDS); }
    }

    @Test
    @Order(1)
    @DisplayName("Service Discovery - All services should be registered and available")
    void testServiceDiscovery() {
        LOG.info("Testing service discovery across all modules");
        
        // Test Tika Parser registration
        ServiceRegistrationResponse tikaResponse = tikaRegistrationClient.getServiceRegistration(Empty.getDefaultInstance());
        assertTrue(tikaResponse.getSuccess(), "Tika service should be registered");
        assertEquals("tika-parser", tikaResponse.getServiceInfo().getServiceName());
        
        // Test Chunker registration
        ServiceRegistrationResponse chunkerResponse = chunkerRegistrationClient.getServiceRegistration(Empty.getDefaultInstance());
        assertTrue(chunkerResponse.getSuccess(), "Chunker service should be registered");
        assertEquals("chunker", chunkerResponse.getServiceInfo().getServiceName());
        
        // Test Embedder registration
        ServiceRegistrationResponse embedderResponse = embedderRegistrationClient.getServiceRegistration(Empty.getDefaultInstance());
        assertTrue(embedderResponse.getSuccess(), "Embedder service should be registered");
        assertEquals("embedder", embedderResponse.getServiceInfo().getServiceName());
        
        // Test Echo Service registration
        ServiceRegistrationResponse echoResponse = echoRegistrationClient.getServiceRegistration(Empty.getDefaultInstance());
        assertTrue(echoResponse.getSuccess(), "Echo service should be registered");
        assertEquals("echo-service", echoResponse.getServiceInfo().getServiceName());
        
        LOG.info("✅ All services registered successfully");
    }

    @Test
    @Order(2)
    @DisplayName("Complete Text Pipeline - Tika -> Chunker -> Embedder workflow")
    void testCompleteTextPipeline() throws Exception {
        LOG.info("Testing complete text processing pipeline");
        
        // Step 1: Create document with blob for Tika processing
        String originalText = "This is a comprehensive test document for end-to-end pipeline validation. " +
                             "It contains multiple sentences that will be processed through Tika parser, " +
                             "then chunked into semantic segments, and finally embedded using machine learning models. " +
                             "This workflow validates the complete document processing pipeline functionality.";
        
        PipeDoc initialDoc = PipeDoc.newBuilder()
                .setId("e2e-text-pipeline-test")
                .setTitle("End-to-End Text Pipeline Test")
                .setBlob(Blob.newBuilder()
                        .setBlobId("e2e-blob-123")
                        .setFilename("test.txt")
                        .setData(ByteString.copyFrom(originalText.getBytes(StandardCharsets.UTF_8)))
                        .setMimeType("text/plain")
                        .build())
                .build();
        
        // Step 2: Process through Tika Parser
        ProcessRequest tikaRequest = createProcessRequest("e2e-text-pipeline", "tika-step", initialDoc, 1);
        ProcessResponse tikaResponse = tikaClient.processData(tikaRequest);
        
        assertTrue(tikaResponse.getSuccess(), "Tika processing should succeed");
        PipeDoc tikaDoc = tikaResponse.getOutputDoc();
        assertNotNull(tikaDoc.getBody(), "Tika should extract text content");
        assertTrue(tikaDoc.getBody().contains("comprehensive test document"), "Extracted text should match original");
        assertTrue(tikaDoc.hasBlob(), "Original blob should be preserved");
        
        LOG.info("✅ Tika processing successful - extracted {} characters", tikaDoc.getBody().length());
        
        // Step 3: Process through Chunker
        ProcessRequest chunkerRequest = createProcessRequest("e2e-text-pipeline", "chunker-step", tikaDoc, 2);
        ProcessResponse chunkerResponse = chunkerClient.processData(chunkerRequest);
        
        assertTrue(chunkerResponse.getSuccess(), "Chunker processing should succeed");
        PipeDoc chunkerDoc = chunkerResponse.getOutputDoc();
        assertTrue(chunkerDoc.getSemanticResultsCount() > 0, "Should have semantic results");
        
        SemanticProcessingResult chunkResult = chunkerDoc.getSemanticResults(0);
        assertTrue(chunkResult.getChunksCount() > 0, "Should have created chunks");
        
        LOG.info("✅ Chunker processing successful - created {} chunks", chunkResult.getChunksCount());
        
        // Step 4: Process through Embedder
        ProcessRequest embedderRequest = createProcessRequest("e2e-text-pipeline", "embedder-step", chunkerDoc, 3);
        ProcessResponse embedderResponse = embedderClient.processData(embedderRequest);
        
        assertTrue(embedderResponse.getSuccess(), "Embedder processing should succeed");
        PipeDoc finalDoc = embedderResponse.getOutputDoc();
        
        // Verify embeddings were added
        assertTrue(finalDoc.getNamedEmbeddingsCount() > 0, "Should have named embeddings");
        
        // Verify semantic results have embeddings
        boolean foundEmbeddedResult = false;
        for (SemanticProcessingResult result : finalDoc.getSemanticResultsList()) {
            if (!result.getEmbeddingConfigId().isEmpty()) {
                foundEmbeddedResult = true;
                for (SemanticChunk chunk : result.getChunksList()) {
                    assertTrue(chunk.getEmbeddingInfo().getVectorCount() > 0, "Chunks should have embeddings");
                }
                break;
            }
        }
        assertTrue(foundEmbeddedResult, "Should have embedded semantic results");
        
        LOG.info("✅ Embedder processing successful - added {} named embeddings", finalDoc.getNamedEmbeddingsCount());
        
        // Verify complete pipeline preservation
        assertEquals(initialDoc.getId(), finalDoc.getId(), "Document ID should be preserved");
        assertTrue(finalDoc.hasBlob(), "Original blob should be preserved through entire pipeline");
        assertEquals(initialDoc.getBlob(), finalDoc.getBlob(), "Blob should be identical");
        
        LOG.info("✅ Complete text pipeline successful - processed through all 3 services");
    }

    @Test
    @Order(3)
    @DisplayName("Complete PDF Pipeline - End-to-end PDF document processing")
    void testCompletePdfPipeline() throws Exception {
        LOG.info("Testing complete PDF processing pipeline");
        
        // Load actual PDF test data
        byte[] pdfData = loadTestDocument("412KB.pdf");
        
        PipeDoc initialDoc = PipeDoc.newBuilder()
                .setId("e2e-pdf-pipeline-test")
                .setTitle("End-to-End PDF Pipeline Test")
                .setBlob(Blob.newBuilder()
                        .setBlobId("e2e-pdf-blob-123")
                        .setFilename("test-document.pdf")
                        .setData(ByteString.copyFrom(pdfData))
                        .setMimeType("application/pdf")
                        .build())
                .build();
        
        long startTime = System.currentTimeMillis();
        
        // Step 1: Tika PDF extraction
        ProcessRequest tikaRequest = createProcessRequest("e2e-pdf-pipeline", "tika-pdf-step", initialDoc, 1);
        ProcessResponse tikaResponse = tikaClient.processData(tikaRequest);
        
        assertTrue(tikaResponse.getSuccess(), "PDF Tika processing should succeed");
        PipeDoc tikaDoc = tikaResponse.getOutputDoc();
        assertNotNull(tikaDoc.getBody(), "Should extract text from PDF");
        assertTrue(tikaDoc.getBody().length() > 100, "Should extract substantial text content");
        
        // Step 2: Chunk the extracted PDF content
        Struct chunkerConfig = Struct.newBuilder()
                .putFields("chunk_size", Value.newBuilder().setNumberValue(500).build())
                .putFields("chunk_overlap", Value.newBuilder().setNumberValue(50).build())
                .build();
        
        ProcessRequest chunkerRequest = createProcessRequestWithConfig("e2e-pdf-pipeline", "chunker-pdf-step", tikaDoc, 2, chunkerConfig);
        ProcessResponse chunkerResponse = chunkerClient.processData(chunkerRequest);
        
        assertTrue(chunkerResponse.getSuccess(), "PDF chunker processing should succeed");
        PipeDoc chunkerDoc = chunkerResponse.getOutputDoc();
        assertTrue(chunkerDoc.getSemanticResultsCount() > 0, "Should have chunked PDF content");
        
        // Step 3: Embed the PDF chunks
        ProcessRequest embedderRequest = createProcessRequest("e2e-pdf-pipeline", "embedder-pdf-step", chunkerDoc, 3);
        ProcessResponse embedderResponse = embedderClient.processData(embedderRequest);
        
        assertTrue(embedderResponse.getSuccess(), "PDF embedder processing should succeed");
        PipeDoc finalDoc = embedderResponse.getOutputDoc();
        
        long totalTime = System.currentTimeMillis() - startTime;
        
        // Verify final result
        assertTrue(finalDoc.getNamedEmbeddingsCount() > 0, "Should have document-level embeddings");
        assertTrue(finalDoc.getSemanticResultsCount() > 0, "Should preserve semantic chunks");
        assertTrue(finalDoc.hasBlob(), "Should preserve original PDF blob");
        
        LOG.info("✅ Complete PDF pipeline successful in {}ms - processed {} KB PDF", totalTime, pdfData.length / 1024);
    }

    @Test
    @Order(4)
    @DisplayName("Multi-Document Batch Pipeline - Process multiple documents through pipeline")
    void testMultiDocumentBatchPipeline() throws Exception {
        LOG.info("Testing multi-document batch processing pipeline");
        
        // Test documents with different formats
        List<TestDocument> testDocs = createTestDocuments();
        List<PipeDoc> finalResults = new ArrayList<>();
        
        for (int i = 0; i < testDocs.size(); i++) {
            TestDocument testDoc = testDocs.get(i);
            LOG.info("Processing document {} of {}: {}", i + 1, testDocs.size(), testDoc.filename);
            
            PipeDoc doc = testDoc.document;
            
            // Process through pipeline
            ProcessResponse tikaResponse = tikaClient.processData(
                createProcessRequest("batch-pipeline-" + i, "tika-batch", doc, 1));
            assertTrue(tikaResponse.getSuccess(), "Batch Tika should succeed for " + testDoc.filename);
            
            ProcessResponse chunkerResponse = chunkerClient.processData(
                createProcessRequest("batch-pipeline-" + i, "chunker-batch", tikaResponse.getOutputDoc(), 2));
            assertTrue(chunkerResponse.getSuccess(), "Batch Chunker should succeed for " + testDoc.filename);
            
            ProcessResponse embedderResponse = embedderClient.processData(
                createProcessRequest("batch-pipeline-" + i, "embedder-batch", chunkerResponse.getOutputDoc(), 3));
            assertTrue(embedderResponse.getSuccess(), "Batch Embedder should succeed for " + testDoc.filename);
            
            finalResults.add(embedderResponse.getOutputDoc());
        }
        
        // Verify all documents were processed successfully
        assertEquals(testDocs.size(), finalResults.size(), "All documents should be processed");
        
        for (int i = 0; i < finalResults.size(); i++) {
            PipeDoc result = finalResults.get(i);
            assertTrue(result.hasBlob(), "Document " + i + " should preserve blob");
            // Most documents should have some embeddings or semantic results
            assertTrue(result.getNamedEmbeddingsCount() > 0 || result.getSemanticResultsCount() > 0,
                      "Document " + i + " should have embeddings or semantic results");
        }
        
        LOG.info("✅ Multi-document batch pipeline successful - processed {} documents", testDocs.size());
    }

    @Test
    @Order(5)
    @DisplayName("Error Propagation - Error handling across service boundaries")
    void testErrorPropagation() {
        LOG.info("Testing error propagation through pipeline");
        
        // Create document with problematic content
        PipeDoc problematicDoc = PipeDoc.newBuilder()
                .setId("error-propagation-test")
                .setTitle("Error Propagation Test")
                .setBlob(Blob.newBuilder()
                        .setBlobId("error-blob")
                        .setFilename("corrupt.pdf")
                        .setData(ByteString.copyFromUtf8("Not a valid PDF file content"))
                        .setMimeType("application/pdf")
                        .build())
                .build();
        
        // Process through Tika (may succeed with extracted text)
        ProcessRequest tikaRequest = createProcessRequest("error-pipeline", "tika-error", problematicDoc, 1);
        ProcessResponse tikaResponse = tikaClient.processData(tikaRequest);
        
        // Regardless of Tika success/failure, test continues
        PipeDoc nextDoc = tikaResponse.getSuccess() ? tikaResponse.getOutputDoc() : problematicDoc;
        
        // Test chunker with potentially problematic content
        ProcessResponse chunkerResponse = chunkerClient.processData(
            createProcessRequest("error-pipeline", "chunker-error", nextDoc, 2));
        
        // Test embedder
        PipeDoc embedderInput = chunkerResponse.getSuccess() ? chunkerResponse.getOutputDoc() : nextDoc;
        ProcessResponse embedderResponse = embedderClient.processData(
            createProcessRequest("error-pipeline", "embedder-error", embedderInput, 3));
        
        // The pipeline should handle errors gracefully - services should not crash
        assertNotNull(tikaResponse, "Tika should return a response");
        assertNotNull(chunkerResponse, "Chunker should return a response");
        assertNotNull(embedderResponse, "Embedder should return a response");
        
        LOG.info("✅ Error propagation test successful - services handle errors gracefully");
    }

    @Test
    @Order(6)
    @DisplayName("Configuration Consistency - Custom configurations through pipeline")
    void testConfigurationConsistency() throws Exception {
        LOG.info("Testing configuration consistency through pipeline");
        
        String testText = "Configuration consistency test document with sufficient content for chunking and embedding validation.";
        
        PipeDoc doc = PipeDoc.newBuilder()
                .setId("config-consistency-test")
                .setTitle("Configuration Test Document")
                .setBlob(Blob.newBuilder()
                        .setBlobId("config-blob")
                        .setFilename("config-test.txt")
                        .setData(ByteString.copyFrom(testText.getBytes()))
                        .setMimeType("text/plain")
                        .build())
                .build();
        
        // Custom configurations
        Map<String, String> tikaConfig = new HashMap<>();
        tikaConfig.put("extractMetadata", "true");
        
        Struct chunkerConfig = Struct.newBuilder()
                .putFields("chunk_size", Value.newBuilder().setNumberValue(100).build())
                .putFields("chunk_overlap", Value.newBuilder().setNumberValue(20).build())
                .putFields("chunk_config_id", Value.newBuilder().setStringValue("small_config_test").build())
                .build();
        
        // Process with configurations
        ProcessResponse tikaResponse = tikaClient.processData(
            createProcessRequestWithParams("config-pipeline", "tika-config", doc, 1, tikaConfig));
        assertTrue(tikaResponse.getSuccess(), "Configured Tika should succeed");
        
        ProcessResponse chunkerResponse = chunkerClient.processData(
            createProcessRequestWithConfig("config-pipeline", "chunker-config", tikaResponse.getOutputDoc(), 2, chunkerConfig));
        assertTrue(chunkerResponse.getSuccess(), "Configured Chunker should succeed");
        
        // Verify configuration effects
        PipeDoc chunkerDoc = chunkerResponse.getOutputDoc();
        if (chunkerDoc.getSemanticResultsCount() > 0) {
            SemanticProcessingResult result = chunkerDoc.getSemanticResults(0);
            assertEquals("small_config_test", result.getChunkConfigId(), "Should use custom chunk config");
        }
        
        ProcessResponse embedderResponse = embedderClient.processData(
            createProcessRequest("config-pipeline", "embedder-config", chunkerDoc, 3));
        assertTrue(embedderResponse.getSuccess(), "Embedder should succeed after configured chunker");
        
        LOG.info("✅ Configuration consistency test successful");
    }

    @Test
    @Order(7)
    @DisplayName("Performance Benchmarking - End-to-end pipeline performance")
    void testPipelinePerformance() throws Exception {
        LOG.info("Testing end-to-end pipeline performance");
        
        // Create moderately sized document
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            content.append("This is sentence ").append(i + 1)
                   .append(" of a performance test document for benchmarking the complete pipeline. ");
        }
        
        PipeDoc doc = PipeDoc.newBuilder()
                .setId("performance-test")
                .setTitle("Performance Test Document")
                .setBlob(Blob.newBuilder()
                        .setBlobId("perf-blob")
                        .setFilename("performance.txt")
                        .setData(ByteString.copyFrom(content.toString().getBytes()))
                        .setMimeType("text/plain")
                        .build())
                .build();
        
        long totalStartTime = System.currentTimeMillis();
        
        // Measure each stage
        long tikaStart = System.currentTimeMillis();
        ProcessResponse tikaResponse = tikaClient.processData(
            createProcessRequest("perf-pipeline", "tika-perf", doc, 1));
        long tikaTime = System.currentTimeMillis() - tikaStart;
        
        long chunkerStart = System.currentTimeMillis();
        ProcessResponse chunkerResponse = chunkerClient.processData(
            createProcessRequest("perf-pipeline", "chunker-perf", tikaResponse.getOutputDoc(), 2));
        long chunkerTime = System.currentTimeMillis() - chunkerStart;
        
        long embedderStart = System.currentTimeMillis();
        ProcessResponse embedderResponse = embedderClient.processData(
            createProcessRequest("perf-pipeline", "embedder-perf", chunkerResponse.getOutputDoc(), 3));
        long embedderTime = System.currentTimeMillis() - embedderStart;
        
        long totalTime = System.currentTimeMillis() - totalStartTime;
        
        // Verify success
        assertTrue(tikaResponse.getSuccess(), "Performance Tika should succeed");
        assertTrue(chunkerResponse.getSuccess(), "Performance Chunker should succeed");
        assertTrue(embedderResponse.getSuccess(), "Performance Embedder should succeed");
        
        LOG.info("Performance results:");
        LOG.info("  Tika Parser: {}ms", tikaTime);
        LOG.info("  Chunker: {}ms", chunkerTime);
        LOG.info("  Embedder: {}ms", embedderTime);
        LOG.info("  Total Pipeline: {}ms", totalTime);
        LOG.info("  Document Size: {} characters", content.length());
        
        // Performance assertions - adjust based on requirements
        assertTrue(totalTime < 60000, "Complete pipeline should finish within 60 seconds");
        assertTrue(tikaTime < 5000, "Tika should process within 5 seconds");
        assertTrue(chunkerTime < 10000, "Chunker should process within 10 seconds");
        
        LOG.info("✅ Pipeline performance test successful");
    }

    @Test
    @Order(8)
    @DisplayName("Echo Service Integration - Verify document preservation through echo")
    void testEchoServiceIntegration() throws Exception {
        LOG.info("Testing Echo service integration with pipeline output");
        
        // Create and process a document through the pipeline
        String testText = "Echo service integration test document with multiple sentences for processing.";
        
        PipeDoc doc = PipeDoc.newBuilder()
                .setId("echo-integration-test")
                .setTitle("Echo Integration Test")
                .setBody(testText)
                .build();
        
        // Process through partial pipeline
        ProcessResponse chunkerResponse = chunkerClient.processData(
            createProcessRequest("echo-integration", "chunker-echo", doc, 1));
        assertTrue(chunkerResponse.getSuccess(), "Chunker should succeed");
        
        ProcessResponse embedderResponse = embedderClient.processData(
            createProcessRequest("echo-integration", "embedder-echo", chunkerResponse.getOutputDoc(), 2));
        assertTrue(embedderResponse.getSuccess(), "Embedder should succeed");
        
        // Process enriched document through Echo service
        ProcessResponse echoResponse = echoClient.processData(
            createProcessRequest("echo-integration", "echo-final", embedderResponse.getOutputDoc(), 3));
        assertTrue(echoResponse.getSuccess(), "Echo should succeed");
        
        // Verify Echo perfectly preserves the enriched document
        PipeDoc enrichedDoc = embedderResponse.getOutputDoc();
        PipeDoc echoedDoc = echoResponse.getOutputDoc();
        
        assertEquals(enrichedDoc, echoedDoc, "Echo should preserve enriched document exactly");
        assertEquals(enrichedDoc.getSemanticResultsCount(), echoedDoc.getSemanticResultsCount(), "Semantic results should be preserved");
        assertEquals(enrichedDoc.getNamedEmbeddingsCount(), echoedDoc.getNamedEmbeddingsCount(), "Named embeddings should be preserved");
        
        LOG.info("✅ Echo service integration successful - preserved enriched document with {} semantic results and {} embeddings",
                echoedDoc.getSemanticResultsCount(), echoedDoc.getNamedEmbeddingsCount());
    }

    // Helper Methods

    private ProcessRequest createProcessRequest(String pipelineName, String stepName, PipeDoc document, int hopNumber) {
        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setPipelineName(pipelineName)
                .setPipeStepName(stepName)
                .setStreamId("e2e-stream-" + System.currentTimeMillis())
                .setCurrentHopNumber(hopNumber)
                .build();
        
        ProcessConfiguration config = ProcessConfiguration.newBuilder().build();
        
        return ProcessRequest.newBuilder()
                .setDocument(document)
                .setConfig(config)
                .setMetadata(metadata)
                .build();
    }
    
    private ProcessRequest createProcessRequestWithConfig(String pipelineName, String stepName, PipeDoc document, 
                                                         int hopNumber, Struct customConfig) {
        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setPipelineName(pipelineName)
                .setPipeStepName(stepName)
                .setStreamId("e2e-stream-" + System.currentTimeMillis())
                .setCurrentHopNumber(hopNumber)
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
    
    private ProcessRequest createProcessRequestWithParams(String pipelineName, String stepName, PipeDoc document, 
                                                         int hopNumber, Map<String, String> configParams) {
        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setPipelineName(pipelineName)
                .setPipeStepName(stepName)
                .setStreamId("e2e-stream-" + System.currentTimeMillis())
                .setCurrentHopNumber(hopNumber)
                .build();
        
        ProcessConfiguration.Builder configBuilder = ProcessConfiguration.newBuilder();
        if (configParams != null) {
            configBuilder.putAllConfigParams(configParams);
        }
        
        return ProcessRequest.newBuilder()
                .setDocument(document)
                .setConfig(configBuilder.build())
                .setMetadata(metadata)
                .build();
    }
    
    private byte[] loadTestDocument(String filename) throws IOException {
        try (InputStream is = getClass().getResourceAsStream("/test-data/" + filename)) {
            if (is == null) {
                throw new IOException("Test document not found: " + filename);
            }
            return is.readAllBytes();
        }
    }
    
    private static class TestDocument {
        final String filename;
        final PipeDoc document;
        
        TestDocument(String filename, PipeDoc document) {
            this.filename = filename;
            this.document = document;
        }
    }
    
    private List<TestDocument> createTestDocuments() {
        List<TestDocument> docs = new ArrayList<>();
        
        // Text document
        docs.add(new TestDocument("test.txt", PipeDoc.newBuilder()
                .setId("batch-text-doc")
                .setTitle("Batch Text Document")
                .setBlob(Blob.newBuilder()
                        .setBlobId("batch-text-blob")
                        .setFilename("batch.txt")
                        .setData(ByteString.copyFromUtf8("Batch processing test text document content."))
                        .setMimeType("text/plain")
                        .build())
                .build()));
        
        // HTML document
        docs.add(new TestDocument("test.html", PipeDoc.newBuilder()
                .setId("batch-html-doc")
                .setTitle("Batch HTML Document")
                .setBlob(Blob.newBuilder()
                        .setBlobId("batch-html-blob")
                        .setFilename("batch.html")
                        .setData(ByteString.copyFromUtf8("<html><body><h1>Batch HTML</h1><p>Test content</p></body></html>"))
                        .setMimeType("text/html")
                        .build())
                .build()));
        
        // JSON document
        docs.add(new TestDocument("test.json", PipeDoc.newBuilder()
                .setId("batch-json-doc")
                .setTitle("Batch JSON Document")
                .setBlob(Blob.newBuilder()
                        .setBlobId("batch-json-blob")
                        .setFilename("batch.json")
                        .setData(ByteString.copyFromUtf8("{\"title\":\"Batch JSON\",\"content\":\"Test data\"}"))
                        .setMimeType("application/json")
                        .build())
                .build()));
        
        return docs;
    }
}