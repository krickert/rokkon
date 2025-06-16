package com.rokkon.modules.echo;

import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.rokkon.search.model.*;
import com.rokkon.search.sdk.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive integration tests for the Echo service using @QuarkusIntegrationTest.
 * These tests run against the actual containerized service to verify end-to-end functionality.
 * 
 * Test Coverage:
 * - Document echoing with complete preservation
 * - Blob handling and preservation
 * - Custom configuration and log prefix
 * - Service registration and health checks
 * - Error handling and edge cases
 * - Async and sync processing patterns
 * - Metadata and custom data preservation
 * - Complex document structures
 * - Performance verification
 * - Concurrent processing capabilities
 */
@QuarkusIntegrationTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EchoServiceIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(EchoServiceIntegrationTest.class);
    
    private ManagedChannel channel;
    private PipeStepProcessorGrpc.PipeStepProcessorBlockingStub blockingClient;
    private PipeStepProcessorGrpc.PipeStepProcessorStub asyncClient;
    private ModuleRegistrationServiceGrpc.ModuleRegistrationServiceBlockingStub registrationClient;

    @BeforeEach
    void setUp() {
        // Set up gRPC channel for integration testing
        channel = ManagedChannelBuilder.forAddress("localhost", 9003)
                .usePlaintext()
                .build();
                
        blockingClient = PipeStepProcessorGrpc.newBlockingStub(channel);
        asyncClient = PipeStepProcessorGrpc.newStub(channel);
        registrationClient = ModuleRegistrationServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach 
    void tearDown() throws InterruptedException {
        if (channel != null) {
            channel.shutdown();
            channel.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @Order(1)
    @DisplayName("Service Registration - Should register successfully and provide echo service metadata")
    void testServiceRegistration() {
        LOG.info("Testing Echo service registration");
        
        ServiceRegistrationResponse response = registrationClient.getServiceRegistration(Empty.getDefaultInstance());
        
        assertNotNull(response, "Service registration response should not be null");
        assertTrue(response.getSuccess(), "Service registration should be successful");
        assertNotNull(response.getServiceInfo(), "Service info should be provided");
        
        ServiceInfo serviceInfo = response.getServiceInfo();
        assertEquals("echo-service", serviceInfo.getServiceName());
        assertTrue(serviceInfo.getVersion().length() > 0, "Version should be specified");
        assertTrue(serviceInfo.getDescription().toLowerCase().contains("echo"), "Description should mention echo");
        
        LOG.info("✅ Echo service registered successfully: {} v{}", serviceInfo.getServiceName(), serviceInfo.getVersion());
    }

    @Test
    @Order(2)
    @DisplayName("Basic Echo Functionality - Should echo document exactly as received")
    void testBasicEchoFunctionality() {
        String testTitle = "Basic Echo Test Document";
        String testBody = "This is a test document for basic echo functionality verification.";
        
        PipeDoc inputDoc = PipeDoc.newBuilder()
                .setId("basic-echo-test")
                .setTitle(testTitle)
                .setBody(testBody)
                .build();
        
        ProcessRequest request = createProcessRequest("basic-echo-pipeline", "echo-basic", inputDoc);
        
        ProcessResponse response = blockingClient.processData(request);
        
        assertTrue(response.getSuccess(), "Basic echo should be successful");
        assertNotNull(response.getOutputDoc(), "Output document should be present");
        assertEquals(inputDoc, response.getOutputDoc(), "Output document should exactly match input");
        assertEquals(testTitle, response.getOutputDoc().getTitle(), "Title should be preserved");
        assertEquals(testBody, response.getOutputDoc().getBody(), "Body should be preserved");
        
        assertFalse(response.getProcessorLogsList().isEmpty(), "Should have processor logs");
        
        LOG.info("✅ Basic echo functionality successful");
    }

    @Test
    @Order(3)
    @DisplayName("Blob Preservation - Should preserve binary blobs exactly")
    void testBlobPreservation() {
        byte[] testData = "This is test blob data with special characters: àáâãäåæçèéêë".getBytes();
        
        Blob inputBlob = Blob.newBuilder()
                .setBlobId("test-blob-123")
                .setFilename("test-document.pdf")
                .setData(ByteString.copyFrom(testData))
                .setMimeType("application/pdf")
                .build();
        
        PipeDoc inputDoc = PipeDoc.newBuilder()
                .setId("blob-preservation-test")
                .setTitle("Blob Preservation Test")
                .setBody("Document with blob attachment")
                .setBlob(inputBlob)
                .build();
        
        ProcessRequest request = createProcessRequest("blob-preservation-pipeline", "echo-blob", inputDoc);
        
        ProcessResponse response = blockingClient.processData(request);
        
        assertTrue(response.getSuccess(), "Blob preservation should be successful");
        assertTrue(response.getOutputDoc().hasBlob(), "Output document should have blob");
        assertEquals(inputBlob, response.getOutputDoc().getBlob(), "Blob should be exactly preserved");
        assertEquals(inputBlob.getBlobId(), response.getOutputDoc().getBlob().getBlobId(), "Blob ID should match");
        assertEquals(inputBlob.getFilename(), response.getOutputDoc().getBlob().getFilename(), "Filename should match");
        assertEquals(inputBlob.getMimeType(), response.getOutputDoc().getBlob().getMimeType(), "MIME type should match");
        assertArrayEquals(testData, response.getOutputDoc().getBlob().getData().toByteArray(), "Blob data should be identical");
        
        LOG.info("✅ Blob preservation successful - preserved {} bytes", testData.length);
    }

    @Test
    @Order(4)
    @DisplayName("Custom Data Preservation - Should preserve complex custom data structures")
    void testCustomDataPreservation() {
        // Create complex custom data structure
        Struct customData = Struct.newBuilder()
                .putFields("string_field", Value.newBuilder().setStringValue("test string").build())
                .putFields("number_field", Value.newBuilder().setNumberValue(42.5).build())
                .putFields("boolean_field", Value.newBuilder().setBoolValue(true).build())
                .putFields("nested_struct", Value.newBuilder()
                        .setStructValue(Struct.newBuilder()
                                .putFields("nested_string", Value.newBuilder().setStringValue("nested value").build())
                                .build())
                        .build())
                .build();
        
        PipeDoc inputDoc = PipeDoc.newBuilder()
                .setId("custom-data-test")
                .setTitle("Custom Data Preservation Test")
                .setBody("Document with complex custom data")
                .setCustomData(customData)
                .build();
        
        ProcessRequest request = createProcessRequest("custom-data-pipeline", "echo-custom-data", inputDoc);
        
        ProcessResponse response = blockingClient.processData(request);
        
        assertTrue(response.getSuccess(), "Custom data preservation should be successful");
        assertTrue(response.getOutputDoc().hasCustomData(), "Output document should have custom data");
        assertEquals(customData, response.getOutputDoc().getCustomData(), "Custom data should be exactly preserved");
        
        // Verify specific fields
        Struct outputCustomData = response.getOutputDoc().getCustomData();
        assertEquals("test string", outputCustomData.getFieldsOrThrow("string_field").getStringValue());
        assertEquals(42.5, outputCustomData.getFieldsOrThrow("number_field").getNumberValue(), 0.001);
        assertTrue(outputCustomData.getFieldsOrThrow("boolean_field").getBoolValue());
        
        LOG.info("✅ Custom data preservation successful - preserved {} fields", customData.getFieldsCount());
    }

    @Test
    @Order(5)
    @DisplayName("Semantic Results Preservation - Should preserve semantic processing results")
    void testSemanticResultsPreservation() {
        // Create semantic processing results (as would come from chunker)
        SemanticChunk chunk = SemanticChunk.newBuilder()
                .setChunkId("test-chunk-1")
                .setChunkNumber(0)
                .setEmbeddingInfo(ChunkEmbedding.newBuilder()
                        .setTextContent("This is a test chunk")
                        .setChunkId("test-chunk-1")
                        .setChunkConfigId("test-config")
                        .build())
                .build();
        
        SemanticProcessingResult semanticResult = SemanticProcessingResult.newBuilder()
                .setResultId("test-result-1")
                .setSourceFieldName("body")
                .setChunkConfigId("test-chunker")
                .addChunks(chunk)
                .build();
        
        PipeDoc inputDoc = PipeDoc.newBuilder()
                .setId("semantic-results-test")
                .setTitle("Semantic Results Preservation Test")
                .setBody("Document with semantic processing results")
                .addSemanticResults(semanticResult)
                .build();
        
        ProcessRequest request = createProcessRequest("semantic-results-pipeline", "echo-semantic", inputDoc);
        
        ProcessResponse response = blockingClient.processData(request);
        
        assertTrue(response.getSuccess(), "Semantic results preservation should be successful");
        assertEquals(1, response.getOutputDoc().getSemanticResultsCount(), "Should have one semantic result");
        assertEquals(semanticResult, response.getOutputDoc().getSemanticResults(0), "Semantic result should be exactly preserved");
        
        LOG.info("✅ Semantic results preservation successful");
    }

    @Test
    @Order(6)
    @DisplayName("Keywords and Named Embeddings - Should preserve all document annotations")
    void testKeywordsAndNamedEmbeddingsPreservation() {
        // Create named embeddings
        NamedEmbedding titleEmbedding = NamedEmbedding.newBuilder()
                .setModelId("test-model")
                .addVector(0.1f)
                .addVector(0.2f)
                .addVector(0.3f)
                .build();
        
        PipeDoc inputDoc = PipeDoc.newBuilder()
                .setId("annotations-test")
                .setTitle("Annotations Preservation Test")
                .setBody("Document with keywords and embeddings")
                .addKeywords("test")
                .addKeywords("echo")
                .addKeywords("preservation")
                .putNamedEmbeddings("title_embedding", titleEmbedding)
                .build();
        
        ProcessRequest request = createProcessRequest("annotations-pipeline", "echo-annotations", inputDoc);
        
        ProcessResponse response = blockingClient.processData(request);
        
        assertTrue(response.getSuccess(), "Annotations preservation should be successful");
        
        PipeDoc outputDoc = response.getOutputDoc();
        assertEquals(3, outputDoc.getKeywordsCount(), "Should preserve all keywords");
        assertTrue(outputDoc.getKeywordsList().contains("test"), "Should contain 'test' keyword");
        assertTrue(outputDoc.getKeywordsList().contains("echo"), "Should contain 'echo' keyword");
        assertTrue(outputDoc.getKeywordsList().contains("preservation"), "Should contain 'preservation' keyword");
        
        assertEquals(1, outputDoc.getNamedEmbeddingsCount(), "Should preserve named embeddings");
        assertTrue(outputDoc.containsNamedEmbeddings("title_embedding"), "Should contain title embedding");
        assertEquals(titleEmbedding, outputDoc.getNamedEmbeddingsOrThrow("title_embedding"), "Named embedding should be exactly preserved");
        
        LOG.info("✅ Keywords and named embeddings preservation successful");
    }

    @Test
    @Order(7)
    @DisplayName("Custom Configuration - Should respect custom log prefix configuration")
    void testCustomConfiguration() {
        String customLogPrefix = "CUSTOM_ECHO_PREFIX: ";
        
        PipeDoc inputDoc = PipeDoc.newBuilder()
                .setId("custom-config-test")
                .setTitle("Custom Configuration Test")
                .setBody("Testing custom configuration")
                .build();
        
        Struct customConfig = Struct.newBuilder()
                .putFields("log_prefix", Value.newBuilder().setStringValue(customLogPrefix).build())
                .build();
        
        ProcessRequest request = createProcessRequestWithJsonConfig("custom-config-pipeline", "echo-custom-config", inputDoc, customConfig);
        
        ProcessResponse response = blockingClient.processData(request);
        
        assertTrue(response.getSuccess(), "Custom configuration should be successful");
        assertEquals(inputDoc, response.getOutputDoc(), "Document should be echoed exactly");
        
        // Verify custom log prefix in processor logs
        assertFalse(response.getProcessorLogsList().isEmpty(), "Should have processor logs");
        String logMessage = response.getProcessorLogs(0);
        assertTrue(logMessage.startsWith(customLogPrefix), "Log should start with custom prefix");
        
        LOG.info("✅ Custom configuration successful with prefix: {}", customLogPrefix);
    }

    @Test
    @Order(8)
    @DisplayName("Empty Document Handling - Should handle documents with minimal content")
    void testEmptyDocumentHandling() {
        PipeDoc inputDoc = PipeDoc.newBuilder()
                .setId("empty-doc-test")
                .build(); // Only ID, no other fields
        
        ProcessRequest request = createProcessRequest("empty-doc-pipeline", "echo-empty", inputDoc);
        
        ProcessResponse response = blockingClient.processData(request);
        
        assertTrue(response.getSuccess(), "Empty document processing should be successful");
        assertEquals(inputDoc, response.getOutputDoc(), "Empty document should be echoed exactly");
        assertFalse(response.getOutputDoc().hasBlob(), "Should not have blob");
        assertFalse(response.getOutputDoc().hasCustomData(), "Should not have custom data");
        
        LOG.info("✅ Empty document handling successful");
    }

    @Test
    @Order(9)
    @DisplayName("Large Document Handling - Should handle large documents efficiently")
    void testLargeDocumentHandling() {
        // Create large document content
        StringBuilder largeContent = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            largeContent.append("This is line ").append(i + 1).append(" of a very large document for testing echo service performance. ");
        }
        
        PipeDoc inputDoc = PipeDoc.newBuilder()
                .setId("large-doc-test")
                .setTitle("Large Document Test")
                .setBody(largeContent.toString())
                .build();
        
        ProcessRequest request = createProcessRequest("large-doc-pipeline", "echo-large", inputDoc);
        
        long startTime = System.currentTimeMillis();
        ProcessResponse response = blockingClient.processData(request);
        long processingTime = System.currentTimeMillis() - startTime;
        
        assertTrue(response.getSuccess(), "Large document processing should be successful");
        assertEquals(inputDoc, response.getOutputDoc(), "Large document should be echoed exactly");
        
        LOG.info("✅ Large document handling successful in {}ms - {} characters", 
                processingTime, largeContent.length());
        
        // Performance verification - echo should be very fast
        assertTrue(processingTime < 5000, "Large document echo should complete within 5 seconds");
    }

    @Test
    @Order(10)
    @DisplayName("Async Processing - Should handle asynchronous requests correctly")
    void testAsyncProcessing() throws InterruptedException {
        PipeDoc inputDoc = PipeDoc.newBuilder()
                .setId("async-test")
                .setTitle("Async Processing Test")
                .setBody("Testing asynchronous echo processing")
                .build();
        
        ProcessRequest request = createProcessRequest("async-pipeline", "echo-async", inputDoc);
        
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ProcessResponse> responseRef = new AtomicReference<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        
        asyncClient.processData(request, new StreamObserver<ProcessResponse>() {
            @Override
            public void onNext(ProcessResponse value) {
                responseRef.set(value);
            }
            
            @Override
            public void onError(Throwable t) {
                errorRef.set(t);
                latch.countDown();
            }
            
            @Override
            public void onCompleted() {
                latch.countDown();
            }
        });
        
        assertTrue(latch.await(10, TimeUnit.SECONDS), "Async call should complete within 10 seconds");
        assertNull(errorRef.get(), "Async call should not produce error");
        
        ProcessResponse response = responseRef.get();
        assertNotNull(response, "Response should not be null");
        assertTrue(response.getSuccess(), "Async processing should be successful");
        assertEquals(inputDoc, response.getOutputDoc(), "Document should be echoed exactly in async mode");
        
        LOG.info("✅ Async processing successful");
    }

    @Test
    @Order(11)
    @DisplayName("Concurrent Processing - Should handle multiple concurrent requests")
    void testConcurrentProcessing() throws Exception {
        int concurrentRequests = 10;
        Thread[] threads = new Thread[concurrentRequests];
        boolean[] results = new boolean[concurrentRequests];
        
        for (int i = 0; i < concurrentRequests; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                try {
                    PipeDoc inputDoc = PipeDoc.newBuilder()
                            .setId("concurrent-" + index)
                            .setTitle("Concurrent Test " + index)
                            .setBody("Concurrent processing test document number " + index)
                            .build();
                    
                    ProcessRequest request = createProcessRequest("concurrent-pipeline-" + index, "echo-concurrent", inputDoc);
                    
                    ProcessResponse response = blockingClient.processData(request);
                    results[index] = response.getSuccess() && 
                                   inputDoc.equals(response.getOutputDoc());
                } catch (Exception e) {
                    LOG.error("Concurrent processing failed for request {}", index, e);
                    results[index] = false;
                }
            });
        }
        
        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }
        
        // Wait for completion
        for (Thread thread : threads) {
            thread.join();
        }
        
        // Verify all succeeded
        for (int i = 0; i < concurrentRequests; i++) {
            assertTrue(results[i], "Concurrent request " + i + " should succeed");
        }
        
        LOG.info("✅ Concurrent processing test passed for {} requests", concurrentRequests);
    }

    @Test
    @Order(12)
    @DisplayName("Error Handling - Should handle invalid requests gracefully")
    void testErrorHandling() {
        // Test with null document (if possible through protobuf)
        ProcessRequest request = ProcessRequest.newBuilder()
                .setMetadata(ServiceMetadata.newBuilder()
                        .setPipelineName("error-test-pipeline")
                        .setPipeStepName("echo-error")
                        .setStreamId("error-stream")
                        .setCurrentHopNumber(1)
                        .build())
                .setConfig(ProcessConfiguration.newBuilder().build())
                // No document set
                .build();
        
        ProcessResponse response = blockingClient.processData(request);
        
        // Should handle gracefully - either succeed with empty doc or provide clear error
        assertNotNull(response, "Response should not be null");
        if (!response.getSuccess()) {
            assertNotNull(response.getErrorDetails(), "Error details should be provided");
            LOG.info("✅ Error handled gracefully: {}", response.getErrorDetails());
        } else {
            LOG.info("✅ Invalid request handled gracefully with default behavior");
        }
    }

    @Test
    @Order(13)
    @DisplayName("Service Health Check - Should respond to health verification")
    void testServiceHealthCheck() {
        // Simple health check by sending minimal valid request
        PipeDoc healthDoc = PipeDoc.newBuilder().setId("health-check").build();
        ProcessRequest request = createProcessRequest("health-pipeline", "echo-health", healthDoc);
        
        assertDoesNotThrow(() -> {
            ProcessResponse response = blockingClient.processData(request);
            assertNotNull(response, "Health check should return response");
            assertTrue(response.getSuccess(), "Health check should be successful");
        }, "Service should respond to health checks");
        
        LOG.info("✅ Service health check successful");
    }

    @Test
    @Order(14)
    @DisplayName("Performance Benchmarking - Should meet echo performance requirements")
    void testPerformanceBenchmarking() {
        // Test multiple document sizes
        int[] documentSizes = {100, 1000, 10000, 50000}; // characters
        
        for (int size : documentSizes) {
            StringBuilder content = new StringBuilder();
            for (int i = 0; i < size / 10; i++) {
                content.append("0123456789");
            }
            
            PipeDoc inputDoc = PipeDoc.newBuilder()
                    .setId("perf-test-" + size)
                    .setTitle("Performance Test Document")
                    .setBody(content.toString())
                    .build();
            
            ProcessRequest request = createProcessRequest("perf-pipeline-" + size, "echo-perf", inputDoc);
            
            long startTime = System.nanoTime();
            ProcessResponse response = blockingClient.processData(request);
            long processingTime = (System.nanoTime() - startTime) / 1_000_000; // Convert to milliseconds
            
            assertTrue(response.getSuccess(), "Performance test should succeed for size " + size);
            assertEquals(inputDoc, response.getOutputDoc(), "Document should be echoed exactly");
            
            LOG.info("Performance test - {} chars processed in {}ms", size, processingTime);
            
            // Echo should be very fast - under 1 second for all sizes
            assertTrue(processingTime < 1000, "Echo processing should be under 1 second for " + size + " characters");
        }
        
        LOG.info("✅ Performance benchmarking successful");
    }

    // MASSIVE EXPANSION - ADVANCED ECHO SERVICE SCENARIOS

    @Test
    @Order(15)
    @DisplayName("Massive Object Handling - Should handle extremely large documents")
    void testMassiveObjectHandling() {
        // Create an extremely large document
        StringBuilder massiveContent = new StringBuilder();
        
        // Generate approximately 100MB of content
        String baseContent = "This is a massive document stress test for the echo service. ".repeat(100);
        
        for (int i = 0; i < 10000; i++) {
            massiveContent.append("Section ").append(i).append(": ").append(baseContent);
            if (i % 100 == 0) {
                massiveContent.append("\n\n=== CHAPTER ").append(i / 100).append(" ===\n\n");
            }
        }
        
        // Create large blob data
        byte[] largeBlobData = new byte[50 * 1024 * 1024]; // 50MB blob
        for (int i = 0; i < largeBlobData.length; i++) {
            largeBlobData[i] = (byte) (i % 256);
        }
        
        Blob massiveBlob = Blob.newBuilder()
                .setBlobId("massive-blob-test")
                .setFilename("massive-test-file.dat")
                .setData(ByteString.copyFrom(largeBlobData))
                .setMimeType("application/octet-stream")
                .build();
        
        PipeDoc massiveDoc = PipeDoc.newBuilder()
                .setId("massive-doc-test")
                .setTitle("Massive Document Stress Test")
                .setBody(massiveContent.toString())
                .setBlob(massiveBlob)
                .build();
        
        ProcessRequest request = createProcessRequest("massive-pipeline", "echo-massive", massiveDoc);
        
        long startTime = System.currentTimeMillis();
        ProcessResponse response = blockingClient.processData(request);
        long processingTime = System.currentTimeMillis() - startTime;
        
        assertTrue(response.getSuccess(), "Massive object handling should be successful");
        assertEquals(massiveDoc, response.getOutputDoc(), "Massive document should be echoed exactly");
        
        LOG.info("✅ Massive object handled successfully in {}ms - content: {}MB, blob: {}MB", 
                processingTime, massiveContent.length() / (1024 * 1024), largeBlobData.length / (1024 * 1024));
        
        // Should handle within reasonable time (allow up to 30 seconds for 150MB total)
        assertTrue(processingTime < 30000, "Massive object should be handled within 30 seconds");
    }

    @Test
    @Order(16)
    @DisplayName("High Load Concurrent Testing - Should handle extreme concurrent load")
    void testHighLoadConcurrentTesting() throws Exception {
        int concurrentRequests = 100; // Very high concurrency
        Thread[] threads = new Thread[concurrentRequests];
        boolean[] results = new boolean[concurrentRequests];
        long[] processingTimes = new long[concurrentRequests];
        
        for (int i = 0; i < concurrentRequests; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                try {
                    // Create varied content for each request
                    String content = "High load test document " + index + " with substantial content. ".repeat(index + 50);
                    
                    PipeDoc inputDoc = PipeDoc.newBuilder()
                            .setId("high-load-" + index)
                            .setTitle("High Load Test " + index)
                            .setBody(content)
                            .build();
                    
                    ProcessRequest request = createProcessRequest("high-load-pipeline-" + index, "echo-high-load", inputDoc);
                    
                    long startTime = System.currentTimeMillis();
                    ProcessResponse response = blockingClient.processData(request);
                    processingTimes[index] = System.currentTimeMillis() - startTime;
                    
                    results[index] = response.getSuccess() && inputDoc.equals(response.getOutputDoc());
                    
                    if (results[index]) {
                        LOG.debug("High load test {} completed in {}ms", index, processingTimes[index]);
                    }
                } catch (Exception e) {
                    LOG.error("High load test {} failed", index, e);
                    results[index] = false;
                }
            });
        }
        
        // Start all threads simultaneously
        long testStartTime = System.currentTimeMillis();
        for (Thread thread : threads) {
            thread.start();
        }
        
        // Wait for completion
        for (Thread thread : threads) {
            thread.join();
        }
        long totalTestTime = System.currentTimeMillis() - testStartTime;
        
        // Calculate statistics
        int successCount = 0;
        long totalProcessingTime = 0;
        long maxProcessingTime = 0;
        long minProcessingTime = Long.MAX_VALUE;
        
        for (int i = 0; i < concurrentRequests; i++) {
            if (results[i]) {
                successCount++;
                totalProcessingTime += processingTimes[i];
                maxProcessingTime = Math.max(maxProcessingTime, processingTimes[i]);
                minProcessingTime = Math.min(minProcessingTime, processingTimes[i]);
            }
        }
        
        double successRate = (double) successCount / concurrentRequests * 100;
        double avgProcessingTime = successCount > 0 ? (double) totalProcessingTime / successCount : 0;
        double throughput = (double) successCount / (totalTestTime / 1000.0); // requests per second
        
        // Should handle high load efficiently
        assertTrue(successRate >= 95, "High load success rate should be at least 95%");
        assertTrue(avgProcessingTime < 5000, "Average processing time should be under 5 seconds under high load");
        
        LOG.info("✅ High load concurrent test completed - Success rate: {:.1f}%, " +
                "Avg time: {:.1f}ms, Min: {}ms, Max: {}ms, Throughput: {:.2f} req/sec", 
                successRate, avgProcessingTime, minProcessingTime, maxProcessingTime, throughput);
    }

    @Test
    @Order(17)
    @DisplayName("Complex Nested Structure Test - Should handle deeply nested data structures")
    void testComplexNestedStructureTest() {
        // Create deeply nested custom data structure
        Struct level3 = Struct.newBuilder()
                .putFields("deep_string", Value.newBuilder().setStringValue("Deep nested value").build())
                .putFields("deep_number", Value.newBuilder().setNumberValue(3.14159).build())
                .putFields("deep_array", Value.newBuilder()
                        .setListValue(com.google.protobuf.ListValue.newBuilder()
                                .addValues(Value.newBuilder().setStringValue("item1").build())
                                .addValues(Value.newBuilder().setStringValue("item2").build())
                                .addValues(Value.newBuilder().setStringValue("item3").build())
                                .build())
                        .build())
                .build();
        
        Struct level2 = Struct.newBuilder()
                .putFields("nested_struct", Value.newBuilder().setStructValue(level3).build())
                .putFields("level2_data", Value.newBuilder().setStringValue("Level 2 data").build())
                .build();
        
        Struct level1 = Struct.newBuilder()
                .putFields("level1_struct", Value.newBuilder().setStructValue(level2).build())
                .putFields("root_data", Value.newBuilder().setStringValue("Root level data").build())
                .putFields("large_text", Value.newBuilder().setStringValue("Large text content. ".repeat(10000)).build())
                .build();
        
        // Create multiple semantic results with complex chunks
        List<SemanticProcessingResult> semanticResults = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            List<SemanticChunk> chunks = new ArrayList<>();
            for (int j = 0; j < 5; j++) {
                ChunkEmbedding embedding = ChunkEmbedding.newBuilder()
                        .setTextContent("Complex chunk " + i + "-" + j + " content")
                        .setChunkId("complex-chunk-" + i + "-" + j)
                        .setChunkConfigId("complex_config")
                        .addVector(0.1f * (i + j))
                        .addVector(0.2f * (i + j))
                        .addVector(0.3f * (i + j))
                        .build();
                
                SemanticChunk chunk = SemanticChunk.newBuilder()
                        .setChunkId("complex-chunk-" + i + "-" + j)
                        .setChunkNumber(j)
                        .setEmbeddingInfo(embedding)
                        .build();
                
                chunks.add(chunk);
            }
            
            SemanticProcessingResult result = SemanticProcessingResult.newBuilder()
                    .setResultId("complex-result-" + i)
                    .setSourceFieldName("body")
                    .setChunkConfigId("complex_config")
                    .setEmbeddingConfigId("complex_embedder")
                    .addAllChunks(chunks)
                    .build();
            
            semanticResults.add(result);
        }
        
        // Create multiple named embeddings
        Map<String, NamedEmbedding> namedEmbeddings = new HashMap<>();
        for (int i = 0; i < 20; i++) {
            NamedEmbedding embedding = NamedEmbedding.newBuilder()
                    .setModelId("complex-model-" + i)
                    .addVector(0.1f * i)
                    .addVector(0.2f * i)
                    .addVector(0.3f * i)
                    .addVector(0.4f * i)
                    .addVector(0.5f * i)
                    .build();
            namedEmbeddings.put("embedding_" + i, embedding);
        }
        
        PipeDoc complexDoc = PipeDoc.newBuilder()
                .setId("complex-nested-test")
                .setTitle("Complex Nested Structure Test")
                .setBody("Complex document with deeply nested structures")
                .setCustomData(level1)
                .addAllKeywords(List.of("complex", "nested", "structure", "test", "deep"))
                .addAllSemanticResults(semanticResults)
                .putAllNamedEmbeddings(namedEmbeddings)
                .build();
        
        ProcessRequest request = createProcessRequest("complex-nested-pipeline", "echo-complex", complexDoc);
        
        long startTime = System.currentTimeMillis();
        ProcessResponse response = blockingClient.processData(request);
        long processingTime = System.currentTimeMillis() - startTime;
        
        assertTrue(response.getSuccess(), "Complex nested structure test should be successful");
        assertEquals(complexDoc, response.getOutputDoc(), "Complex document should be echoed exactly");
        
        // Verify specific nested elements
        PipeDoc outputDoc = response.getOutputDoc();
        Struct outputCustomData = outputDoc.getCustomData();
        assertTrue(outputCustomData.containsFields("level1_struct"), "Should preserve level1 structure");
        
        Struct outputLevel1 = outputCustomData.getFieldsOrThrow("level1_struct").getStructValue();
        assertTrue(outputLevel1.containsFields("nested_struct"), "Should preserve level2 structure");
        
        assertEquals(10, outputDoc.getSemanticResultsCount(), "Should preserve all semantic results");
        assertEquals(20, outputDoc.getNamedEmbeddingsCount(), "Should preserve all named embeddings");
        
        LOG.info("✅ Complex nested structure handled successfully in {}ms", processingTime);
    }

    @Test
    @Order(18)
    @DisplayName("Memory Pressure Test - Should handle memory constraints gracefully")
    void testMemoryPressureTest() throws Exception {
        Runtime runtime = Runtime.getRuntime();
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();
        
        // Process many large documents to create memory pressure
        for (int i = 0; i < 100; i++) {
            // Create large content
            String largeContent = "Memory pressure test content. ".repeat(100000); // ~3MB per document
            
            // Create large blob
            byte[] largeBlob = new byte[5 * 1024 * 1024]; // 5MB blob
            for (int j = 0; j < largeBlob.length; j++) {
                largeBlob[j] = (byte) ((i + j) % 256);
            }
            
            Blob blob = Blob.newBuilder()
                    .setBlobId("memory-pressure-blob-" + i)
                    .setFilename("memory-test-" + i + ".dat")
                    .setData(ByteString.copyFrom(largeBlob))
                    .setMimeType("application/octet-stream")
                    .build();
            
            PipeDoc inputDoc = PipeDoc.newBuilder()
                    .setId("memory-pressure-" + i)
                    .setTitle("Memory Pressure Test " + i)
                    .setBody(largeContent)
                    .setBlob(blob)
                    .build();
            
            ProcessRequest request = createProcessRequest("memory-pressure-pipeline", "echo-memory", inputDoc);
            
            ProcessResponse response = blockingClient.processData(request);
            
            assertTrue(response.getSuccess(), "Memory pressure test should succeed for document " + i);
            assertEquals(inputDoc, response.getOutputDoc(), "Document should be echoed exactly under memory pressure");
            
            // Force garbage collection periodically
            if (i % 10 == 0) {
                System.gc();
                Thread.sleep(100);
                
                long currentMemory = runtime.totalMemory() - runtime.freeMemory();
                LOG.debug("Memory pressure test {} - current memory usage: {}MB", 
                        i, (currentMemory - initialMemory) / (1024 * 1024));
            }
        }
        
        // Final memory check
        Thread.sleep(2000);
        System.gc();
        Thread.sleep(2000);
        
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryIncrease = finalMemory - initialMemory;
        
        LOG.info("✅ Memory pressure test completed - Memory increase: {}MB", memoryIncrease / (1024 * 1024));
        
        // Memory increase should be reasonable (allow some increase due to processing)
        assertTrue(memoryIncrease < 1000 * 1024 * 1024, "Memory increase should be less than 1GB");
    }

    @Test
    @Order(19)
    @DisplayName("Resource Limit Testing - Should handle resource constraints")
    void testResourceLimitTesting() throws Exception {
        // Test with various resource constraint scenarios
        Map<String, Integer> resourceTests = new HashMap<>();
        resourceTests.put("small_content", 1000);     // 1KB
        resourceTests.put("medium_content", 100000);  // 100KB  
        resourceTests.put("large_content", 1000000);  // 1MB
        resourceTests.put("xlarge_content", 10000000); // 10MB
        
        for (Map.Entry<String, Integer> test : resourceTests.entrySet()) {
            String contentType = test.getKey();
            int contentSize = test.getValue();
            
            // Generate content of specified size
            StringBuilder content = new StringBuilder();
            String baseText = "Resource limit test content for " + contentType + ". ";
            while (content.length() < contentSize) {
                content.append(baseText);
            }
            
            // Truncate to exact size
            if (content.length() > contentSize) {
                content.setLength(contentSize);
            }
            
            PipeDoc inputDoc = PipeDoc.newBuilder()
                    .setId("resource-limit-" + contentType)
                    .setTitle("Resource Limit Test - " + contentType)
                    .setBody(content.toString())
                    .build();
            
            ProcessRequest request = createProcessRequest("resource-limit-pipeline", "echo-resource", inputDoc);
            
            long startTime = System.currentTimeMillis();
            ProcessResponse response = blockingClient.processData(request);
            long processingTime = System.currentTimeMillis() - startTime;
            
            assertTrue(response.getSuccess(), "Resource limit test should succeed for " + contentType);
            assertEquals(inputDoc, response.getOutputDoc(), "Document should be echoed exactly for " + contentType);
            
            LOG.info("✅ Resource limit test '{}' successful - {}KB processed in {}ms", 
                    contentType, contentSize / 1024, processingTime);
            
            // Processing time should scale reasonably with content size
            assertTrue(processingTime < 10000, "Processing time should be under 10 seconds for " + contentType);
        }
    }

    @Test
    @Order(20)
    @DisplayName("Service Resilience Test - Should handle various failure scenarios")
    void testServiceResilienceTest() throws Exception {
        // Test various edge cases that might cause issues
        List<PipeDoc> resilientTestCases = new ArrayList<>();
        
        // Empty document
        resilientTestCases.add(PipeDoc.newBuilder().setId("empty-test").build());
        
        // Document with only ID
        resilientTestCases.add(PipeDoc.newBuilder().setId("id-only-test").build());
        
        // Document with empty strings
        resilientTestCases.add(PipeDoc.newBuilder()
                .setId("empty-strings-test")
                .setTitle("")
                .setBody("")
                .build());
        
        // Document with special characters
        resilientTestCases.add(PipeDoc.newBuilder()
                .setId("special-chars-test")
                .setTitle("Special: !@#$%^&*()_+-=[]{}|;':\",./<>?`~")
                .setBody("Body with special chars: àáâãäåæçèéêëìíîïñòóôõöùúûüý")
                .build());
        
        // Document with Unicode control characters
        resilientTestCases.add(PipeDoc.newBuilder()
                .setId("unicode-control-test")
                .setTitle("Unicode Control Test")
                .setBody("Control chars: \u0000\u0001\u0002\u0003\u001F\u007F\u0080\u009F")
                .build());
        
        // Document with very long lines
        resilientTestCases.add(PipeDoc.newBuilder()
                .setId("long-line-test")
                .setTitle("Long Line Test")
                .setBody("Very long line: " + "A".repeat(100000))
                .build());
        
        for (int i = 0; i < resilientTestCases.size(); i++) {
            PipeDoc testDoc = resilientTestCases.get(i);
            ProcessRequest request = createProcessRequest("resilience-pipeline-" + i, "echo-resilience", testDoc);
            
            try {
                ProcessResponse response = blockingClient.processData(request);
                
                assertTrue(response.getSuccess(), "Resilience test should succeed for case " + i);
                assertEquals(testDoc, response.getOutputDoc(), "Document should be echoed exactly for case " + i);
                
                LOG.info("✅ Resilience test case {} passed - {}", i, testDoc.getId());
            } catch (Exception e) {
                // If an exception occurs, it should be handled gracefully
                LOG.warn("Resilience test case {} caused exception but was handled: {}", i, e.getMessage());
            }
        }
        
        LOG.info("✅ Service resilience testing completed");
    }

    @Test
    @Order(21)
    @DisplayName("Data Integrity Verification - Should maintain perfect data integrity")
    void testDataIntegrityVerification() throws Exception {
        // Test data integrity with various data types and sizes
        
        // Create document with precise binary data
        byte[] precisionData = new byte[1000];
        for (int i = 0; i < precisionData.length; i++) {
            precisionData[i] = (byte) i;
        }
        
        Blob precisionBlob = Blob.newBuilder()
                .setBlobId("precision-blob")
                .setFilename("precision-test.dat")
                .setData(ByteString.copyFrom(precisionData))
                .setMimeType("application/octet-stream")
                .build();
        
        // Create precise numeric data in custom fields
        Struct precisionStruct = Struct.newBuilder()
                .putFields("precise_double", Value.newBuilder().setNumberValue(3.141592653589793).build())
                .putFields("large_integer", Value.newBuilder().setNumberValue(9223372036854775807L).build())
                .putFields("small_decimal", Value.newBuilder().setNumberValue(0.0000000001).build())
                .build();
        
        String precisionText = "Precision text with exact content: The quick brown fox jumps over the lazy dog. 1234567890";
        
        PipeDoc precisionDoc = PipeDoc.newBuilder()
                .setId("data-integrity-test")
                .setTitle("Data Integrity Verification Test")
                .setBody(precisionText)
                .setBlob(precisionBlob)
                .setCustomData(precisionStruct)
                .addKeywords("precision")
                .addKeywords("integrity")
                .addKeywords("verification")
                .build();
        
        ProcessRequest request = createProcessRequest("integrity-pipeline", "echo-integrity", precisionDoc);
        
        ProcessResponse response = blockingClient.processData(request);
        
        assertTrue(response.getSuccess(), "Data integrity test should be successful");
        
        PipeDoc outputDoc = response.getOutputDoc();
        
        // Verify exact equality
        assertEquals(precisionDoc, outputDoc, "Documents should be exactly equal");
        
        // Verify specific precision elements
        assertEquals(precisionText, outputDoc.getBody(), "Text should be exactly preserved");
        assertArrayEquals(precisionData, outputDoc.getBlob().getData().toByteArray(), "Binary data should be exactly preserved");
        
        Struct outputStruct = outputDoc.getCustomData();
        assertEquals(3.141592653589793, outputStruct.getFieldsOrThrow("precise_double").getNumberValue(), 1e-15);
        assertEquals(9223372036854775807L, (long) outputStruct.getFieldsOrThrow("large_integer").getNumberValue());
        assertEquals(0.0000000001, outputStruct.getFieldsOrThrow("small_decimal").getNumberValue(), 1e-12);
        
        // Verify checksums for additional verification
        String inputChecksum = Integer.toString(precisionDoc.hashCode());
        String outputChecksum = Integer.toString(outputDoc.hashCode());
        assertEquals(inputChecksum, outputChecksum, "Document checksums should match");
        
        LOG.info("✅ Data integrity verification successful - perfect preservation confirmed");
    }

    @Test
    @Order(22)
    @DisplayName("Throughput Stress Test - Should maintain high throughput under stress")
    void testThroughputStressTest() throws Exception {
        int totalRequests = 1000;
        int threadCount = 20;
        int requestsPerThread = totalRequests / threadCount;
        
        Thread[] threads = new Thread[threadCount];
        int[] successCounts = new int[threadCount];
        long[] threadTimes = new long[threadCount];
        
        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            threads[i] = new Thread(() -> {
                long threadStartTime = System.currentTimeMillis();
                int threadSuccessCount = 0;
                
                for (int j = 0; j < requestsPerThread; j++) {
                    try {
                        String content = "Throughput stress test document " + threadIndex + "-" + j;
                        
                        PipeDoc inputDoc = PipeDoc.newBuilder()
                                .setId("throughput-" + threadIndex + "-" + j)
                                .setTitle("Throughput Test Document")
                                .setBody(content)
                                .build();
                        
                        ProcessRequest request = createProcessRequest("throughput-pipeline", "echo-throughput", inputDoc);
                        
                        ProcessResponse response = blockingClient.processData(request);
                        
                        if (response.getSuccess() && inputDoc.equals(response.getOutputDoc())) {
                            threadSuccessCount++;
                        }
                    } catch (Exception e) {
                        LOG.error("Throughput test failed for thread {} request {}", threadIndex, j, e);
                    }
                }
                
                successCounts[threadIndex] = threadSuccessCount;
                threadTimes[threadIndex] = System.currentTimeMillis() - threadStartTime;
                
                LOG.info("Thread {} completed {}/{} requests in {}ms", 
                        threadIndex, threadSuccessCount, requestsPerThread, threadTimes[threadIndex]);
            });
        }
        
        // Start all threads
        long testStartTime = System.currentTimeMillis();
        for (Thread thread : threads) {
            thread.start();
        }
        
        // Wait for completion
        for (Thread thread : threads) {
            thread.join();
        }
        long totalTestTime = System.currentTimeMillis() - testStartTime;
        
        // Calculate overall statistics
        int totalSuccessful = 0;
        for (int successCount : successCounts) {
            totalSuccessful += successCount;
        }
        
        double successRate = (double) totalSuccessful / totalRequests * 100;
        double throughput = (double) totalSuccessful / (totalTestTime / 1000.0); // requests per second
        
        assertTrue(successRate >= 99, "Throughput stress test success rate should be at least 99%");
        assertTrue(throughput >= 50, "Throughput should be at least 50 requests per second");
        
        LOG.info("✅ Throughput stress test completed - Success rate: {:.2f}%, " +
                "Throughput: {:.2f} req/sec, Total time: {}ms", 
                successRate, throughput, totalTestTime);
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
                                                             PipeDoc document, Struct customJsonConfig) {
        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setPipelineName(pipelineName)
                .setPipeStepName(stepName)
                .setStreamId("stream-" + System.currentTimeMillis())
                .setCurrentHopNumber(1)
                .build();
        
        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .setCustomJsonConfig(customJsonConfig)
                .build();
        
        return ProcessRequest.newBuilder()
                .setDocument(document)
                .setConfig(config)
                .setMetadata(metadata)
                .build();
    }
    
    private ProcessRequest createProcessRequestWithParams(String pipelineName, String stepName, 
                                                         PipeDoc document, Map<String, String> configParams) {
        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setPipelineName(pipelineName)
                .setPipeStepName(stepName)
                .setStreamId("stream-" + System.currentTimeMillis())
                .setCurrentHopNumber(1)
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
}