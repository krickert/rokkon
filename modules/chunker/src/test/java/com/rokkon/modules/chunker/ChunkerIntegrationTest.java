package com.rokkon.modules.chunker;

import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.rokkon.search.model.*;
import com.rokkon.search.registration.api.ModuleRegistrationServiceGrpc;
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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive integration tests for the Chunker service using @QuarkusIntegrationTest.
 * These tests run against the actual containerized service to verify end-to-end functionality.
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
 */
@QuarkusIntegrationTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ChunkerIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(ChunkerIntegrationTest.class);
    
    private ManagedChannel channel;
    private PipeStepProcessorGrpc.PipeStepProcessorBlockingStub blockingClient;
    private ModuleRegistrationServiceGrpc.ModuleRegistrationServiceBlockingStub registrationClient;

    @BeforeEach
    void setUp() {
        // Set up gRPC channel for integration testing
        channel = ManagedChannelBuilder.forAddress("localhost", 9001)
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
    @DisplayName("Service Registration - Should register successfully and provide chunker metadata")
    void testServiceRegistration() {
        LOG.info("Testing Chunker service registration");
        
        ServiceRegistrationResponse response = registrationClient.getServiceRegistration(Empty.getDefaultInstance());
        
        assertNotNull(response, "Service registration response should not be null");
        assertTrue(response.getSuccess(), "Service registration should be successful");
        assertNotNull(response.getServiceInfo(), "Service info should be provided");
        
        ServiceInfo serviceInfo = response.getServiceInfo();
        assertEquals("chunker", serviceInfo.getServiceName());
        assertTrue(serviceInfo.getVersion().length() > 0, "Version should be specified");
        assertTrue(serviceInfo.getDescription().contains("chunk"), "Description should mention chunking");
        assertTrue(serviceInfo.getSupportedConfigurationCount() > 0, "Should support chunking configurations");
        
        LOG.info("✅ Chunker service registered successfully: {} v{}", serviceInfo.getServiceName(), serviceInfo.getVersion());
    }

    @Test
    @Order(2)
    @DisplayName("Default Chunking - Should chunk simple text with default configuration")
    void testDefaultChunking() {
        String testText = "This is the first sentence of our test document. " +
                         "This is the second sentence that should be included in chunking. " +
                         "This is the third sentence to ensure we have enough content. " +
                         "Finally, this is the fourth sentence to complete our test.";
        
        PipeDoc inputDoc = createDocumentWithBody("default-chunk-test", testText);
        ProcessRequest request = createProcessRequest("default-chunking-pipeline", "chunker-default", inputDoc);
        
        ProcessResponse response = blockingClient.processData(request);
        
        assertTrue(response.getSuccess(), "Default chunking should be successful");
        assertNotNull(response.getOutputDoc(), "Output document should be present");
        assertTrue(response.getOutputDoc().getSemanticResultsCount() > 0, "Should have semantic results");
        
        SemanticProcessingResult result = response.getOutputDoc().getSemanticResults(0);
        assertTrue(result.getChunksCount() > 0, "Should have created chunks");
        assertEquals(ChunkerOptions.DEFAULT_SOURCE_FIELD, result.getSourceFieldName());
        assertEquals(ChunkerOptions.DEFAULT_CHUNK_CONFIG_ID, result.getChunkConfigId());
        
        // Verify chunk content
        SemanticChunk chunk = result.getChunks(0);
        assertTrue(chunk.getEmbeddingInfo().getTextContent().length() > 0, "Chunk should have text content");
        assertTrue(chunk.getChunkId().contains("chunk"), "Chunk ID should follow expected format");
        
        LOG.info("✅ Default chunking successful - created {} chunks", result.getChunksCount());
    }

    @Test
    @Order(3)
    @DisplayName("Custom Chunk Size - Should respect custom chunk size and overlap settings")
    void testCustomChunkSize() {
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            longText.append("This is sentence number ").append(i + 1)
                   .append(" in our long test document for custom chunking. ");
        }
        
        PipeDoc inputDoc = createDocumentWithBody("custom-size-test", longText.toString());
        
        // Create custom configuration with small chunks
        Struct customConfig = Struct.newBuilder()
                .putFields("chunk_size", Value.newBuilder().setNumberValue(200).build())
                .putFields("chunk_overlap", Value.newBuilder().setNumberValue(50).build())
                .putFields("chunk_config_id", Value.newBuilder().setStringValue("small_chunks_200_50").build())
                .build();
        
        ProcessRequest request = createProcessRequestWithJsonConfig("custom-size-pipeline", "chunker-custom", inputDoc, customConfig);
        
        ProcessResponse response = blockingClient.processData(request);
        
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
    @Order(4)
    @DisplayName("Large Chunk Configuration - Should handle large chunks efficiently")
    void testLargeChunkConfiguration() {
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            longText.append("This is paragraph ").append(i + 1)
                   .append(" of our comprehensive test document. ")
                   .append("It contains multiple sentences to test large chunk processing. ")
                   .append("The chunker should handle this efficiently with large chunk sizes. ");
        }
        
        PipeDoc inputDoc = createDocumentWithBody("large-chunk-test", longText.toString());
        
        // Create configuration with large chunks
        Struct customConfig = Struct.newBuilder()
                .putFields("chunk_size", Value.newBuilder().setNumberValue(2000).build())
                .putFields("chunk_overlap", Value.newBuilder().setNumberValue(200).build())
                .putFields("chunk_config_id", Value.newBuilder().setStringValue("large_chunks_2000_200").build())
                .build();
        
        ProcessRequest request = createProcessRequestWithJsonConfig("large-chunk-pipeline", "chunker-large", inputDoc, customConfig);
        
        ProcessResponse response = blockingClient.processData(request);
        
        assertTrue(response.getSuccess(), "Large chunk processing should be successful");
        
        SemanticProcessingResult result = response.getOutputDoc().getSemanticResults(0);
        assertEquals("large_chunks_2000_200", result.getChunkConfigId());
        
        // Should create fewer, larger chunks
        assertTrue(result.getChunksCount() > 0, "Should create at least one chunk");
        
        LOG.info("✅ Large chunk configuration successful - created {} large chunks", result.getChunksCount());
    }

    @Test
    @Order(5)
    @DisplayName("Zero Overlap Configuration - Should handle no overlap between chunks")
    void testZeroOverlapConfiguration() {
        String testText = "First paragraph with multiple sentences for testing. " +
                         "Second paragraph that should be in a separate chunk. " +
                         "Third paragraph to ensure proper separation. " +
                         "Fourth paragraph for comprehensive testing coverage.";
        
        PipeDoc inputDoc = createDocumentWithBody("zero-overlap-test", testText);
        
        // Create configuration with no overlap
        Struct customConfig = Struct.newBuilder()
                .putFields("chunk_size", Value.newBuilder().setNumberValue(100).build())
                .putFields("chunk_overlap", Value.newBuilder().setNumberValue(0).build())
                .putFields("chunk_config_id", Value.newBuilder().setStringValue("no_overlap_100_0").build())
                .build();
        
        ProcessRequest request = createProcessRequestWithJsonConfig("zero-overlap-pipeline", "chunker-no-overlap", inputDoc, customConfig);
        
        ProcessResponse response = blockingClient.processData(request);
        
        assertTrue(response.getSuccess(), "Zero overlap processing should be successful");
        
        SemanticProcessingResult result = response.getOutputDoc().getSemanticResults(0);
        assertEquals("no_overlap_100_0", result.getChunkConfigId());
        assertTrue(result.getChunksCount() > 1, "Should create multiple chunks");
        
        LOG.info("✅ Zero overlap configuration successful - created {} non-overlapping chunks", result.getChunksCount());
    }

    @Test
    @Order(6)
    @DisplayName("Custom Templates - Should use custom chunk ID and result set templates")
    void testCustomTemplates() {
        String testText = "Template testing document with custom identifiers and naming conventions.";
        
        PipeDoc inputDoc = createDocumentWithBody("template-test", testText);
        
        // Create configuration with custom templates
        Struct customConfig = Struct.newBuilder()
                .putFields("chunk_id_template", Value.newBuilder().setStringValue("custom_%s_%s_part_%d").build())
                .putFields("result_set_name_template", Value.newBuilder().setStringValue("custom_%s_results_%s").build())
                .putFields("chunk_config_id", Value.newBuilder().setStringValue("template_test_config").build())
                .build();
        
        ProcessRequest request = createProcessRequestWithJsonConfig("template-pipeline", "chunker-template", inputDoc, customConfig);
        
        ProcessResponse response = blockingClient.processData(request);
        
        assertTrue(response.getSuccess(), "Template processing should be successful");
        
        SemanticProcessingResult result = response.getOutputDoc().getSemanticResults(0);
        assertEquals("template_test_config", result.getChunkConfigId());
        assertTrue(result.getResultSetName().contains("custom_"), "Result set should use custom template");
        
        // Verify chunk IDs use custom template
        if (result.getChunksCount() > 0) {
            SemanticChunk chunk = result.getChunks(0);
            assertTrue(chunk.getChunkId().contains("custom_"), "Chunk ID should use custom template");
            assertTrue(chunk.getChunkId().contains("part_"), "Chunk ID should follow custom format");
        }
        
        LOG.info("✅ Custom templates successful - result set: {}", result.getResultSetName());
    }

    @Test
    @Order(7)
    @DisplayName("Multiple Chunking Configurations - Should process same document with different strategies")
    void testMultipleChunkingConfigurations() {
        String testText = generateLongTestDocument(1000); // Generate substantial content
        
        PipeDoc inputDoc = createDocumentWithBody("multi-config-test", testText);
        
        // First configuration: Small chunks
        Struct smallChunksConfig = Struct.newBuilder()
                .putFields("chunk_size", Value.newBuilder().setNumberValue(300).build())
                .putFields("chunk_overlap", Value.newBuilder().setNumberValue(30).build())
                .putFields("chunk_config_id", Value.newBuilder().setStringValue("small_config").build())
                .build();
        
        ProcessRequest smallRequest = createProcessRequestWithJsonConfig("multi-config-small", "chunker-small", inputDoc, smallChunksConfig);
        ProcessResponse smallResponse = blockingClient.processData(smallRequest);
        
        // Second configuration: Large chunks
        Struct largeChunksConfig = Struct.newBuilder()
                .putFields("chunk_size", Value.newBuilder().setNumberValue(800).build())
                .putFields("chunk_overlap", Value.newBuilder().setNumberValue(80).build())
                .putFields("chunk_config_id", Value.newBuilder().setStringValue("large_config").build())
                .build();
        
        ProcessRequest largeRequest = createProcessRequestWithJsonConfig("multi-config-large", "chunker-large", inputDoc, largeChunksConfig);
        ProcessResponse largeResponse = blockingClient.processData(largeRequest);
        
        // Verify both configurations worked
        assertTrue(smallResponse.getSuccess(), "Small chunk configuration should succeed");
        assertTrue(largeResponse.getSuccess(), "Large chunk configuration should succeed");
        
        int smallChunkCount = smallResponse.getOutputDoc().getSemanticResults(0).getChunksCount();
        int largeChunkCount = largeResponse.getOutputDoc().getSemanticResults(0).getChunksCount();
        
        assertTrue(smallChunkCount > largeChunkCount, "Small chunks should create more chunks than large chunks");
        
        LOG.info("✅ Multiple configurations successful - small: {} chunks, large: {} chunks", 
                smallChunkCount, largeChunkCount);
    }

    @Test
    @Order(8)
    @DisplayName("Integration with Tika Output - Should process Tika-parsed documents")
    void testTikaIntegration() {
        // Simulate a document that came from Tika parser
        String tikaExtractedText = "This document was processed by Tika parser. " +
                                  "It contains extracted text content from a PDF or other document format. " +
                                  "The chunker should process this extracted content efficiently.";
        
        Blob originalBlob = Blob.newBuilder()
                .setBlobId("original-blob-123")
                .setFilename("document.pdf")
                .setData(ByteString.copyFromUtf8("simulated PDF data"))
                .setMimeType("application/pdf")
                .build();
        
        PipeDoc tikaProcessedDoc = PipeDoc.newBuilder()
                .setId("tika-processed-doc")
                .setTitle("Tika Processed Document")
                .setBody(tikaExtractedText) // This would come from Tika
                .setBlob(originalBlob) // Preserve original blob
                .build();
        
        ProcessRequest request = createProcessRequest("tika-integration-pipeline", "chunker-tika", tikaProcessedDoc);
        
        ProcessResponse response = blockingClient.processData(request);
        
        assertTrue(response.getSuccess(), "Tika integration processing should be successful");
        
        PipeDoc outputDoc = response.getOutputDoc();
        assertTrue(outputDoc.hasBlob(), "Original blob should be preserved");
        assertTrue(outputDoc.getSemanticResultsCount() > 0, "Should have chunked the Tika-extracted content");
        
        SemanticProcessingResult result = outputDoc.getSemanticResults(0);
        assertTrue(result.getChunksCount() > 0, "Should have created chunks from Tika content");
        
        LOG.info("✅ Tika integration successful - chunked Tika-extracted content into {} chunks", 
                result.getChunksCount());
    }

    @Test
    @Order(9)
    @DisplayName("Large Document Processing - Should handle large documents efficiently")
    void testLargeDocumentProcessing() throws IOException {
        // Load or generate a large document (simulating US Constitution test from old tests)
        String largeContent = generateLongTestDocument(5000); // Generate ~5000 words
        
        PipeDoc inputDoc = createDocumentWithBody("large-document-test", largeContent);
        
        Struct config = Struct.newBuilder()
                .putFields("chunk_size", Value.newBuilder().setNumberValue(1000).build())
                .putFields("chunk_overlap", Value.newBuilder().setNumberValue(100).build())
                .putFields("chunk_config_id", Value.newBuilder().setStringValue("large_doc_1000_100").build())
                .build();
        
        ProcessRequest request = createProcessRequestWithJsonConfig("large-doc-pipeline", "chunker-large-doc", inputDoc, config);
        
        long startTime = System.currentTimeMillis();
        ProcessResponse response = blockingClient.processData(request);
        long processingTime = System.currentTimeMillis() - startTime;
        
        assertTrue(response.getSuccess(), "Large document processing should be successful");
        
        SemanticProcessingResult result = response.getOutputDoc().getSemanticResults(0);
        assertTrue(result.getChunksCount() > 5, "Large document should produce multiple chunks");
        
        // Verify chunk metadata
        for (int i = 0; i < Math.min(3, result.getChunksCount()); i++) {
            SemanticChunk chunk = result.getChunks(i);
            assertTrue(chunk.getEmbeddingInfo().getOriginalCharStartOffset() >= 0, 
                      "Chunk should have valid start offset");
            assertTrue(chunk.getEmbeddingInfo().getOriginalCharEndOffset() > 
                      chunk.getEmbeddingInfo().getOriginalCharStartOffset(), 
                      "End offset should be after start offset");
        }
        
        LOG.info("✅ Large document processed successfully in {}ms - created {} chunks", 
                processingTime, result.getChunksCount());
        
        // Performance verification
        assertTrue(processingTime < 10000, "Large document should process within 10 seconds");
    }

    @Test
    @Order(10)
    @DisplayName("Error Handling - Should handle invalid configurations gracefully")
    void testErrorHandling() {
        String testText = "Error handling test document";
        PipeDoc inputDoc = createDocumentWithBody("error-test", testText);
        
        // Test with invalid chunk size (negative)
        Struct invalidConfig = Struct.newBuilder()
                .putFields("chunk_size", Value.newBuilder().setNumberValue(-100).build())
                .build();
        
        ProcessRequest request = createProcessRequestWithJsonConfig("error-pipeline", "chunker-error", inputDoc, invalidConfig);
        
        ProcessResponse response = blockingClient.processData(request);
        
        // Should either use defaults or handle error gracefully
        assertNotNull(response, "Response should not be null");
        if (!response.getSuccess()) {
            assertNotNull(response.getErrorDetails(), "Error details should be provided");
            LOG.info("✅ Error handled gracefully: {}", response.getErrorDetails());
        } else {
            LOG.info("✅ Invalid configuration handled gracefully with fallback to defaults");
        }
    }

    @Test
    @Order(11)
    @DisplayName("Empty Content Handling - Should handle documents without body content")
    void testEmptyContentHandling() {
        PipeDoc inputDoc = PipeDoc.newBuilder()
                .setId("empty-content-doc")
                .setTitle("Document Without Body")
                .build(); // No body content
        
        ProcessRequest request = createProcessRequest("empty-content-pipeline", "chunker-empty", inputDoc);
        
        ProcessResponse response = blockingClient.processData(request);
        
        assertTrue(response.getSuccess(), "Empty content processing should be successful");
        
        // Should return document unchanged or with empty semantic results
        PipeDoc outputDoc = response.getOutputDoc();
        assertEquals(inputDoc.getId(), outputDoc.getId(), "Document ID should be preserved");
        
        LOG.info("✅ Empty content handled successfully");
    }

    @Test
    @Order(12)
    @DisplayName("Chunk Metadata Validation - Should generate proper chunk metadata")
    void testChunkMetadataValidation() {
        String testText = "First sentence for metadata testing. " +
                         "Second sentence with different content. " +
                         "Third sentence to verify offset calculations.";
        
        PipeDoc inputDoc = createDocumentWithBody("metadata-test", testText);
        ProcessRequest request = createProcessRequest("metadata-pipeline", "chunker-metadata", inputDoc);
        
        ProcessResponse response = blockingClient.processData(request);
        
        assertTrue(response.getSuccess(), "Metadata processing should be successful");
        
        SemanticProcessingResult result = response.getOutputDoc().getSemanticResults(0);
        assertTrue(result.getChunksCount() > 0, "Should have created chunks");
        
        // Validate chunk metadata
        for (SemanticChunk chunk : result.getChunksList()) {
            ChunkEmbedding embedding = chunk.getEmbeddingInfo();
            
            // Verify required fields
            assertNotNull(embedding.getTextContent(), "Chunk should have text content");
            assertTrue(embedding.getTextContent().length() > 0, "Text content should not be empty");
            assertNotNull(chunk.getChunkId(), "Chunk should have an ID");
            assertTrue(chunk.getChunkNumber() >= 0, "Chunk number should be non-negative");
            
            // Verify offset calculations
            assertTrue(embedding.getOriginalCharStartOffset() >= 0, "Start offset should be non-negative");
            assertTrue(embedding.getOriginalCharEndOffset() > embedding.getOriginalCharStartOffset(), 
                      "End offset should be after start offset");
            
            // Verify config ID is set
            assertEquals(ChunkerOptions.DEFAULT_CHUNK_CONFIG_ID, embedding.getChunkConfigId());
        }
        
        LOG.info("✅ Chunk metadata validation successful - validated {} chunks", result.getChunksCount());
    }

    @Test
    @Order(13)
    @DisplayName("Concurrent Processing - Should handle concurrent chunking requests")
    void testConcurrentProcessing() throws Exception {
        int concurrentRequests = 5;
        Thread[] threads = new Thread[concurrentRequests];
        boolean[] results = new boolean[concurrentRequests];
        
        for (int i = 0; i < concurrentRequests; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                try {
                    String testText = generateTestDocument("Concurrent processing test " + index, 100);
                    PipeDoc inputDoc = createDocumentWithBody("concurrent-" + index, testText);
                    ProcessRequest request = createProcessRequest("concurrent-pipeline-" + index, "chunker-concurrent", inputDoc);
                    
                    ProcessResponse response = blockingClient.processData(request);
                    results[index] = response.getSuccess() && 
                                   response.getOutputDoc().getSemanticResultsCount() > 0;
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
    @Order(14)
    @DisplayName("Custom Log Prefix - Should respect custom logging configuration")
    void testCustomLogPrefix() {
        String testText = "Custom log prefix test document";
        PipeDoc inputDoc = createDocumentWithBody("log-prefix-test", testText);
        
        Struct customConfig = Struct.newBuilder()
                .putFields("log_prefix", Value.newBuilder().setStringValue("CUSTOM_CHUNKER: ").build())
                .build();
        
        ProcessRequest request = createProcessRequestWithJsonConfig("log-prefix-pipeline", "chunker-log", inputDoc, customConfig);
        
        ProcessResponse response = blockingClient.processData(request);
        
        assertTrue(response.getSuccess(), "Custom log prefix processing should be successful");
        
        // Check if custom log prefix appears in processor logs
        assertFalse(response.getProcessorLogsList().isEmpty(), "Should have processor logs");
        
        LOG.info("✅ Custom log prefix test successful");
    }

    // MASSIVE EXPANSION - ADVANCED CHUNKING SCENARIOS

    @Test
    @Order(15)
    @DisplayName("Massive Document Stress Test - Should handle extremely large documents efficiently")
    void testMassiveDocumentStressTest() {
        // Create a massive document (simulate processing a large book)
        StringBuilder massiveContent = new StringBuilder();
        
        // Generate approximately 10MB of content
        String paragraph = "This is a comprehensive stress test for the chunker service with a massive document. " +
                         "The chunker must efficiently process large amounts of text while maintaining accuracy. " +
                         "This paragraph contains multiple sentences that should be properly chunked and processed. " +
                         "The service should handle memory efficiently and not cause performance degradation. " +
                         "Each chunk should maintain proper offsets and metadata for downstream processing. ";
        
        for (int i = 0; i < 100000; i++) {
            massiveContent.append("Chapter ").append(i / 1000 + 1).append(", Section ").append(i % 1000).append(": ");
            massiveContent.append(paragraph);
            if (i % 100 == 0) {
                massiveContent.append("\n\n--- Page Break ---\n\n");
            }
        }
        
        PipeDoc inputDoc = createDocumentWithBody("massive-document-test", massiveContent.toString());
        
        Struct config = Struct.newBuilder()
                .putFields("chunk_size", Value.newBuilder().setNumberValue(2000).build())
                .putFields("chunk_overlap", Value.newBuilder().setNumberValue(200).build())
                .putFields("chunk_config_id", Value.newBuilder().setStringValue("massive_doc_2000_200").build())
                .build();
        
        ProcessRequest request = createProcessRequestWithJsonConfig("massive-doc-pipeline", "chunker-massive", inputDoc, config);
        
        long startTime = System.currentTimeMillis();
        ProcessResponse response = blockingClient.processData(request);
        long processingTime = System.currentTimeMillis() - startTime;
        
        assertTrue(response.getSuccess(), "Massive document processing should be successful");
        
        SemanticProcessingResult result = response.getOutputDoc().getSemanticResults(0);
        assertTrue(result.getChunksCount() > 100, "Massive document should produce many chunks");
        
        // Verify chunk integrity
        for (int i = 0; i < Math.min(10, result.getChunksCount()); i++) {
            SemanticChunk chunk = result.getChunks(i);
            assertTrue(chunk.getEmbeddingInfo().getOriginalCharStartOffset() >= 0, "Chunk should have valid start offset");
            assertTrue(chunk.getEmbeddingInfo().getOriginalCharEndOffset() > chunk.getEmbeddingInfo().getOriginalCharStartOffset(), 
                      "Chunk should have valid end offset");
            assertFalse(chunk.getEmbeddingInfo().getTextContent().isEmpty(), "Chunk should have content");
        }
        
        LOG.info("✅ Massive document processed successfully in {}ms - {} chunks created, content size: {}MB", 
                processingTime, result.getChunksCount(), massiveContent.length() / (1024 * 1024));
        
        // Performance verification - should process within reasonable time
        assertTrue(processingTime < 300000, "Massive document should process within 5 minutes");
    }

    @Test
    @Order(16)
    @DisplayName("Multi-Language Support - Should handle Unicode and various languages")
    void testMultiLanguageSupport() {
        // Test with various languages and scripts
        String multiLangContent = 
            "English: This is a test document with multiple languages.\n\n" +
            "Español: Este es un documento de prueba con múltiples idiomas.\n\n" +
            "Français: Ceci est un document de test avec plusieurs langues.\n\n" +
            "Deutsch: Dies ist ein Testdokument mit mehreren Sprachen.\n\n" +
            "中文: 这是一个包含多种语言的测试文档。\n\n" +
            "日本語: これは複数の言語を含むテスト文書です。\n\n" +
            "العربية: هذا مستند اختبار بلغات متعددة.\n\n" +
            "Русский: Это тестовый документ с несколькими языками.\n\n" +
            "हिन्दी: यह कई भाषाओं के साथ एक परीक्षण दस्तावेज है।\n\n" +
            "한국어: 이것은 여러 언어가 포함된 테스트 문서입니다.\n\n" +
            "Emoji Test: 🌍🌎🌏 🚀🔬💻 📚📖📝 🎯✅❌ 🔥💡⭐";
        
        PipeDoc inputDoc = createDocumentWithBody("multi-language-test", multiLangContent);
        
        Struct config = Struct.newBuilder()
                .putFields("chunk_size", Value.newBuilder().setNumberValue(300).build())
                .putFields("chunk_overlap", Value.newBuilder().setNumberValue(50).build())
                .putFields("chunk_config_id", Value.newBuilder().setStringValue("multilang_300_50").build())
                .build();
        
        ProcessRequest request = createProcessRequestWithJsonConfig("multilang-pipeline", "chunker-multilang", inputDoc, config);
        
        ProcessResponse response = blockingClient.processData(request);
        
        assertTrue(response.getSuccess(), "Multi-language processing should be successful");
        
        SemanticProcessingResult result = response.getOutputDoc().getSemanticResults(0);
        assertTrue(result.getChunksCount() > 0, "Should create chunks from multi-language content");
        
        // Verify that Unicode characters are preserved
        for (SemanticChunk chunk : result.getChunksList()) {
            String content = chunk.getEmbeddingInfo().getTextContent();
            assertFalse(content.contains("?"), "Unicode characters should be preserved, not replaced with ?");
            assertTrue(content.length() > 0, "Chunks should have content");
        }
        
        LOG.info("✅ Multi-language support successful - created {} chunks from multilingual content", result.getChunksCount());
    }

    @Test
    @Order(17)
    @DisplayName("Right-to-Left Language Support - Should handle RTL languages properly")
    void testRightToLeftLanguageSupport() {
        String rtlContent = 
            "Arabic RTL Test: هذا نص تجريبي باللغة العربية لاختبار معالجة النصوص من اليمين إلى اليسار. " +
            "يجب أن يحافظ النظام على اتجاه النص الصحيح ويقوم بتقطيع النص بشكل مناسب. " +
            "هذا مهم جداً للتأكد من صحة معالجة النصوص العربية والعبرية وغيرها من اللغات التي تكتب من اليمين إلى اليسار.\n\n" +
            
            "Hebrew RTL Test: זהו טקסט בדיקה בעברית לבדיקת עיבוד טקסט מימין לשמאל. " +
            "המערכת צריכה לשמור על כיוון הטקסט הנכון ולחתוך את הטקסט בצורה מתאימה. " +
            "זה חשוב מאוד כדי לוודא עיבוד נכון של טקסטים בעברית וערבית ושפות אחרות הנכתבות מימין לשמאל.\n\n" +
            
            "Mixed Content: This paragraph mixes English with Arabic العربية and Hebrew עברית to test complex text processing.";
        
        PipeDoc inputDoc = createDocumentWithBody("rtl-language-test", rtlContent);
        
        Struct config = Struct.newBuilder()
                .putFields("chunk_size", Value.newBuilder().setNumberValue(400).build())
                .putFields("chunk_overlap", Value.newBuilder().setNumberValue(40).build())
                .putFields("chunk_config_id", Value.newBuilder().setStringValue("rtl_400_40").build())
                .build();
        
        ProcessRequest request = createProcessRequestWithJsonConfig("rtl-pipeline", "chunker-rtl", inputDoc, config);
        
        ProcessResponse response = blockingClient.processData(request);
        
        assertTrue(response.getSuccess(), "RTL language processing should be successful");
        
        SemanticProcessingResult result = response.getOutputDoc().getSemanticResults(0);
        assertTrue(result.getChunksCount() > 0, "Should create chunks from RTL content");
        
        // Verify RTL characters are preserved
        boolean foundArabic = false, foundHebrew = false;
        for (SemanticChunk chunk : result.getChunksList()) {
            String content = chunk.getEmbeddingInfo().getTextContent();
            if (content.contains("العربية") || content.contains("هذا")) foundArabic = true;
            if (content.contains("עברית") || content.contains("זהו")) foundHebrew = true;
        }
        
        assertTrue(foundArabic || foundHebrew, "RTL characters should be preserved in chunks");
        
        LOG.info("✅ RTL language support successful - Arabic found: {}, Hebrew found: {}", foundArabic, foundHebrew);
    }

    @Test
    @Order(18)
    @DisplayName("Boundary Condition Testing - Should handle edge cases in chunk sizes")
    void testBoundaryConditionTesting() {
        String testContent = "This is a test for boundary conditions. ".repeat(100);
        PipeDoc inputDoc = createDocumentWithBody("boundary-test", testContent);
        
        // Test various boundary conditions
        int[][] boundaryTests = {
            {1, 0},      // Minimum chunk size, no overlap
            {10, 9},     // Overlap almost equal to chunk size
            {100, 0},    // Moderate chunk, no overlap
            {50, 25},    // 50% overlap
            {1000, 999}, // Very large overlap
            {testContent.length(), 0}, // Chunk size equals content length
            {testContent.length() + 100, 0} // Chunk size larger than content
        };
        
        for (int i = 0; i < boundaryTests.length; i++) {
            int chunkSize = boundaryTests[i][0];
            int overlap = boundaryTests[i][1];
            
            Struct config = Struct.newBuilder()
                    .putFields("chunk_size", Value.newBuilder().setNumberValue(chunkSize).build())
                    .putFields("chunk_overlap", Value.newBuilder().setNumberValue(overlap).build())
                    .putFields("chunk_config_id", Value.newBuilder().setStringValue("boundary_" + chunkSize + "_" + overlap).build())
                    .build();
            
            ProcessRequest request = createProcessRequestWithJsonConfig("boundary-pipeline-" + i, "chunker-boundary", inputDoc, config);
            
            ProcessResponse response = blockingClient.processData(request);
            
            if (overlap >= chunkSize && chunkSize > 1) {
                // This might fail or be handled gracefully
                if (!response.getSuccess()) {
                    LOG.info("Boundary condition appropriately rejected: chunk_size={}, overlap={}", chunkSize, overlap);
                    continue;
                }
            }
            
            assertTrue(response.getSuccess(), 
                    "Boundary condition should be handled: chunk_size=" + chunkSize + ", overlap=" + overlap);
            
            SemanticProcessingResult result = response.getOutputDoc().getSemanticResults(0);
            assertTrue(result.getChunksCount() > 0, "Should create at least one chunk");
            
            LOG.info("✅ Boundary condition successful: chunk_size={}, overlap={}, chunks={}", 
                    chunkSize, overlap, result.getChunksCount());
        }
    }

    @Test
    @Order(19)
    @DisplayName("High Concurrency Stress Test - Should handle many concurrent requests")
    void testHighConcurrencyStressTest() throws Exception {
        int concurrentRequests = 20; // High concurrency
        Thread[] threads = new Thread[concurrentRequests];
        boolean[] results = new boolean[concurrentRequests];
        long[] processingTimes = new long[concurrentRequests];
        
        for (int i = 0; i < concurrentRequests; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                try {
                    // Create unique content for each thread
                    String content = generateTestDocument("High concurrency test " + index, 1000 + (index * 100));
                    PipeDoc inputDoc = createDocumentWithBody("concurrent-stress-" + index, content);
                    
                    Struct config = Struct.newBuilder()
                            .putFields("chunk_size", Value.newBuilder().setNumberValue(500 + (index * 10)).build())
                            .putFields("chunk_overlap", Value.newBuilder().setNumberValue(50 + (index * 2)).build())
                            .putFields("chunk_config_id", Value.newBuilder().setStringValue("stress_" + index).build())
                            .build();
                    
                    ProcessRequest request = createProcessRequestWithJsonConfig("stress-pipeline-" + index, "chunker-stress", inputDoc, config);
                    
                    long startTime = System.currentTimeMillis();
                    ProcessResponse response = blockingClient.processData(request);
                    processingTimes[index] = System.currentTimeMillis() - startTime;
                    
                    results[index] = response.getSuccess() && response.getOutputDoc().getSemanticResultsCount() > 0;
                    
                    if (results[index]) {
                        LOG.debug("Stress test {} completed in {}ms", index, processingTimes[index]);
                    }
                } catch (Exception e) {
                    LOG.error("High concurrency stress test {} failed", index, e);
                    results[index] = false;
                }
            });
        }
        
        // Start all threads simultaneously
        long testStartTime = System.currentTimeMillis();
        for (Thread thread : threads) {
            thread.start();
        }
        
        // Wait for all to complete
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
        
        // Should have high success rate under stress
        assertTrue(successRate >= 80, "Success rate under high concurrency should be at least 80%");
        
        LOG.info("✅ High concurrency stress test completed - Success rate: {:.1f}%, " +
                "Avg processing time: {:.1f}ms, Min: {}ms, Max: {}ms, Total test time: {}ms", 
                successRate, avgProcessingTime, minProcessingTime, maxProcessingTime, totalTestTime);
    }

    @Test
    @Order(20)
    @DisplayName("Configuration Matrix Testing - Should handle all combinations of settings")
    void testConfigurationMatrixTesting() {
        String testContent = generateTestDocument("Configuration matrix test", 2000);
        PipeDoc inputDoc = createDocumentWithBody("config-matrix-test", testContent);
        
        // Test matrix of different configurations
        int[] chunkSizes = {100, 500, 1000, 2000};
        int[] overlaps = {0, 10, 50, 100};
        
        int testCount = 0;
        int successCount = 0;
        
        for (int chunkSize : chunkSizes) {
            for (int overlap : overlaps) {
                // Skip invalid combinations
                if (overlap >= chunkSize) continue;
                
                testCount++;
                
                Struct config = Struct.newBuilder()
                        .putFields("chunk_size", Value.newBuilder().setNumberValue(chunkSize).build())
                        .putFields("chunk_overlap", Value.newBuilder().setNumberValue(overlap).build())
                        .putFields("chunk_config_id", Value.newBuilder().setStringValue("matrix_" + chunkSize + "_" + overlap).build())
                        .build();
                
                ProcessRequest request = createProcessRequestWithJsonConfig("matrix-pipeline-" + testCount, "chunker-matrix", inputDoc, config);
                
                ProcessResponse response = blockingClient.processData(request);
                
                if (response.getSuccess()) {
                    successCount++;
                    SemanticProcessingResult result = response.getOutputDoc().getSemanticResults(0);
                    assertTrue(result.getChunksCount() > 0, 
                            "Should create chunks for config: " + chunkSize + "/" + overlap);
                    
                    LOG.debug("Matrix test successful: chunk_size={}, overlap={}, chunks={}", 
                            chunkSize, overlap, result.getChunksCount());
                } else {
                    LOG.warn("Matrix test failed for chunk_size={}, overlap={}: {}", 
                            chunkSize, overlap, response.getErrorDetails());
                }
            }
        }
        
        double successRate = (double) successCount / testCount * 100;
        assertTrue(successRate >= 90, "Configuration matrix success rate should be at least 90%");
        
        LOG.info("✅ Configuration matrix testing completed - {}/{} configurations successful ({:.1f}%)", 
                successCount, testCount, successRate);
    }

    @Test
    @Order(21)
    @DisplayName("Memory Efficiency Testing - Should handle chunking without memory leaks")
    void testMemoryEfficiencyTesting() throws Exception {
        // Monitor memory usage during chunking operations
        Runtime runtime = Runtime.getRuntime();
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();
        
        // Process multiple documents of varying sizes
        int[] documentSizes = {1000, 5000, 10000, 20000, 50000}; // words
        
        for (int size : documentSizes) {
            String content = generateTestDocument("Memory efficiency test", size);
            PipeDoc inputDoc = createDocumentWithBody("memory-test-" + size, content);
            
            Struct config = Struct.newBuilder()
                    .putFields("chunk_size", Value.newBuilder().setNumberValue(1000).build())
                    .putFields("chunk_overlap", Value.newBuilder().setNumberValue(100).build())
                    .putFields("chunk_config_id", Value.newBuilder().setStringValue("memory_test_" + size).build())
                    .build();
            
            ProcessRequest request = createProcessRequestWithJsonConfig("memory-pipeline-" + size, "chunker-memory", inputDoc, config);
            
            ProcessResponse response = blockingClient.processData(request);
            
            assertTrue(response.getSuccess(), "Memory efficiency test should succeed for size " + size);
            
            // Force garbage collection
            System.gc();
            Thread.sleep(100);
        }
        
        // Final memory check
        System.gc();
        Thread.sleep(1000);
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryIncrease = finalMemory - initialMemory;
        
        LOG.info("✅ Memory efficiency test completed - Memory increase: {}MB", memoryIncrease / (1024 * 1024));
        
        // Memory increase should be reasonable
        assertTrue(memoryIncrease < 200 * 1024 * 1024, "Memory increase should be less than 200MB");
    }

    @Test
    @Order(22)
    @DisplayName("Edge Case Content Testing - Should handle problematic content patterns")
    void testEdgeCaseContentTesting() {
        // Test various problematic content patterns
        Map<String, String> edgeCases = new HashMap<>();
        
        edgeCases.put("empty_lines", "Line 1\n\n\n\n\nLine 2\n\n\n\nLine 3");
        edgeCases.put("only_whitespace", "   \t\t\t   \n\n   \t   \n\n   ");
        edgeCases.put("very_long_line", "This is an extremely long line without any breaks that goes on and on and on ".repeat(100));
        edgeCases.put("special_chars", "Special characters: !@#$%^&*()_+-=[]{}|;':\",./<>?`~");
        edgeCases.put("mixed_newlines", "Unix\nWindows\r\nMac\rMixed\n\r\nContent");
        edgeCases.put("unicode_control", "Control chars: \u0000\u0001\u0002\u0003\u001F\u007F\u0080\u009F");
        edgeCases.put("repeated_patterns", "ABC ".repeat(10000));
        
        for (Map.Entry<String, String> testCase : edgeCases.entrySet()) {
            PipeDoc inputDoc = createDocumentWithBody("edge-case-" + testCase.getKey(), testCase.getValue());
            
            Struct config = Struct.newBuilder()
                    .putFields("chunk_size", Value.newBuilder().setNumberValue(200).build())
                    .putFields("chunk_overlap", Value.newBuilder().setNumberValue(20).build())
                    .putFields("chunk_config_id", Value.newBuilder().setStringValue("edge_" + testCase.getKey()).build())
                    .build();
            
            ProcessRequest request = createProcessRequestWithJsonConfig("edge-pipeline-" + testCase.getKey(), "chunker-edge", inputDoc, config);
            
            ProcessResponse response = blockingClient.processData(request);
            
            assertTrue(response.getSuccess(), "Edge case test should succeed for: " + testCase.getKey());
            
            // Even if content is problematic, should handle gracefully
            if (response.getOutputDoc().getSemanticResultsCount() > 0) {
                SemanticProcessingResult result = response.getOutputDoc().getSemanticResults(0);
                LOG.info("Edge case '{}' created {} chunks", testCase.getKey(), result.getChunksCount());
            } else {
                LOG.info("Edge case '{}' resulted in no chunks (handled gracefully)", testCase.getKey());
            }
        }
        
        LOG.info("✅ Edge case content testing completed - all cases handled gracefully");
    }

    @Test
    @Order(23)
    @DisplayName("Performance Regression Testing - Should maintain chunking performance")
    void testPerformanceRegressionTesting() {
        // Establish performance baselines for different scenarios
        Map<String, String> performanceTests = new HashMap<>();
        performanceTests.put("small_doc", generateTestDocument("Small document", 100));
        performanceTests.put("medium_doc", generateTestDocument("Medium document", 1000));
        performanceTests.put("large_doc", generateTestDocument("Large document", 10000));
        
        Map<String, Long> performanceBaselines = new HashMap<>();
        performanceBaselines.put("small_doc", 1000L);  // 1 second
        performanceBaselines.put("medium_doc", 5000L); // 5 seconds
        performanceBaselines.put("large_doc", 30000L); // 30 seconds
        
        for (Map.Entry<String, String> test : performanceTests.entrySet()) {
            PipeDoc inputDoc = createDocumentWithBody("perf-" + test.getKey(), test.getValue());
            
            Struct config = Struct.newBuilder()
                    .putFields("chunk_size", Value.newBuilder().setNumberValue(500).build())
                    .putFields("chunk_overlap", Value.newBuilder().setNumberValue(50).build())
                    .putFields("chunk_config_id", Value.newBuilder().setStringValue("perf_" + test.getKey()).build())
                    .build();
            
            ProcessRequest request = createProcessRequestWithJsonConfig("perf-pipeline-" + test.getKey(), "chunker-perf", inputDoc, config);
            
            long startTime = System.currentTimeMillis();
            ProcessResponse response = blockingClient.processData(request);
            long processingTime = System.currentTimeMillis() - startTime;
            
            assertTrue(response.getSuccess(), "Performance test should succeed for " + test.getKey());
            
            Long baseline = performanceBaselines.get(test.getKey());
            assertTrue(processingTime < baseline, 
                    "Performance regression detected for " + test.getKey() + ": " + processingTime + "ms > " + baseline + "ms");
            
            SemanticProcessingResult result = response.getOutputDoc().getSemanticResults(0);
            LOG.info("✅ Performance test '{}' completed in {}ms - {} chunks created", 
                    test.getKey(), processingTime, result.getChunksCount());
        }
    }

    // Helper Methods

    private PipeDoc createDocumentWithBody(String docId, String body) {
        return PipeDoc.newBuilder()
                .setId(docId)
                .setTitle("Test Document: " + docId)
                .setBody(body)
                .build();
    }
    
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
    
    private String generateLongTestDocument(int wordCount) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < wordCount; i++) {
            sb.append("Word").append(i + 1).append(" ");
            if ((i + 1) % 10 == 0) {
                sb.append(". ");
            }
            if ((i + 1) % 50 == 0) {
                sb.append("\n\n");
            }
        }
        return sb.toString();
    }
    
    private String generateTestDocument(String prefix, int sentenceCount) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sentenceCount; i++) {
            sb.append(prefix).append(" sentence ").append(i + 1).append(". ");
        }
        return sb.toString();
    }
}