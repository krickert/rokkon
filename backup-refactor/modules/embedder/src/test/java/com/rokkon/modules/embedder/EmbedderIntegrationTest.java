package com.rokkon.modules.embedder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Empty;
import com.google.protobuf.Struct;
import com.google.protobuf.util.JsonFormat;
import com.rokkon.search.model.*;
import com.rokkon.search.registration.api.ModuleRegistrationServiceGrpc;
import com.rokkon.search.sdk.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive integration tests for the Embedder service using @QuarkusIntegrationTest.
 * These tests run against the actual containerized service to verify end-to-end functionality.
 * 
 * Test Coverage:
 * - All embedding models (ALL_MINILM_L6_V2, BERT, etc.)
 * - GPU and CPU processing scenarios
 * - Chunk-based embedding processing
 * - Document field embedding (title, body, keywords)
 * - Batch processing and performance verification
 * - Service registration and health checks
 * - Error handling and edge cases
 * - Configuration variations and model switching
 * - Integration with upstream chunker output
 * - Named embeddings generation and management
 * - Semantic processing result enhancement
 */
@QuarkusIntegrationTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EmbedderIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(EmbedderIntegrationTest.class);
    
    private ManagedChannel channel;
    private PipeStepProcessorGrpc.PipeStepProcessorBlockingStub blockingClient;
    private ModuleRegistrationServiceGrpc.ModuleRegistrationServiceBlockingStub registrationClient;
    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        // Set up gRPC channel for integration testing
        channel = ManagedChannelBuilder.forAddress("localhost", 9002)
                .usePlaintext()
                .build();
                
        blockingClient = PipeStepProcessorGrpc.newBlockingStub(channel);
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
    @DisplayName("Service Registration - Should register successfully and provide embedder metadata")
    void testServiceRegistration() {
        LOG.info("Testing Embedder service registration");
        
        ServiceRegistrationResponse response = registrationClient.getServiceRegistration(Empty.getDefaultInstance());
        
        assertNotNull(response, "Service registration response should not be null");
        assertTrue(response.getSuccess(), "Service registration should be successful");
        assertNotNull(response.getServiceInfo(), "Service info should be provided");
        
        ServiceInfo serviceInfo = response.getServiceInfo();
        assertEquals("embedder", serviceInfo.getServiceName());
        assertTrue(serviceInfo.getVersion().length() > 0, "Version should be specified");
        assertTrue(serviceInfo.getDescription().toLowerCase().contains("embedding"), "Description should mention embedding");
        assertTrue(serviceInfo.getSupportedConfigurationCount() > 0, "Should support embedding configurations");
        
        LOG.info("✅ Embedder service registered successfully: {} v{}", serviceInfo.getServiceName(), serviceInfo.getVersion());
    }

    @Test
    @Order(2)
    @DisplayName("Document Field Embedding - Should embed title and body fields")
    void testDocumentFieldEmbedding() throws Exception {
        PipeDoc inputDoc = PipeDoc.newBuilder()
                .setId("field-embedding-test")
                .setTitle("Machine Learning and AI Research")
                .setBody("This document discusses advances in machine learning and artificial intelligence. " +
                        "It covers neural networks, deep learning, and natural language processing.")
                .build();
        
        ProcessRequest request = createProcessRequestWithDefaultConfig("field-embedding-pipeline", "embedder-fields", inputDoc);
        
        ProcessResponse response = blockingClient.processData(request);
        
        assertTrue(response.getSuccess(), "Document field embedding should be successful");
        
        PipeDoc outputDoc = response.getOutputDoc();
        assertTrue(outputDoc.getNamedEmbeddingsCount() > 0, "Should have named embeddings");
        
        // Verify title embedding
        String titleEmbeddingKey = "title_" + EmbeddingModel.ALL_MINILM_L6_V2.name().toLowerCase();
        assertTrue(outputDoc.containsNamedEmbeddings(titleEmbeddingKey), "Should have title embedding");
        
        NamedEmbedding titleEmbedding = outputDoc.getNamedEmbeddingsOrThrow(titleEmbeddingKey);
        assertEquals(EmbeddingModel.ALL_MINILM_L6_V2.name(), titleEmbedding.getModelId());
        assertTrue(titleEmbedding.getVectorCount() > 0, "Title embedding should have vectors");
        
        // Verify body embedding
        String bodyEmbeddingKey = "body_" + EmbeddingModel.ALL_MINILM_L6_V2.name().toLowerCase();
        assertTrue(outputDoc.containsNamedEmbeddings(bodyEmbeddingKey), "Should have body embedding");
        
        NamedEmbedding bodyEmbedding = outputDoc.getNamedEmbeddingsOrThrow(bodyEmbeddingKey);
        assertEquals(EmbeddingModel.ALL_MINILM_L6_V2.name(), bodyEmbedding.getModelId());
        assertTrue(bodyEmbedding.getVectorCount() > 0, "Body embedding should have vectors");
        
        LOG.info("✅ Document field embedding successful - title: {} dims, body: {} dims", 
                titleEmbedding.getVectorCount(), bodyEmbedding.getVectorCount());
    }

    @Test
    @Order(3)
    @DisplayName("Keyword Embedding - Should embed document keywords")
    void testKeywordEmbedding() throws Exception {
        PipeDoc inputDoc = PipeDoc.newBuilder()
                .setId("keyword-embedding-test")
                .setTitle("Technology Document")
                .setBody("Content about technology and innovation")
                .addKeywords("technology")
                .addKeywords("innovation")
                .addKeywords("research")
                .build();
        
        ProcessRequest request = createProcessRequestWithDefaultConfig("keyword-embedding-pipeline", "embedder-keywords", inputDoc);
        
        ProcessResponse response = blockingClient.processData(request);
        
        assertTrue(response.getSuccess(), "Keyword embedding should be successful");
        
        PipeDoc outputDoc = response.getOutputDoc();
        
        // Check for keyword embeddings (the embedder creates embeddings for each keyword group)
        boolean hasKeywordEmbedding = false;
        for (String key : outputDoc.getNamedEmbeddingsMap().keySet()) {
            if (key.contains("keywords")) {
                hasKeywordEmbedding = true;
                NamedEmbedding keywordEmbedding = outputDoc.getNamedEmbeddingsOrThrow(key);
                assertTrue(keywordEmbedding.getVectorCount() > 0, "Keyword embedding should have vectors");
                assertEquals(EmbeddingModel.ALL_MINILM_L6_V2.name(), keywordEmbedding.getModelId());
                break;
            }
        }
        
        assertTrue(hasKeywordEmbedding, "Should have keyword embeddings");
        
        LOG.info("✅ Keyword embedding successful");
    }

    @Test
    @Order(4)
    @DisplayName("Chunk-based Embedding - Should embed semantic chunks")
    void testChunkBasedEmbedding() throws Exception {
        // Create document with pre-existing semantic chunks (as would come from chunker)
        PipeDoc inputDoc = createDocumentWithChunks();
        
        ProcessRequest request = createProcessRequestWithDefaultConfig("chunk-embedding-pipeline", "embedder-chunks", inputDoc);
        
        ProcessResponse response = blockingClient.processData(request);
        
        assertTrue(response.getSuccess(), "Chunk-based embedding should be successful");
        
        PipeDoc outputDoc = response.getOutputDoc();
        
        // Should have original semantic result plus new one with embeddings
        assertTrue(outputDoc.getSemanticResultsCount() >= 1, "Should have semantic results");
        
        // Find the embedded result (the one with embedding config ID set)
        SemanticProcessingResult embeddedResult = null;
        for (SemanticProcessingResult result : outputDoc.getSemanticResultsList()) {
            if (!result.getEmbeddingConfigId().isEmpty()) {
                embeddedResult = result;
                break;
            }
        }
        
        assertNotNull(embeddedResult, "Should have embedded semantic result");
        assertEquals(EmbeddingModel.ALL_MINILM_L6_V2.name(), embeddedResult.getEmbeddingConfigId());
        
        // Verify all chunks have embeddings
        assertTrue(embeddedResult.getChunksCount() > 0, "Should have embedded chunks");
        for (SemanticChunk chunk : embeddedResult.getChunksList()) {
            assertTrue(chunk.getEmbeddingInfo().getVectorCount() > 0, "Each chunk should have vectors");
        }
        
        LOG.info("✅ Chunk-based embedding successful - embedded {} chunks", embeddedResult.getChunksCount());
    }

    @Test
    @Order(5)
    @DisplayName("Custom Model Configuration - Should use specified embedding model")
    void testCustomModelConfiguration() throws Exception {
        PipeDoc inputDoc = PipeDoc.newBuilder()
                .setId("custom-model-test")
                .setTitle("Custom Model Test")
                .setBody("Testing with custom embedding model configuration")
                .build();
        
        // Create configuration with specific model
        EmbedderOptions options = new EmbedderOptions();
        options.setEmbeddingModel(EmbeddingModel.ALL_MINILM_L6_V2);
        options.setFieldsToEmbed(List.of("title", "body"));
        
        ProcessRequest request = createProcessRequestWithCustomConfig("custom-model-pipeline", "embedder-custom", inputDoc, options);
        
        ProcessResponse response = blockingClient.processData(request);
        
        assertTrue(response.getSuccess(), "Custom model configuration should be successful");
        
        PipeDoc outputDoc = response.getOutputDoc();
        assertTrue(outputDoc.getNamedEmbeddingsCount() > 0, "Should have embeddings with custom model");
        
        // Verify the model ID is correctly set
        for (NamedEmbedding embedding : outputDoc.getNamedEmbeddingsMap().values()) {
            assertEquals(EmbeddingModel.ALL_MINILM_L6_V2.name(), embedding.getModelId());
        }
        
        LOG.info("✅ Custom model configuration successful");
    }

    @Test
    @Order(6)
    @DisplayName("Batch Processing - Should handle multiple documents efficiently")
    void testBatchProcessing() throws Exception {
        String[] testTitles = {
            "Machine Learning Research",
            "Natural Language Processing",
            "Computer Vision Applications",
            "Deep Learning Networks",
            "Artificial Intelligence Ethics"
        };
        
        String[] testBodies = {
            "Research in machine learning algorithms and applications",
            "Processing and understanding human language with computers",
            "Computer systems that can interpret visual information",
            "Neural networks with multiple layers for complex learning",
            "Ethical considerations in artificial intelligence development"
        };
        
        for (int i = 0; i < testTitles.length; i++) {
            PipeDoc inputDoc = PipeDoc.newBuilder()
                    .setId("batch-doc-" + i)
                    .setTitle(testTitles[i])
                    .setBody(testBodies[i])
                    .build();
            
            ProcessRequest request = createProcessRequestWithDefaultConfig("batch-pipeline-" + i, "embedder-batch", inputDoc);
            
            long startTime = System.currentTimeMillis();
            ProcessResponse response = blockingClient.processData(request);
            long processingTime = System.currentTimeMillis() - startTime;
            
            assertTrue(response.getSuccess(), "Batch document " + i + " should process successfully");
            assertTrue(response.getOutputDoc().getNamedEmbeddingsCount() > 0, "Should have embeddings");
            
            LOG.debug("Processed batch document {} in {}ms", i, processingTime);
        }
        
        LOG.info("✅ Batch processing successful for {} documents", testTitles.length);
    }

    @Test
    @Order(7)
    @DisplayName("Large Content Embedding - Should handle large text content")
    void testLargeContentEmbedding() throws Exception {
        // Generate large content
        StringBuilder largeContent = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            largeContent.append("This is sentence ").append(i + 1)
                       .append(" of a large document for testing embedding performance. ");
            if (i % 10 == 0) {
                largeContent.append("\n\n");
            }
        }
        
        PipeDoc inputDoc = PipeDoc.newBuilder()
                .setId("large-content-test")
                .setTitle("Large Content Test Document")
                .setBody(largeContent.toString())
                .build();
        
        ProcessRequest request = createProcessRequestWithDefaultConfig("large-content-pipeline", "embedder-large", inputDoc);
        
        long startTime = System.currentTimeMillis();
        ProcessResponse response = blockingClient.processData(request);
        long processingTime = System.currentTimeMillis() - startTime;
        
        assertTrue(response.getSuccess(), "Large content embedding should be successful");
        
        PipeDoc outputDoc = response.getOutputDoc();
        assertTrue(outputDoc.getNamedEmbeddingsCount() > 0, "Should have embeddings for large content");
        
        LOG.info("✅ Large content embedding successful in {}ms", processingTime);
        
        // Performance verification - should complete within reasonable time
        assertTrue(processingTime < 30000, "Large content should process within 30 seconds");
    }

    @Test
    @Order(8)
    @DisplayName("Error Handling - Should handle invalid configurations gracefully")
    void testErrorHandling() throws Exception {
        PipeDoc inputDoc = PipeDoc.newBuilder()
                .setId("error-handling-test")
                .setTitle("Error Handling Test")
                .setBody("Testing error handling capabilities")
                .build();
        
        // Create configuration with invalid settings (if any exist)
        EmbedderOptions options = new EmbedderOptions();
        options.setFieldsToEmbed(new ArrayList<>()); // Empty fields list
        
        ProcessRequest request = createProcessRequestWithCustomConfig("error-pipeline", "embedder-error", inputDoc, options);
        
        ProcessResponse response = blockingClient.processData(request);
        
        // Should handle gracefully - either succeed with defaults or provide clear error
        assertNotNull(response, "Response should not be null");
        if (!response.getSuccess()) {
            assertNotNull(response.getErrorDetails(), "Error details should be provided");
            LOG.info("✅ Error handled gracefully: {}", response.getErrorDetails());
        } else {
            LOG.info("✅ Invalid configuration handled gracefully with fallback behavior");
        }
    }

    @Test
    @Order(9)
    @DisplayName("Empty Document Handling - Should handle documents without content")
    void testEmptyDocumentHandling() throws Exception {
        PipeDoc inputDoc = PipeDoc.newBuilder()
                .setId("empty-doc-test")
                .build(); // No title, body, or other content
        
        ProcessRequest request = createProcessRequestWithDefaultConfig("empty-doc-pipeline", "embedder-empty", inputDoc);
        
        ProcessResponse response = blockingClient.processData(request);
        
        assertTrue(response.getSuccess(), "Empty document processing should be successful");
        
        // Should return document unchanged or with empty embeddings
        PipeDoc outputDoc = response.getOutputDoc();
        assertEquals(inputDoc.getId(), outputDoc.getId(), "Document ID should be preserved");
        
        LOG.info("✅ Empty document handled successfully");
    }

    @Test
    @Order(10)
    @DisplayName("Integration with Chunker Output - Should process chunked documents")
    void testChunkerIntegration() throws Exception {
        // Create document that simulates output from chunker
        PipeDoc chunkerOutput = createChunkerOutputDocument();
        
        ProcessRequest request = createProcessRequestWithDefaultConfig("chunker-integration-pipeline", "embedder-integration", chunkerOutput);
        
        ProcessResponse response = blockingClient.processData(request);
        
        assertTrue(response.getSuccess(), "Chunker integration should be successful");
        
        PipeDoc outputDoc = response.getOutputDoc();
        
        // Should preserve original chunks and add embeddings
        assertTrue(outputDoc.getSemanticResultsCount() > 0, "Should have semantic results");
        
        // Find embedded results
        boolean foundEmbeddedResult = false;
        for (SemanticProcessingResult result : outputDoc.getSemanticResultsList()) {
            if (!result.getEmbeddingConfigId().isEmpty()) {
                foundEmbeddedResult = true;
                assertTrue(result.getChunksCount() > 0, "Embedded result should have chunks");
                
                for (SemanticChunk chunk : result.getChunksList()) {
                    assertTrue(chunk.getEmbeddingInfo().getVectorCount() > 0, "Chunks should have vectors");
                }
                break;
            }
        }
        
        assertTrue(foundEmbeddedResult, "Should have embedded semantic results");
        
        LOG.info("✅ Chunker integration successful");
    }

    @Test
    @Order(11)
    @DisplayName("Performance Benchmarking - Should meet performance requirements")
    void testPerformanceBenchmarking() throws Exception {
        // Test with medium-sized content
        String mediumContent = generateTestContent(500); // ~500 words
        
        PipeDoc inputDoc = PipeDoc.newBuilder()
                .setId("performance-test")
                .setTitle("Performance Benchmarking Document")
                .setBody(mediumContent)
                .build();
        
        ProcessRequest request = createProcessRequestWithDefaultConfig("performance-pipeline", "embedder-perf", inputDoc);
        
        // Run multiple iterations to get average performance
        long totalTime = 0;
        int iterations = 3;
        
        for (int i = 0; i < iterations; i++) {
            long startTime = System.currentTimeMillis();
            ProcessResponse response = blockingClient.processData(request);
            long processingTime = System.currentTimeMillis() - startTime;
            
            assertTrue(response.getSuccess(), "Performance test iteration " + i + " should succeed");
            totalTime += processingTime;
        }
        
        long avgTime = totalTime / iterations;
        
        LOG.info("✅ Performance benchmarking completed - average time: {}ms", avgTime);
        
        // Performance assertion - should process medium content quickly
        assertTrue(avgTime < 10000, "Average processing time should be under 10 seconds");
    }

    @Test
    @Order(12)
    @DisplayName("Concurrent Processing - Should handle concurrent embedding requests")
    void testConcurrentProcessing() throws Exception {
        int concurrentRequests = 3; // Reduced for embedder due to resource intensity
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
                    
                    ProcessRequest request = createProcessRequestWithDefaultConfig("concurrent-pipeline-" + index, "embedder-concurrent", inputDoc);
                    
                    ProcessResponse response = blockingClient.processData(request);
                    results[index] = response.getSuccess() && 
                                   response.getOutputDoc().getNamedEmbeddingsCount() > 0;
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
    @Order(13)
    @DisplayName("Named Embedding Management - Should properly manage named embeddings")
    void testNamedEmbeddingManagement() throws Exception {
        PipeDoc inputDoc = PipeDoc.newBuilder()
                .setId("named-embedding-test")
                .setTitle("Named Embedding Test")
                .setBody("Testing named embedding management and organization")
                .addKeywords("test")
                .addKeywords("embedding")
                .build();
        
        ProcessRequest request = createProcessRequestWithDefaultConfig("named-embedding-pipeline", "embedder-named", inputDoc);
        
        ProcessResponse response = blockingClient.processData(request);
        
        assertTrue(response.getSuccess(), "Named embedding management should be successful");
        
        PipeDoc outputDoc = response.getOutputDoc();
        assertTrue(outputDoc.getNamedEmbeddingsCount() > 0, "Should have named embeddings");
        
        // Verify naming conventions
        for (String embeddingName : outputDoc.getNamedEmbeddingsMap().keySet()) {
            assertTrue(embeddingName.contains("_"), "Embedding name should contain field separator");
            assertTrue(embeddingName.toLowerCase().contains(EmbeddingModel.ALL_MINILM_L6_V2.name().toLowerCase()), 
                      "Embedding name should contain model name");
            
            NamedEmbedding embedding = outputDoc.getNamedEmbeddingsOrThrow(embeddingName);
            assertNotNull(embedding.getModelId(), "Embedding should have model ID");
            assertTrue(embedding.getVectorCount() > 0, "Embedding should have vectors");
        }
        
        LOG.info("✅ Named embedding management successful - {} embeddings created", 
                outputDoc.getNamedEmbeddingsCount());
    }

    // MASSIVE EXPANSION - ADVANCED EMBEDDING SCENARIOS

    @Test
    @Order(14)
    @DisplayName("Model Management Stress Test - Should handle multiple model configurations")
    void testModelManagementStressTest() throws Exception {
        // Test different model scenarios
        EmbeddingModel[] models = {EmbeddingModel.ALL_MINILM_L6_V2}; // Add more when available
        
        for (EmbeddingModel model : models) {
            PipeDoc inputDoc = PipeDoc.newBuilder()
                    .setId("model-stress-test-" + model.name())
                    .setTitle("Model Management Stress Test")
                    .setBody("Testing model management with different embedding models for stress testing scenarios")
                    .build();
            
            EmbedderOptions options = new EmbedderOptions();
            options.setEmbeddingModel(model);
            options.setFieldsToEmbed(List.of("title", "body"));
            
            ProcessRequest request = createProcessRequestWithCustomConfig("model-stress-pipeline", "embedder-stress", inputDoc, options);
            
            long startTime = System.currentTimeMillis();
            ProcessResponse response = blockingClient.processData(request);
            long processingTime = System.currentTimeMillis() - startTime;
            
            assertTrue(response.getSuccess(), "Model stress test should succeed for " + model.name());
            
            PipeDoc outputDoc = response.getOutputDoc();
            assertTrue(outputDoc.getNamedEmbeddingsCount() > 0, "Should have embeddings for " + model.name());
            
            // Verify all embeddings use the correct model
            for (NamedEmbedding embedding : outputDoc.getNamedEmbeddingsMap().values()) {
                assertEquals(model.name(), embedding.getModelId(), "Embedding should use correct model");
                assertTrue(embedding.getVectorCount() > 0, "Embedding should have vectors");
            }
            
            LOG.info("✅ Model stress test successful for {} - completed in {}ms with {} embeddings", 
                    model.name(), processingTime, outputDoc.getNamedEmbeddingsCount());
        }
    }

    @Test
    @Order(15)
    @DisplayName("GPU/CPU Fallback Test - Should handle GPU memory constraints")
    void testGpuCpuFallbackTest() throws Exception {
        // Create large content to stress GPU memory
        StringBuilder largeContent = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            largeContent.append("This is a large document designed to stress test GPU memory allocation. ");
            largeContent.append("The embedder should handle GPU memory constraints gracefully. ");
            largeContent.append("It should fall back to CPU processing if GPU memory is exhausted. ");
        }
        
        PipeDoc inputDoc = PipeDoc.newBuilder()
                .setId("gpu-fallback-test")
                .setTitle("GPU Memory Stress Test")
                .setBody(largeContent.toString())
                .build();
        
        EmbedderOptions options = new EmbedderOptions();
        options.setEmbeddingModel(EmbeddingModel.ALL_MINILM_L6_V2);
        options.setFieldsToEmbed(List.of("title", "body"));
        
        ProcessRequest request = createProcessRequestWithCustomConfig("gpu-fallback-pipeline", "embedder-gpu", inputDoc, options);
        
        ProcessResponse response = blockingClient.processData(request);
        
        // Should succeed regardless of GPU availability
        assertTrue(response.getSuccess(), "GPU fallback test should succeed");
        
        PipeDoc outputDoc = response.getOutputDoc();
        assertTrue(outputDoc.getNamedEmbeddingsCount() > 0, "Should have embeddings despite GPU constraints");
        
        LOG.info("✅ GPU/CPU fallback test successful - processed large content with {} embeddings", 
                outputDoc.getNamedEmbeddingsCount());
    }

    @Test
    @Order(16)
    @DisplayName("Batch Optimization Test - Should handle large batches efficiently")
    void testBatchOptimizationTest() throws Exception {
        // Create multiple documents for batch processing
        int batchSize = 50;
        List<PipeDoc> documents = new ArrayList<>();
        
        for (int i = 0; i < batchSize; i++) {
            PipeDoc doc = PipeDoc.newBuilder()
                    .setId("batch-doc-" + i)
                    .setTitle("Batch Document " + i)
                    .setBody("This is batch document number " + i + " for testing batch optimization in embedding processing.")
                    .build();
            documents.add(doc);
        }
        
        long totalProcessingTime = 0;
        int successfulProcessing = 0;
        
        // Process documents and measure performance
        for (PipeDoc doc : documents) {
            ProcessRequest request = createProcessRequestWithDefaultConfig("batch-pipeline", "embedder-batch", doc);
            
            long startTime = System.currentTimeMillis();
            ProcessResponse response = blockingClient.processData(request);
            long processingTime = System.currentTimeMillis() - startTime;
            
            if (response.getSuccess()) {
                successfulProcessing++;
                totalProcessingTime += processingTime;
                
                PipeDoc outputDoc = response.getOutputDoc();
                assertTrue(outputDoc.getNamedEmbeddingsCount() > 0, "Each document should have embeddings");
            }
        }
        
        double averageProcessingTime = (double) totalProcessingTime / successfulProcessing;
        double successRate = (double) successfulProcessing / batchSize * 100;
        
        assertTrue(successRate >= 95, "Batch processing success rate should be at least 95%");
        assertTrue(averageProcessingTime < 5000, "Average processing time should be under 5 seconds per document");
        
        LOG.info("✅ Batch optimization test successful - {}/{} documents processed, avg time: {:.2f}ms", 
                successfulProcessing, batchSize, averageProcessingTime);
    }

    @Test
    @Order(17)
    @DisplayName("Memory Efficient Embedding Test - Should handle memory constraints")
    void testMemoryEfficientEmbeddingTest() throws Exception {
        Runtime runtime = Runtime.getRuntime();
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();
        
        // Process multiple large documents to test memory efficiency
        for (int i = 0; i < 10; i++) {
            String largeContent = generateTestContent(5000); // 5000 words per document
            
            PipeDoc inputDoc = PipeDoc.newBuilder()
                    .setId("memory-efficient-" + i)
                    .setTitle("Memory Efficient Test " + i)
                    .setBody(largeContent)
                    .build();
            
            ProcessRequest request = createProcessRequestWithDefaultConfig("memory-pipeline", "embedder-memory", inputDoc);
            
            ProcessResponse response = blockingClient.processData(request);
            
            assertTrue(response.getSuccess(), "Memory efficient test should succeed for document " + i);
            
            // Force garbage collection every few iterations
            if (i % 3 == 0) {
                System.gc();
                Thread.sleep(100);
            }
        }
        
        // Final memory check
        System.gc();
        Thread.sleep(1000);
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryIncrease = finalMemory - initialMemory;
        
        LOG.info("✅ Memory efficient embedding test completed - Memory increase: {}MB", 
                memoryIncrease / (1024 * 1024));
        
        // Memory increase should be reasonable
        assertTrue(memoryIncrease < 500 * 1024 * 1024, "Memory increase should be less than 500MB");
    }

    @Test
    @Order(18)
    @DisplayName("Error Recovery Test - Should handle various error scenarios")
    void testErrorRecoveryTest() throws Exception {
        // Test various error scenarios
        Map<String, String> errorScenarios = new HashMap<>();
        errorScenarios.put("empty_content", "");
        errorScenarios.put("very_long_content", "A".repeat(100000)); // Very long content
        errorScenarios.put("special_unicode", "🚀🔬💻📚📖📝🎯✅❌🔥💡⭐".repeat(1000));
        errorScenarios.put("control_characters", "\u0000\u0001\u0002\u0003".repeat(100));
        
        for (Map.Entry<String, String> scenario : errorScenarios.entrySet()) {
            PipeDoc inputDoc = PipeDoc.newBuilder()
                    .setId("error-recovery-" + scenario.getKey())
                    .setTitle("Error Recovery Test")
                    .setBody(scenario.getValue())
                    .build();
            
            ProcessRequest request = createProcessRequestWithDefaultConfig("error-pipeline", "embedder-error", inputDoc);
            
            ProcessResponse response = blockingClient.processData(request);
            
            // Should handle gracefully - either succeed or provide meaningful error
            assertNotNull(response, "Response should not be null for scenario: " + scenario.getKey());
            
            if (!response.getSuccess()) {
                assertNotNull(response.getErrorDetails(), "Error details should be provided for: " + scenario.getKey());
                LOG.info("Error scenario '{}' handled gracefully: {}", scenario.getKey(), response.getErrorDetails());
            } else {
                LOG.info("Error scenario '{}' processed successfully", scenario.getKey());
            }
        }
        
        LOG.info("✅ Error recovery test completed - all scenarios handled appropriately");
    }

    @Test
    @Order(19)
    @DisplayName("High Throughput Test - Should handle high-volume embedding requests")
    void testHighThroughputTest() throws Exception {
        int requestCount = 100;
        Thread[] threads = new Thread[10]; // 10 concurrent threads
        int requestsPerThread = requestCount / threads.length;
        boolean[] results = new boolean[threads.length];
        long[] processingTimes = new long[threads.length];
        
        for (int i = 0; i < threads.length; i++) {
            final int threadIndex = i;
            threads[i] = new Thread(() -> {
                try {
                    long threadStartTime = System.currentTimeMillis();
                    int successCount = 0;
                    
                    for (int j = 0; j < requestsPerThread; j++) {
                        PipeDoc inputDoc = PipeDoc.newBuilder()
                                .setId("throughput-" + threadIndex + "-" + j)
                                .setTitle("Throughput Test Document")
                                .setBody("High throughput test document for thread " + threadIndex + " request " + j)
                                .build();
                        
                        ProcessRequest request = createProcessRequestWithDefaultConfig("throughput-pipeline", 
                                "embedder-throughput", inputDoc);
                        
                        ProcessResponse response = blockingClient.processData(request);
                        
                        if (response.getSuccess() && response.getOutputDoc().getNamedEmbeddingsCount() > 0) {
                            successCount++;
                        }
                    }
                    
                    processingTimes[threadIndex] = System.currentTimeMillis() - threadStartTime;
                    results[threadIndex] = successCount >= (requestsPerThread * 0.9); // 90% success rate
                    
                    LOG.info("Thread {} completed {}/{} requests in {}ms", 
                            threadIndex, successCount, requestsPerThread, processingTimes[threadIndex]);
                } catch (Exception e) {
                    LOG.error("High throughput test failed for thread {}", threadIndex, e);
                    results[threadIndex] = false;
                }
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
        
        // Calculate success rate
        int successfulThreads = 0;
        for (boolean result : results) {
            if (result) successfulThreads++;
        }
        
        double successRate = (double) successfulThreads / threads.length * 100;
        double throughput = (double) requestCount / (totalTestTime / 1000.0); // requests per second
        
        assertTrue(successRate >= 80, "High throughput test success rate should be at least 80%");
        
        LOG.info("✅ High throughput test completed - Success rate: {:.1f}%, Throughput: {:.2f} req/sec", 
                successRate, throughput);
    }

    @Test
    @Order(20)
    @DisplayName("Vector Dimension Validation Test - Should validate embedding dimensions")
    void testVectorDimensionValidationTest() throws Exception {
        PipeDoc inputDoc = PipeDoc.newBuilder()
                .setId("dimension-test")
                .setTitle("Vector Dimension Test")
                .setBody("Testing vector dimension consistency and validation across different content sizes")
                .build();
        
        ProcessRequest request = createProcessRequestWithDefaultConfig("dimension-pipeline", "embedder-dimension", inputDoc);
        
        ProcessResponse response = blockingClient.processData(request);
        
        assertTrue(response.getSuccess(), "Vector dimension test should succeed");
        
        PipeDoc outputDoc = response.getOutputDoc();
        assertTrue(outputDoc.getNamedEmbeddingsCount() > 0, "Should have embeddings");
        
        // Verify all embeddings have consistent dimensions
        int expectedDimensions = -1;
        for (NamedEmbedding embedding : outputDoc.getNamedEmbeddingsMap().values()) {
            int vectorCount = embedding.getVectorCount();
            assertTrue(vectorCount > 0, "Embedding should have vectors");
            
            if (expectedDimensions == -1) {
                expectedDimensions = vectorCount;
            } else {
                assertEquals(expectedDimensions, vectorCount, 
                        "All embeddings should have consistent dimensions");
            }
            
            // Verify vector values are reasonable (not all zeros or NaN)
            boolean hasNonZeroValue = false;
            for (int i = 0; i < Math.min(10, vectorCount); i++) {
                float value = embedding.getVector(i);
                assertFalse(Float.isNaN(value), "Vector values should not be NaN");
                assertFalse(Float.isInfinite(value), "Vector values should not be infinite");
                if (value != 0.0f) hasNonZeroValue = true;
            }
            assertTrue(hasNonZeroValue, "Embedding should have non-zero values");
        }
        
        LOG.info("✅ Vector dimension validation successful - all embeddings have {} dimensions", expectedDimensions);
    }

    @Test
    @Order(21)
    @DisplayName("Cross-Language Embedding Test - Should handle multilingual content")
    void testCrossLanguageEmbeddingTest() throws Exception {
        // Test with various languages
        Map<String, String> languageTests = new HashMap<>();
        languageTests.put("english", "This is a test document in English for cross-language embedding validation.");
        languageTests.put("spanish", "Este es un documento de prueba en español para validación de embeddings multiidioma.");
        languageTests.put("french", "Ceci est un document de test en français pour la validation des embeddings multilingues.");
        languageTests.put("german", "Dies ist ein Testdokument auf Deutsch für die Validierung mehrsprachiger Embeddings.");
        languageTests.put("chinese", "这是一个中文测试文档，用于跨语言嵌入验证。");
        languageTests.put("japanese", "これは、言語横断的な埋め込み検証のための日本語のテスト文書です。");
        languageTests.put("arabic", "هذا مستند اختبار باللغة العربية للتحقق من التضمين متعدد اللغات.");
        
        for (Map.Entry<String, String> test : languageTests.entrySet()) {
            PipeDoc inputDoc = PipeDoc.newBuilder()
                    .setId("lang-test-" + test.getKey())
                    .setTitle("Cross-Language Test - " + test.getKey())
                    .setBody(test.getValue())
                    .build();
            
            ProcessRequest request = createProcessRequestWithDefaultConfig("lang-pipeline", "embedder-lang", inputDoc);
            
            ProcessResponse response = blockingClient.processData(request);
            
            assertTrue(response.getSuccess(), "Cross-language test should succeed for " + test.getKey());
            
            PipeDoc outputDoc = response.getOutputDoc();
            assertTrue(outputDoc.getNamedEmbeddingsCount() > 0, "Should have embeddings for " + test.getKey());
            
            // Verify embeddings are generated with reasonable values
            for (NamedEmbedding embedding : outputDoc.getNamedEmbeddingsMap().values()) {
                assertTrue(embedding.getVectorCount() > 0, "Should have vector dimensions for " + test.getKey());
                
                // Check for reasonable vector values (not all same value)
                Set<Float> uniqueValues = new HashSet<>();
                for (int i = 0; i < Math.min(10, embedding.getVectorCount()); i++) {
                    uniqueValues.add(embedding.getVector(i));
                }
                assertTrue(uniqueValues.size() > 1, "Embeddings should have varied values for " + test.getKey());
            }
            
            LOG.info("✅ Cross-language test successful for {} - {} embeddings created", 
                    test.getKey(), outputDoc.getNamedEmbeddingsCount());
        }
    }

    @Test
    @Order(22)
    @DisplayName("Chunk Processing Stress Test - Should handle large semantic chunks")
    void testChunkProcessingStressTest() throws Exception {
        // Create document with many semantic chunks (simulate chunker output)
        List<SemanticChunk> chunks = new ArrayList<>();
        
        for (int i = 0; i < 100; i++) {
            ChunkEmbedding chunkEmbedding = ChunkEmbedding.newBuilder()
                    .setTextContent("This is chunk number " + i + " with substantial content for stress testing. " +
                                   "Each chunk should be processed independently and efficiently. ".repeat(5))
                    .setChunkId("stress-chunk-" + i)
                    .setChunkConfigId("stress_test_chunker")
                    .setOriginalCharStartOffset(i * 100)
                    .setOriginalCharEndOffset((i + 1) * 100 - 1)
                    .build();
            
            SemanticChunk chunk = SemanticChunk.newBuilder()
                    .setChunkId("stress-chunk-" + i)
                    .setChunkNumber(i)
                    .setEmbeddingInfo(chunkEmbedding)
                    .build();
            
            chunks.add(chunk);
        }
        
        // Create semantic processing result
        SemanticProcessingResult result = SemanticProcessingResult.newBuilder()
                .setResultId(UUID.randomUUID().toString())
                .setSourceFieldName("body")
                .setChunkConfigId("stress_test_chunker")
                .addAllChunks(chunks)
                .build();
        
        PipeDoc inputDoc = PipeDoc.newBuilder()
                .setId("chunk-stress-test")
                .setTitle("Chunk Processing Stress Test")
                .setBody("Large document with many chunks for stress testing")
                .addSemanticResults(result)
                .build();
        
        ProcessRequest request = createProcessRequestWithDefaultConfig("chunk-stress-pipeline", "embedder-chunk-stress", inputDoc);
        
        long startTime = System.currentTimeMillis();
        ProcessResponse response = blockingClient.processData(request);
        long processingTime = System.currentTimeMillis() - startTime;
        
        assertTrue(response.getSuccess(), "Chunk processing stress test should succeed");
        
        PipeDoc outputDoc = response.getOutputDoc();
        assertTrue(outputDoc.getSemanticResultsCount() > 0, "Should have semantic results");
        
        // Find the embedded result
        SemanticProcessingResult embeddedResult = null;
        for (SemanticProcessingResult embeddedRes : outputDoc.getSemanticResultsList()) {
            if (!embeddedRes.getEmbeddingConfigId().isEmpty()) {
                embeddedResult = embeddedRes;
                break;
            }
        }
        
        assertNotNull(embeddedResult, "Should have embedded semantic result");
        assertEquals(100, embeddedResult.getChunksCount(), "Should have embedded all chunks");
        
        // Verify all chunks have embeddings
        for (SemanticChunk chunk : embeddedResult.getChunksList()) {
            assertTrue(chunk.getEmbeddingInfo().getVectorCount() > 0, "Each chunk should have vectors");
        }
        
        LOG.info("✅ Chunk processing stress test successful - processed {} chunks in {}ms", 
                embeddedResult.getChunksCount(), processingTime);
        
        // Performance verification
        assertTrue(processingTime < 120000, "Chunk stress test should complete within 2 minutes");
    }

    @Test
    @Order(23)
    @DisplayName("Resource Cleanup Test - Should properly clean up embedding resources")
    void testResourceCleanupTest() throws Exception {
        Runtime runtime = Runtime.getRuntime();
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();
        
        // Process many documents to verify resource cleanup
        for (int i = 0; i < 50; i++) {
            String content = "Resource cleanup test document " + i + " with varying content sizes. ".repeat(i * 10 + 50);
            
            PipeDoc inputDoc = PipeDoc.newBuilder()
                    .setId("cleanup-test-" + i)
                    .setTitle("Resource Cleanup Test " + i)
                    .setBody(content)
                    .build();
            
            ProcessRequest request = createProcessRequestWithDefaultConfig("cleanup-pipeline", "embedder-cleanup", inputDoc);
            
            ProcessResponse response = blockingClient.processData(request);
            
            assertTrue(response.getSuccess(), "Resource cleanup test should succeed for document " + i);
            
            // Periodic cleanup
            if (i % 10 == 0) {
                System.gc();
                Thread.sleep(100);
            }
        }
        
        // Final cleanup and memory check
        Thread.sleep(2000);
        System.gc();
        Thread.sleep(2000);
        
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryIncrease = finalMemory - initialMemory;
        
        LOG.info("✅ Resource cleanup test completed - Memory increase: {}MB", memoryIncrease / (1024 * 1024));
        
        // Memory increase should be reasonable
        assertTrue(memoryIncrease < 300 * 1024 * 1024, "Memory increase should be less than 300MB after cleanup");
    }

    // Helper Methods

    private ProcessRequest createProcessRequestWithDefaultConfig(String pipelineName, String stepName, PipeDoc document) throws Exception {
        EmbedderOptions options = new EmbedderOptions();
        return createProcessRequestWithCustomConfig(pipelineName, stepName, document, options);
    }
    
    private ProcessRequest createProcessRequestWithCustomConfig(String pipelineName, String stepName, 
                                                               PipeDoc document, EmbedderOptions options) throws Exception {
        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setPipelineName(pipelineName)
                .setPipeStepName(stepName)
                .setStreamId("stream-" + System.currentTimeMillis())
                .setCurrentHopNumber(1)
                .build();
        
        // Convert options to JSON
        String optionsJson = objectMapper.writeValueAsString(options);
        
        // Create custom JSON config
        Struct.Builder customJsonConfigBuilder = Struct.newBuilder();
        JsonFormat.parser().merge(optionsJson, customJsonConfigBuilder);
        
        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .setCustomJsonConfig(customJsonConfigBuilder.build())
                .build();
        
        return ProcessRequest.newBuilder()
                .setDocument(document)
                .setConfig(config)
                .setMetadata(metadata)
                .build();
    }
    
    private PipeDoc createDocumentWithChunks() {
        // Create chunk embeddings
        ChunkEmbedding chunk1 = ChunkEmbedding.newBuilder()
                .setTextContent("This is the first chunk of text for embedding testing.")
                .setChunkId("chunk-1")
                .setChunkConfigId("test_chunker")
                .setOriginalCharStartOffset(0)
                .setOriginalCharEndOffset(56)
                .build();
        
        ChunkEmbedding chunk2 = ChunkEmbedding.newBuilder()
                .setTextContent("This is the second chunk with different content.")
                .setChunkId("chunk-2")
                .setChunkConfigId("test_chunker")
                .setOriginalCharStartOffset(57)
                .setOriginalCharEndOffset(104)
                .build();
        
        // Create semantic chunks
        SemanticChunk semanticChunk1 = SemanticChunk.newBuilder()
                .setChunkId("chunk-1")
                .setChunkNumber(0)
                .setEmbeddingInfo(chunk1)
                .build();
        
        SemanticChunk semanticChunk2 = SemanticChunk.newBuilder()
                .setChunkId("chunk-2")
                .setChunkNumber(1)
                .setEmbeddingInfo(chunk2)
                .build();
        
        // Create semantic processing result
        SemanticProcessingResult result = SemanticProcessingResult.newBuilder()
                .setResultId(UUID.randomUUID().toString())
                .setSourceFieldName("body")
                .setChunkConfigId("test_chunker")
                .addChunks(semanticChunk1)
                .addChunks(semanticChunk2)
                .build();
        
        // Create the document
        return PipeDoc.newBuilder()
                .setId("chunked-document-test")
                .setTitle("Document with Chunks")
                .setBody("This is the first chunk of text for embedding testing. This is the second chunk with different content.")
                .addSemanticResults(result)
                .build();
    }
    
    private PipeDoc createChunkerOutputDocument() {
        // Simulate more realistic chunker output
        return PipeDoc.newBuilder()
                .setId("chunker-output-test")
                .setTitle("Machine Learning Applications")
                .setBody("Machine learning has revolutionized many industries. Deep learning networks can process complex data patterns. Natural language processing enables human-computer interaction.")
                .addSemanticResults(
                    SemanticProcessingResult.newBuilder()
                        .setResultId("chunker-result-1")
                        .setSourceFieldName("body")
                        .setChunkConfigId("default_chunker")
                        .addChunks(
                            SemanticChunk.newBuilder()
                                .setChunkId("chunk_0")
                                .setChunkNumber(0)
                                .setEmbeddingInfo(
                                    ChunkEmbedding.newBuilder()
                                        .setTextContent("Machine learning has revolutionized many industries.")
                                        .setChunkConfigId("default_chunker")
                                        .setOriginalCharStartOffset(0)
                                        .setOriginalCharEndOffset(49)
                                        .build()
                                )
                                .build()
                        )
                        .addChunks(
                            SemanticChunk.newBuilder()
                                .setChunkId("chunk_1")
                                .setChunkNumber(1)
                                .setEmbeddingInfo(
                                    ChunkEmbedding.newBuilder()
                                        .setTextContent("Deep learning networks can process complex data patterns.")
                                        .setChunkConfigId("default_chunker")
                                        .setOriginalCharStartOffset(50)
                                        .setOriginalCharEndOffset(104)
                                        .build()
                                )
                                .build()
                        )
                        .build()
                )
                .build();
    }
    
    private String generateTestContent(int wordCount) {
        StringBuilder sb = new StringBuilder();
        String[] words = {"machine", "learning", "artificial", "intelligence", "neural", "network", 
                         "data", "processing", "algorithm", "model", "training", "prediction"};
        
        for (int i = 0; i < wordCount; i++) {
            sb.append(words[i % words.length]).append(" ");
            if ((i + 1) % 15 == 0) {
                sb.append(". ");
            }
            if ((i + 1) % 60 == 0) {
                sb.append("\n\n");
            }
        }
        
        return sb.toString();
    }
}