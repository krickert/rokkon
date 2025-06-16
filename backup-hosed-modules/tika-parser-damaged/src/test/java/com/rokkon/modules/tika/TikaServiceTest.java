package com.rokkon.modules.tika;

import com.rokkon.search.model.PipeDoc;
import com.rokkon.search.sdk.*;
import com.rokkon.test.data.TestDocumentFactory;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tier 1: Primary tests for Tika service using @QuarkusTest.
 * These tests run the entire Quarkus application including the gRPC server
 * within the same JVM as the test itself.
 * 
 * Tests focus on Tika-specific functionality like text extraction from blobs,
 * document format handling, and Tika configuration options.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TikaServiceTest {

    private static final Logger LOG = LoggerFactory.getLogger(TikaServiceTest.class);

    @GrpcClient("tika")
    MutinyPipeStepProcessorGrpc.MutinyPipeStepProcessorStub tikaClient;

    @Inject
    TestDocumentFactory documentFactory;

    @Test
    @Order(1)
    @DisplayName("Basic Connectivity - Should connect to Tika service")
    void testBasicConnectivity() {
        LOG.info("Testing basic connectivity to Tika service");

        PipeDoc simpleDoc = documentFactory.createSimpleTextDocument("connectivity-test", "Connectivity Test", "Simple test content");
        ProcessRequest request = createBasicProcessRequest("connectivity-test", simpleDoc);
        
        ProcessResponse response = tikaClient.processData(request)
            .await().atMost(Duration.ofSeconds(5));
        
        assertNotNull(response, "Should receive a response from Tika service");
        LOG.info("✅ Basic connectivity test passed");
    }

    @Test
    @Order(2)
    @DisplayName("Tika Text Extraction - Should extract text from documents with blobs")
    void testTikaTextExtraction() {
        LOG.info("Testing Tika-specific text extraction capabilities");

        String sampleText = "This is a sample document for Tika text extraction. " +
                           "It contains multiple sentences and should be processed by Tika to extract text content.";
        PipeDoc docWithBlob = documentFactory.createDocumentWithBlob(
            "tika-extraction-test", 
            sampleText.getBytes(), 
            "text/plain", 
            "test.txt"
        );
        
        ProcessRequest request = createBasicProcessRequest("tika-text-extraction", docWithBlob);
        ProcessResponse response = tikaClient.processData(request)
            .await().atMost(Duration.ofSeconds(30));
        
        assertTrue(response.getSuccess(), "Tika text extraction should succeed");
        assertNotNull(response.getOutputDoc().getBody(), "Extracted text should be present");
        assertTrue(response.getOutputDoc().getBody().contains("sample document"), 
                  "Extracted text should contain original content");
        assertTrue(response.getOutputDoc().hasBlob(), "Original blob should be preserved");

        LOG.info("✅ Tika text extraction test completed");
    }

    @Test
    @Order(3)
    @DisplayName("Multiple Document Formats - Should handle various file types")
    void testMultipleDocumentFormats() {
        LOG.info("Testing multiple document formats with blob data");

        List<PipeDoc> rawDocuments = documentFactory.createRawDocumentsWithBlobData();
        assertTrue(rawDocuments.size() > 0, "Should have raw documents with blob data");

        int successCount = 0;
        int totalTextLength = 0;
        int maxTestDocs = Math.min(10, rawDocuments.size());

        for (int i = 0; i < maxTestDocs; i++) {
            PipeDoc rawDoc = rawDocuments.get(i);
            
            ProcessConfiguration config = ProcessConfiguration.newBuilder()
                    .putConfigParams("extractMetadata", "true")
                    .putConfigParams("maxContentLength", "1000000")
                    .build();

            ProcessRequest request = createProcessRequest(
                "multi-format-test", "tika-format-" + i, rawDoc, config);

            try {
                ProcessResponse response = tikaClient.processData(request)
                    .await().atMost(Duration.ofSeconds(30));

                LOG.debug("Processing document: {} ({})", rawDoc.getId(), 
                    rawDoc.hasBlob() ? rawDoc.getBlob().getFilename() : "no-blob");

                if (response.getSuccess()) {
                    successCount++;
                    PipeDoc processedDoc = response.getOutputDoc();
                    
                    if (processedDoc.hasBody() && !processedDoc.getBody().trim().isEmpty()) {
                        totalTextLength += processedDoc.getBody().length();
                        LOG.debug("✅ Extracted {} characters from document {}", 
                            processedDoc.getBody().length(), rawDoc.getId());
                    }
                } else {
                    LOG.warn("⚠️ Failed to process document: {}", rawDoc.getId());
                }
            } catch (Exception e) {
                LOG.warn("Document processing failed for {}: {}", rawDoc.getId(), e.getMessage());
            }
        }

        double successRate = (double) successCount / maxTestDocs;
        LOG.info("Text extraction success rate: {}/{} ({:.1f}%)", 
            successCount, maxTestDocs, successRate * 100);

        assertTrue(successRate >= 0.5, 
            "Should successfully extract text from at least 50% of documents");

        LOG.info("✅ Multiple document formats test completed");
    }

    @Test
    @Order(4)
    @DisplayName("Tika Configuration Options - Should handle Tika-specific configurations")
    void testTikaConfigurationOptions() {
        LOG.info("Testing Tika-specific configuration options");

        String testContent = "This is a comprehensive test document for Tika configuration testing. " +
                           "It contains multiple paragraphs and should be processed with different settings. " +
                           "The configuration should affect how Tika extracts and processes this content.";
        
        PipeDoc testDoc = documentFactory.createDocumentWithBlob(
            "tika-config-test", 
            testContent.getBytes(), 
            "text/plain", 
            "config-test.txt"
        );

        // Test 1: Metadata extraction enabled
        testTikaConfigVariation(testDoc, "metadata-enabled", 
            ProcessConfiguration.newBuilder()
                .putConfigParams("extractMetadata", "true")
                .putConfigParams("maxContentLength", "500000")
                .build());

        // Test 2: Limited content length
        testTikaConfigVariation(testDoc, "limited-content",
            ProcessConfiguration.newBuilder()
                .putConfigParams("extractMetadata", "false")
                .putConfigParams("maxContentLength", "100")
                .build());

        LOG.info("✅ Tika configuration options testing completed");
    }

    @Test
    @Order(5)
    @DisplayName("Large Document Processing - Should handle large documents efficiently")
    void testLargeDocumentProcessing() {
        LOG.info("Testing Tika with large document content");

        StringBuilder largeContent = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            largeContent.append("Large document test paragraph ").append(i).append(". ");
            largeContent.append("This paragraph contains meaningful content that Tika should extract. ");
        }
        
        PipeDoc largeDoc = documentFactory.createDocumentWithBlob(
            "large-doc-test", 
            largeContent.toString().getBytes(), 
            "text/plain", 
            "large-test.txt"
        );

        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .putConfigParams("maxContentLength", "2000000")
                .build();

        ProcessRequest request = createProcessRequest("large-doc-test", "tika-large", largeDoc, config);

        long startTime = System.currentTimeMillis();
        ProcessResponse response = tikaClient.processData(request)
            .await().atMost(Duration.ofSeconds(60));
        long processingTime = System.currentTimeMillis() - startTime;

        assertTrue(response.getSuccess(), "Large document processing should succeed");
        assertNotNull(response.getOutputDoc().getBody(), "Large document should have extracted content");
        assertTrue(response.getOutputDoc().getBody().length() > 10000, "Should extract substantial content");

        LOG.info("✅ Large document processed successfully in {}ms", processingTime);
        assertTrue(processingTime < 30000, "Large document should process within 30 seconds");
    }

    @Test
    @Order(6)
    @DisplayName("Error Handling - Should handle invalid inputs gracefully")
    void testErrorHandling() {
        LOG.info("Testing error handling with invalid inputs");

        PipeDoc invalidDoc = PipeDoc.newBuilder()
            .setId("invalid-doc")
            .build();

        ProcessRequest request = createBasicProcessRequest("error-test", invalidDoc);
        
        ProcessResponse response = tikaClient.processData(request)
            .await().atMost(Duration.ofSeconds(5));

        assertFalse(response.getSuccess(), "Should fail gracefully with invalid input");
        assertTrue(response.hasErrorDetails(), "Should have error details");
        
        LOG.info("✅ Error handling test completed");
    }

    @Test
    @Order(7)
    @DisplayName("Performance - Should handle reasonable processing loads")
    void testBasicPerformance() {
        LOG.info("Testing basic performance with multiple documents");

        int numDocs = 5;
        int successCount = 0;
        long totalTime = 0;

        for (int i = 0; i < numDocs; i++) {
            PipeDoc doc = documentFactory.createSimpleTextDocument(
                "perf-test-" + i, 
                "Performance Test " + i,
                "Performance test document " + i + " with some content to process."
            );

            ProcessRequest request = createBasicProcessRequest("perf-test-" + i, doc);
            
            long startTime = System.currentTimeMillis();
            try {
                ProcessResponse response = tikaClient.processData(request)
                    .await().atMost(Duration.ofSeconds(10));
                
                long endTime = System.currentTimeMillis();
                totalTime += (endTime - startTime);

                if (response.getSuccess()) {
                    successCount++;
                    LOG.debug("Performance test document {} processed in {}ms", i, endTime - startTime);
                } else {
                    LOG.warn("Performance test document {} failed", i);
                }
            } catch (Exception e) {
                LOG.warn("Performance test document {} failed: {}", i, e.getMessage());
            }
        }

        double avgTime = (double) totalTime / numDocs;
        LOG.info("Performance test completed: {}/{} successful, avg time: {:.0f}ms", 
            successCount, numDocs, avgTime);

        assertTrue(successCount >= numDocs * 0.8, "At least 80% of documents should process successfully");
        assertTrue(avgTime < 5000, "Average processing time should be under 5 seconds");
    }

    private void testTikaConfigVariation(PipeDoc testDoc, String configName, ProcessConfiguration config) {
        LOG.debug("Testing Tika configuration: {}", configName);

        ProcessRequest request = createProcessRequest("tika-config-test", "tika-" + configName, testDoc, config);
        ProcessResponse response = tikaClient.processData(request)
            .await().atMost(Duration.ofSeconds(30));

        assertTrue(response.getSuccess(), 
            "Tika configuration " + configName + " should process successfully");

        LOG.debug("✅ Tika configuration {} completed", configName);
    }

    private ProcessRequest createBasicProcessRequest(String streamId, PipeDoc doc) {
        return ProcessRequest.newBuilder()
            .setDocument(doc)
            .build();
    }

    private ProcessRequest createProcessRequest(String streamId, String docId, PipeDoc doc, ProcessConfiguration config) {
        PipeDoc docWithId = doc.toBuilder().setId(docId).build();
        return ProcessRequest.newBuilder()
            .setDocument(docWithId)
            .setConfig(config)
            .build();
    }
}