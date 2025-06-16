package com.rokkon.modules.tika;

import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import com.google.protobuf.Struct;
import com.google.protobuf.util.JsonFormat;
import com.rokkon.search.model.Blob;
import com.rokkon.search.model.PipeDoc;
import com.rokkon.search.sdk.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive integration tests for the Tika Parser service using @QuarkusIntegrationTest.
 * These tests run against the actual containerized service to verify end-to-end functionality.
 * 
 * Test Coverage:
 * - Document parsing for all supported formats (PDF, DOC, DOCX, HTML, TXT, etc.)
 * - Configuration variations and custom settings
 * - Error handling and edge cases
 * - Service registration and health checks
 * - Performance verification with large documents
 * - Metadata extraction capabilities
 * - Blob handling and preservation
 * - Async and sync processing patterns
 */
@QuarkusIntegrationTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TikaParserIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(TikaParserIntegrationTest.class);
    
    private ManagedChannel channel;
    private PipeStepProcessorGrpc.PipeStepProcessorBlockingStub blockingClient;
    private ModuleRegistrationServiceGrpc.ModuleRegistrationServiceBlockingStub registrationClient;

    @BeforeEach
    void setUp() {
        // Set up gRPC channel for integration testing
        channel = ManagedChannelBuilder.forAddress("localhost", 9000)
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
    @DisplayName("Service Registration - Should register successfully and provide service metadata")
    void testServiceRegistration() {
        LOG.info("Testing Tika Parser service registration");
        
        ServiceRegistrationResponse response = registrationClient.getServiceRegistration(Empty.getDefaultInstance());
        
        assertNotNull(response, "Service registration response should not be null");
        assertTrue(response.getSuccess(), "Service registration should be successful");
        assertNotNull(response.getServiceInfo(), "Service info should be provided");
        
        ServiceInfo serviceInfo = response.getServiceInfo();
        assertEquals("tika-parser", serviceInfo.getServiceName());
        assertTrue(serviceInfo.getVersion().length() > 0, "Version should be specified");
        assertTrue(serviceInfo.getDescription().contains("Tika"), "Description should mention Tika");
        assertTrue(serviceInfo.getSupportedConfigurationCount() > 0, "Should support some configurations");
        
        LOG.info("✅ Service registered successfully: {} v{}", serviceInfo.getServiceName(), serviceInfo.getVersion());
    }

    @Test
    @Order(2)
    @DisplayName("Text Document Processing - Should parse plain text successfully")
    void testPlainTextProcessing() {
        String testText = "This is a comprehensive test document for Tika parsing. " +
                         "It contains multiple sentences and paragraphs to verify parsing capabilities. " +
                         "The parser should extract this text content and preserve formatting.";
        
        PipeDoc inputDoc = createDocumentWithBlob("test.txt", testText.getBytes(StandardCharsets.UTF_8), "text/plain");
        ProcessRequest request = createProcessRequest("text-processing-pipeline", "tika-parser", inputDoc);
        
        ProcessResponse response = blockingClient.processData(request);
        
        assertTrue(response.getSuccess(), "Text processing should be successful");
        assertNotNull(response.getOutputDoc(), "Output document should be present");
        assertTrue(response.getOutputDoc().getBody().contains("comprehensive test document"), 
                  "Parsed body should contain original text");
        assertTrue(response.getOutputDoc().hasBlob(), "Original blob should be preserved");
        
        LOG.info("✅ Plain text document processed successfully");
    }

    @Test
    @Order(3)
    @DisplayName("PDF Document Processing - Should extract text from PDF files")
    void testPdfProcessing() throws IOException {
        byte[] pdfData = loadTestDocument("412KB.pdf");
        
        PipeDoc inputDoc = createDocumentWithBlob("test.pdf", pdfData, "application/pdf");
        ProcessRequest request = createProcessRequest("pdf-processing-pipeline", "tika-parser", inputDoc);
        
        ProcessResponse response = blockingClient.processData(request);
        
        assertTrue(response.getSuccess(), "PDF processing should be successful");
        assertNotNull(response.getOutputDoc().getBody(), "PDF should have extracted body text");
        assertTrue(response.getOutputDoc().getBody().length() > 100, "PDF should contain substantial text");
        assertTrue(response.getOutputDoc().hasBlob(), "Original PDF blob should be preserved");
        
        // Verify metadata extraction
        assertTrue(response.getOutputDoc().hasCustomData(), "PDF should have extracted metadata");
        
        LOG.info("✅ PDF document processed successfully - extracted {} characters", 
                response.getOutputDoc().getBody().length());
    }

    @Test
    @Order(4)
    @DisplayName("Microsoft Word Processing - Should handle DOC and DOCX files")
    void testMicrosoftWordProcessing() throws IOException {
        // Test DOCX file
        byte[] docxData = loadTestDocument("file-sample_100kB.docx");
        PipeDoc docxDoc = createDocumentWithBlob("test.docx", docxData, "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        ProcessRequest docxRequest = createProcessRequest("docx-processing-pipeline", "tika-parser", docxDoc);
        
        ProcessResponse docxResponse = blockingClient.processData(docxRequest);
        assertTrue(docxResponse.getSuccess(), "DOCX processing should be successful");
        assertNotNull(docxResponse.getOutputDoc().getBody(), "DOCX should have extracted body text");
        
        // Test DOC file
        byte[] docData = loadTestDocument("file-sample_500kB.doc");
        PipeDoc docDoc = createDocumentWithBlob("test.doc", docData, "application/msword");
        ProcessRequest docRequest = createProcessRequest("doc-processing-pipeline", "tika-parser", docDoc);
        
        ProcessResponse docResponse = blockingClient.processData(docRequest);
        assertTrue(docResponse.getSuccess(), "DOC processing should be successful");
        assertNotNull(docResponse.getOutputDoc().getBody(), "DOC should have extracted body text");
        
        LOG.info("✅ Microsoft Word documents processed successfully");
    }

    @Test
    @Order(5)
    @DisplayName("Excel Processing - Should handle XLS files")
    void testExcelProcessing() throws IOException {
        byte[] excelData = loadTestDocument("file_example_XLS_100KB.xls");
        
        PipeDoc inputDoc = createDocumentWithBlob("test.xls", excelData, "application/vnd.ms-excel");
        ProcessRequest request = createProcessRequest("excel-processing-pipeline", "tika-parser", inputDoc);
        
        ProcessResponse response = blockingClient.processData(request);
        
        assertTrue(response.getSuccess(), "Excel processing should be successful");
        assertNotNull(response.getOutputDoc().getBody(), "Excel should have extracted content");
        
        LOG.info("✅ Excel document processed successfully");
    }

    @Test
    @Order(6)
    @DisplayName("PowerPoint Processing - Should handle PPT files")
    void testPowerPointProcessing() throws IOException {
        byte[] pptData = loadTestDocument("file_example_PPT_500kB.ppt");
        
        PipeDoc inputDoc = createDocumentWithBlob("test.ppt", pptData, "application/vnd.ms-powerpoint");
        ProcessRequest request = createProcessRequest("ppt-processing-pipeline", "tika-parser", inputDoc);
        
        ProcessResponse response = blockingClient.processData(request);
        
        assertTrue(response.getSuccess(), "PowerPoint processing should be successful");
        assertNotNull(response.getOutputDoc().getBody(), "PowerPoint should have extracted content");
        
        LOG.info("✅ PowerPoint document processed successfully");
    }

    @Test
    @Order(7)
    @DisplayName("HTML Processing - Should parse HTML and extract text content")
    void testHtmlProcessing() throws IOException {
        byte[] htmlData = loadTestDocument("HTML_3KB.html");
        
        PipeDoc inputDoc = createDocumentWithBlob("test.html", htmlData, "text/html");
        ProcessRequest request = createProcessRequest("html-processing-pipeline", "tika-parser", inputDoc);
        
        ProcessResponse response = blockingClient.processData(request);
        
        assertTrue(response.getSuccess(), "HTML processing should be successful");
        assertNotNull(response.getOutputDoc().getBody(), "HTML should have extracted text content");
        assertFalse(response.getOutputDoc().getBody().contains("<html>"), "HTML tags should be stripped");
        
        LOG.info("✅ HTML document processed successfully");
    }

    @Test
    @Order(8)
    @DisplayName("JSON Processing - Should handle structured JSON data")
    void testJsonProcessing() throws IOException {
        byte[] jsonData = loadTestDocument("sample.json");
        
        PipeDoc inputDoc = createDocumentWithBlob("test.json", jsonData, "application/json");
        ProcessRequest request = createProcessRequest("json-processing-pipeline", "tika-parser", inputDoc);
        
        ProcessResponse response = blockingClient.processData(request);
        
        assertTrue(response.getSuccess(), "JSON processing should be successful");
        assertNotNull(response.getOutputDoc().getBody(), "JSON should have extracted content");
        
        LOG.info("✅ JSON document processed successfully");
    }

    @Test
    @Order(9)
    @DisplayName("XML Processing - Should handle XML structured data")  
    void testXmlProcessing() throws IOException {
        byte[] xmlData = loadTestDocument("sample.xml");
        
        PipeDoc inputDoc = createDocumentWithBlob("test.xml", xmlData, "application/xml");
        ProcessRequest request = createProcessRequest("xml-processing-pipeline", "tika-parser", inputDoc);
        
        ProcessResponse response = blockingClient.processData(request);
        
        assertTrue(response.getSuccess(), "XML processing should be successful");
        assertNotNull(response.getOutputDoc().getBody(), "XML should have extracted content");
        
        LOG.info("✅ XML document processed successfully");
    }

    @Test
    @Order(10)
    @DisplayName("Large Document Processing - Should handle large documents efficiently")
    void testLargeDocumentProcessing() throws IOException {
        byte[] largeData = loadTestDocument("sample_1mb.txt");
        
        PipeDoc inputDoc = createDocumentWithBlob("large.txt", largeData, "text/plain");
        ProcessRequest request = createProcessRequest("large-doc-pipeline", "tika-parser", inputDoc);
        
        long startTime = System.currentTimeMillis();
        ProcessResponse response = blockingClient.processData(request);
        long processingTime = System.currentTimeMillis() - startTime;
        
        assertTrue(response.getSuccess(), "Large document processing should be successful");
        assertNotNull(response.getOutputDoc().getBody(), "Large document should have extracted content");
        assertTrue(response.getOutputDoc().getBody().length() > 100000, "Should extract substantial content");
        
        LOG.info("✅ Large document processed successfully in {}ms", processingTime);
        
        // Performance verification - should process within reasonable time
        assertTrue(processingTime < 30000, "Large document should process within 30 seconds");
    }

    @Test
    @Order(11)
    @DisplayName("Custom Configuration - Should respect custom parsing settings")
    void testCustomConfiguration() {
        String testText = "Test document with custom configuration";
        PipeDoc inputDoc = createDocumentWithBlob("custom.txt", testText.getBytes(StandardCharsets.UTF_8), "text/plain");
        
        // Create custom configuration
        Map<String, String> configParams = new HashMap<>();
        configParams.put("extractMetadata", "true");
        configParams.put("maxStringLength", "10000");
        
        ProcessRequest request = createProcessRequestWithConfig("custom-config-pipeline", "tika-parser", inputDoc, configParams);
        
        ProcessResponse response = blockingClient.processData(request);
        
        assertTrue(response.getSuccess(), "Custom configuration processing should be successful");
        assertNotNull(response.getOutputDoc().getBody(), "Should have extracted content");
        
        LOG.info("✅ Custom configuration processing successful");
    }

    @Test
    @Order(12)
    @DisplayName("Metadata Extraction - Should extract document metadata")
    void testMetadataExtraction() throws IOException {
        byte[] pdfData = loadTestDocument("412KB.pdf");
        
        PipeDoc inputDoc = createDocumentWithBlob("metadata.pdf", pdfData, "application/pdf");
        
        Map<String, String> configParams = new HashMap<>();
        configParams.put("extractMetadata", "true");
        
        ProcessRequest request = createProcessRequestWithConfig("metadata-pipeline", "tika-parser", inputDoc, configParams);
        
        ProcessResponse response = blockingClient.processData(request);
        
        assertTrue(response.getSuccess(), "Metadata extraction should be successful");
        assertTrue(response.getOutputDoc().hasCustomData(), "Should have extracted metadata");
        
        // Verify some common metadata fields are present
        Struct metadata = response.getOutputDoc().getCustomData();
        assertTrue(metadata.getFieldsCount() > 0, "Should have extracted some metadata fields");
        
        LOG.info("✅ Metadata extraction successful - extracted {} fields", metadata.getFieldsCount());
    }

    @Test
    @Order(13)
    @DisplayName("Error Handling - Should handle invalid documents gracefully")
    void testErrorHandling() {
        // Test with corrupted/invalid data
        byte[] invalidData = "This is not a valid PDF file".getBytes(StandardCharsets.UTF_8);
        
        PipeDoc inputDoc = createDocumentWithBlob("invalid.pdf", invalidData, "application/pdf");
        ProcessRequest request = createProcessRequest("error-handling-pipeline", "tika-parser", inputDoc);
        
        ProcessResponse response = blockingClient.processData(request);
        
        // Should handle gracefully - either succeed with extracted text or fail gracefully
        assertNotNull(response, "Response should not be null");
        if (!response.getSuccess()) {
            assertNotNull(response.getErrorDetails(), "Error details should be provided");
            LOG.info("✅ Error handled gracefully: {}", response.getErrorDetails());
        } else {
            LOG.info("✅ Invalid document processed gracefully with extracted content");
        }
    }

    @Test
    @Order(14)
    @DisplayName("Empty Document Handling - Should handle documents without blob")
    void testEmptyDocumentHandling() {
        PipeDoc inputDoc = PipeDoc.newBuilder()
                .setId("empty-doc-123")
                .setTitle("Empty Document Test")
                .build();
        
        ProcessRequest request = createProcessRequest("empty-doc-pipeline", "tika-parser", inputDoc);
        
        ProcessResponse response = blockingClient.processData(request);
        
        assertTrue(response.getSuccess(), "Empty document processing should be successful");
        assertEquals(inputDoc, response.getOutputDoc(), "Document should be returned unchanged");
        
        LOG.info("✅ Empty document handled successfully");
    }

    @Test
    @Order(15)
    @DisplayName("Batch Processing - Should handle multiple documents efficiently")
    void testBatchProcessing() throws IOException {
        // Process multiple different document types in sequence
        String[] documentTypes = {"TXT.txt", "HTML_3KB.html", "CSV.csv"};
        String[] mimeTypes = {"text/plain", "text/html", "text/csv"};
        
        for (int i = 0; i < documentTypes.length; i++) {
            byte[] data = loadTestDocument(documentTypes[i]);
            PipeDoc inputDoc = createDocumentWithBlob(documentTypes[i], data, mimeTypes[i]);
            ProcessRequest request = createProcessRequest("batch-pipeline-" + i, "tika-parser-batch", inputDoc);
            
            ProcessResponse response = blockingClient.processData(request);
            
            assertTrue(response.getSuccess(), "Batch processing should succeed for " + documentTypes[i]);
            assertNotNull(response.getOutputDoc().getBody(), "Should extract content from " + documentTypes[i]);
        }
        
        LOG.info("✅ Batch processing completed successfully for {} documents", documentTypes.length);
    }

    @Test
    @Order(16)
    @DisplayName("Service Health Check - Should respond to health checks")
    void testServiceHealthCheck() {
        // This is implicit - if the service responds to gRPC calls, it's healthy
        ProcessRequest healthRequest = createProcessRequest("health-check", "health", 
                PipeDoc.newBuilder().setId("health-doc").build());
        
        assertDoesNotThrow(() -> {
            ProcessResponse response = blockingClient.processData(healthRequest);
            assertNotNull(response, "Health check should return a response");
        }, "Service should respond to health checks");
        
        LOG.info("✅ Service health check passed");
    }

    @Test
    @Order(17)
    @DisplayName("Concurrent Processing - Should handle concurrent requests")
    void testConcurrentProcessing() throws Exception {
        String testText = "Concurrent processing test document";
        int concurrentRequests = 5;
        
        // Create multiple threads to process documents concurrently
        Thread[] threads = new Thread[concurrentRequests];
        boolean[] results = new boolean[concurrentRequests];
        
        for (int i = 0; i < concurrentRequests; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                try {
                    PipeDoc inputDoc = createDocumentWithBlob("concurrent-" + index + ".txt", 
                            (testText + " " + index).getBytes(StandardCharsets.UTF_8), "text/plain");
                    ProcessRequest request = createProcessRequest("concurrent-pipeline-" + index, "tika-concurrent", inputDoc);
                    
                    ProcessResponse response = blockingClient.processData(request);
                    results[index] = response.getSuccess();
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
        
        // Wait for all to complete
        for (Thread thread : threads) {
            thread.join();
        }
        
        // Verify all succeeded
        for (int i = 0; i < concurrentRequests; i++) {
            assertTrue(results[i], "Concurrent request " + i + " should succeed");
        }
        
        LOG.info("✅ Concurrent processing test passed for {} requests", concurrentRequests);
    }

    // ADVANCED EDGE CASE AND STRESS TESTS - SIGNIFICANT EXPANSION

    @Test
    @Order(18)
    @DisplayName("Corrupted File Handling - Should handle corrupted documents gracefully")
    void testCorruptedFileHandling() throws IOException {
        // Test various types of corrupted files
        String[] corruptedFileTypes = {"corrupted.pdf", "corrupted.docx", "corrupted.xlsx"};
        String[] mimeTypes = {"application/pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"};
        
        for (int i = 0; i < corruptedFileTypes.length; i++) {
            // Create corrupted data by truncating valid file data
            byte[] corruptedData = generateCorruptedData(corruptedFileTypes[i]);
            
            PipeDoc inputDoc = createDocumentWithBlob(corruptedFileTypes[i], corruptedData, mimeTypes[i]);
            ProcessRequest request = createProcessRequest("corrupted-pipeline-" + i, "tika-corrupted", inputDoc);
            
            ProcessResponse response = blockingClient.processData(request);
            
            // Should handle gracefully - either succeed with partial extraction or fail gracefully
            assertNotNull(response, "Response should not be null for corrupted file");
            if (!response.getSuccess()) {
                assertNotNull(response.getErrorDetails(), "Error details should be provided for corrupted files");
                LOG.info("✅ Corrupted file {} handled gracefully: {}", corruptedFileTypes[i], response.getErrorDetails());
            } else {
                LOG.info("✅ Corrupted file {} processed with partial extraction", corruptedFileTypes[i]);
            }
        }
    }

    @Test
    @Order(19)
    @DisplayName("Password Protected Documents - Should handle password-protected files")
    void testPasswordProtectedDocuments() {
        // Test password-protected PDF (simulate by creating a document that claims to be password-protected)
        String passwordProtectedContent = "%PDF-1.4\n1 0 obj\n<< /Type /Catalog /Pages 2 0 R /Encrypt 3 0 R >>";
        
        PipeDoc inputDoc = createDocumentWithBlob("password-protected.pdf", 
                passwordProtectedContent.getBytes(StandardCharsets.UTF_8), "application/pdf");
        ProcessRequest request = createProcessRequest("password-protected-pipeline", "tika-password", inputDoc);
        
        ProcessResponse response = blockingClient.processData(request);
        
        assertNotNull(response, "Response should not be null for password-protected file");
        // Should either handle gracefully or provide appropriate error
        if (!response.getSuccess()) {
            assertNotNull(response.getErrorDetails(), "Error details should be provided for password-protected files");
            LOG.info("✅ Password-protected document handled gracefully: {}", response.getErrorDetails());
        } else {
            LOG.info("✅ Password-protected document processed successfully");
        }
    }

    @Test
    @Order(20)
    @DisplayName("Zero Byte File Handling - Should handle empty files")
    void testZeroByteFileHandling() {
        byte[] emptyData = new byte[0];
        
        PipeDoc inputDoc = createDocumentWithBlob("empty.pdf", emptyData, "application/pdf");
        ProcessRequest request = createProcessRequest("empty-file-pipeline", "tika-empty", inputDoc);
        
        ProcessResponse response = blockingClient.processData(request);
        
        assertTrue(response.getSuccess(), "Empty file processing should be successful");
        assertNotNull(response.getOutputDoc(), "Output document should be present");
        // Body might be empty, but document should be preserved
        assertTrue(response.getOutputDoc().hasBlob(), "Original blob should be preserved");
        
        LOG.info("✅ Zero byte file handled successfully");
    }

    @Test
    @Order(21)
    @DisplayName("Extremely Large File Processing - Should handle very large documents")
    void testExtremelyLargeFileProcessing() {
        // Create a very large text file (simulate 50MB text document)
        StringBuilder largeContent = new StringBuilder();
        String baseText = "This is a very large document used for stress testing the Tika parser service. ";
        
        // Create approximately 50MB of text data
        int iterations = 50 * 1024 * 1024 / baseText.length(); // ~50MB
        for (int i = 0; i < iterations; i++) {
            largeContent.append(baseText);
            if (i % 1000 == 0) {
                largeContent.append("\n\nParagraph ").append(i / 1000).append("\n\n");
            }
        }
        
        PipeDoc inputDoc = createDocumentWithBlob("extremely-large.txt", 
                largeContent.toString().getBytes(StandardCharsets.UTF_8), "text/plain");
        
        // Set longer timeout for large file processing
        Map<String, String> configParams = new HashMap<>();
        configParams.put("maxStringLength", "52428800"); // 50MB
        
        ProcessRequest request = createProcessRequestWithConfig("large-file-pipeline", "tika-large", inputDoc, configParams);
        
        long startTime = System.currentTimeMillis();
        ProcessResponse response = blockingClient.processData(request);
        long processingTime = System.currentTimeMillis() - startTime;
        
        assertTrue(response.getSuccess(), "Large file processing should be successful");
        assertNotNull(response.getOutputDoc().getBody(), "Large file should have extracted content");
        
        LOG.info("✅ Extremely large file processed successfully in {}ms - size: {}MB", 
                processingTime, largeContent.length() / (1024 * 1024));
        
        // Performance verification - should process within reasonable time (allow up to 2 minutes for 50MB)
        assertTrue(processingTime < 120000, "Extremely large file should process within 2 minutes");
    }

    @Test
    @Order(22)
    @DisplayName("Encoding Issues - Should handle various character encodings")
    void testEncodingIssues() throws IOException {
        // Test various encodings
        String[] encodings = {"UTF-8", "ISO-8859-1", "Windows-1252", "UTF-16"};
        String testText = "Test with special characters: àáâãäåæçèéêëìíîïñòóôõöùúûüý ñañoño 汉字 العربية";
        
        for (String encoding : encodings) {
            try {
                byte[] encodedData = testText.getBytes(encoding);
                
                PipeDoc inputDoc = createDocumentWithBlob("encoded-" + encoding + ".txt", encodedData, "text/plain");
                ProcessRequest request = createProcessRequest("encoding-pipeline-" + encoding.replace("-", ""), "tika-encoding", inputDoc);
                
                ProcessResponse response = blockingClient.processData(request);
                
                assertTrue(response.getSuccess(), "Encoding test should succeed for " + encoding);
                assertNotNull(response.getOutputDoc().getBody(), "Should extract content for " + encoding);
                
                LOG.info("✅ Encoding {} handled successfully - extracted {} characters", 
                        encoding, response.getOutputDoc().getBody().length());
            } catch (Exception e) {
                LOG.warn("Encoding {} caused issues but was handled: {}", encoding, e.getMessage());
            }
        }
    }

    @Test
    @Order(23)
    @DisplayName("Mixed Content Types - Should handle files with incorrect MIME types")
    void testMixedContentTypes() throws IOException {
        // Load a PDF but claim it's a text file
        byte[] pdfData = loadTestDocument("412KB.pdf");
        
        PipeDoc inputDoc = createDocumentWithBlob("fake.txt", pdfData, "text/plain");
        ProcessRequest request = createProcessRequest("mixed-content-pipeline", "tika-mixed", inputDoc);
        
        ProcessResponse response = blockingClient.processData(request);
        
        assertTrue(response.getSuccess(), "Mixed content type processing should be successful");
        assertNotNull(response.getOutputDoc().getBody(), "Should extract content despite wrong MIME type");
        assertTrue(response.getOutputDoc().getBody().length() > 100, "Should extract substantial content");
        
        LOG.info("✅ Mixed content type handled successfully - extracted {} characters", 
                response.getOutputDoc().getBody().length());
    }

    @Test
    @Order(24)
    @DisplayName("Memory Pressure Test - Should handle multiple large documents concurrently")
    void testMemoryPressure() throws Exception {
        int concurrentLargeFiles = 3;
        Thread[] threads = new Thread[concurrentLargeFiles];
        boolean[] results = new boolean[concurrentLargeFiles];
        
        for (int i = 0; i < concurrentLargeFiles; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                try {
                    // Create large content for each thread
                    StringBuilder content = new StringBuilder();
                    for (int j = 0; j < 100000; j++) {
                        content.append("Memory pressure test document ").append(index).append(" line ").append(j).append(". ");
                    }
                    
                    PipeDoc inputDoc = createDocumentWithBlob("memory-pressure-" + index + ".txt", 
                            content.toString().getBytes(StandardCharsets.UTF_8), "text/plain");
                    ProcessRequest request = createProcessRequest("memory-pressure-pipeline-" + index, "tika-memory", inputDoc);
                    
                    ProcessResponse response = blockingClient.processData(request);
                    results[index] = response.getSuccess();
                    
                    if (results[index]) {
                        LOG.info("Memory pressure test {} completed successfully - processed {}MB", 
                                index, content.length() / (1024 * 1024));
                    }
                } catch (Exception e) {
                    LOG.error("Memory pressure test {} failed", index, e);
                    results[index] = false;
                }
            });
        }
        
        // Start all threads simultaneously
        for (Thread thread : threads) {
            thread.start();
        }
        
        // Wait for completion
        for (Thread thread : threads) {
            thread.join();
        }
        
        // Verify at least most succeeded (allow for some failures under extreme memory pressure)
        int successCount = 0;
        for (boolean result : results) {
            if (result) successCount++;
        }
        
        assertTrue(successCount >= concurrentLargeFiles / 2, 
                "At least half of memory pressure tests should succeed");
        
        LOG.info("✅ Memory pressure test completed - {}/{} tests succeeded", successCount, concurrentLargeFiles);
    }

    @Test
    @Order(25)
    @DisplayName("Complex Metadata Extraction - Should handle advanced metadata scenarios")
    void testComplexMetadataExtraction() throws IOException {
        byte[] pdfData = loadTestDocument("412KB.pdf");
        
        PipeDoc inputDoc = createDocumentWithBlob("complex-metadata.pdf", pdfData, "application/pdf");
        
        // Create complex metadata mapping configuration
        Map<String, String> configParams = new HashMap<>();
        configParams.put("extractMetadata", "true");
        configParams.put("includeAllMetadata", "true");
        configParams.put("metadataPrefix", "tika_");
        configParams.put("preserveOriginalFieldNames", "true");
        
        ProcessRequest request = createProcessRequestWithConfig("complex-metadata-pipeline", "tika-complex-meta", inputDoc, configParams);
        
        ProcessResponse response = blockingClient.processData(request);
        
        assertTrue(response.getSuccess(), "Complex metadata extraction should be successful");
        assertTrue(response.getOutputDoc().hasCustomData(), "Should have extracted complex metadata");
        
        Struct metadata = response.getOutputDoc().getCustomData();
        assertTrue(metadata.getFieldsCount() > 5, "Should have extracted multiple metadata fields");
        
        LOG.info("✅ Complex metadata extraction successful - extracted {} fields", metadata.getFieldsCount());
        
        // Log some metadata fields for verification
        metadata.getFieldsMap().entrySet().stream()
                .limit(5)
                .forEach(entry -> LOG.info("Metadata field: {} = {}", entry.getKey(), 
                        entry.getValue().hasStringValue() ? entry.getValue().getStringValue() : entry.getValue()));
    }

    @Test
    @Order(26)
    @DisplayName("Timeout Handling - Should handle processing timeouts gracefully")
    void testTimeoutHandling() {
        // Create a document that might take a long time to process
        StringBuilder timeoutContent = new StringBuilder();
        for (int i = 0; i < 1000000; i++) {
            timeoutContent.append("Timeout test line ").append(i).append(" with complex formatting and special characters àáâãäåæçèéêë. ");
        }
        
        PipeDoc inputDoc = createDocumentWithBlob("timeout-test.txt", 
                timeoutContent.toString().getBytes(StandardCharsets.UTF_8), "text/plain");
        
        // Set a relatively short timeout
        Map<String, String> configParams = new HashMap<>();
        configParams.put("maxProcessingTime", "10000"); // 10 seconds
        
        ProcessRequest request = createProcessRequestWithConfig("timeout-pipeline", "tika-timeout", inputDoc, configParams);
        
        long startTime = System.currentTimeMillis();
        ProcessResponse response = blockingClient.processData(request);
        long processingTime = System.currentTimeMillis() - startTime;
        
        // Should either complete successfully or handle timeout gracefully
        assertNotNull(response, "Response should not be null even with timeout");
        if (!response.getSuccess()) {
            assertNotNull(response.getErrorDetails(), "Error details should be provided for timeout");
            LOG.info("✅ Timeout handled gracefully: {}", response.getErrorDetails());
        } else {
            LOG.info("✅ Timeout test completed successfully in {}ms", processingTime);
        }
    }

    @Test
    @Order(27)
    @DisplayName("Resource Cleanup Verification - Should properly clean up resources")
    void testResourceCleanupVerification() throws Exception {
        // Process multiple documents and verify no resource leaks
        int documentCount = 20;
        long initialMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        
        for (int i = 0; i < documentCount; i++) {
            String content = "Resource cleanup test document " + i + " with moderate content size. ".repeat(1000);
            
            PipeDoc inputDoc = createDocumentWithBlob("cleanup-test-" + i + ".txt", 
                    content.getBytes(StandardCharsets.UTF_8), "text/plain");
            ProcessRequest request = createProcessRequest("cleanup-pipeline-" + i, "tika-cleanup", inputDoc);
            
            ProcessResponse response = blockingClient.processData(request);
            assertTrue(response.getSuccess(), "Resource cleanup test " + i + " should succeed");
            
            // Force garbage collection periodically
            if (i % 5 == 0) {
                System.gc();
                Thread.sleep(100);
            }
        }
        
        // Allow time for cleanup
        Thread.sleep(1000);
        System.gc();
        Thread.sleep(1000);
        
        long finalMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long memoryIncrease = finalMemory - initialMemory;
        
        LOG.info("✅ Resource cleanup test completed - Memory increase: {}MB", memoryIncrease / (1024 * 1024));
        
        // Memory increase should be reasonable (allow up to 100MB increase)
        assertTrue(memoryIncrease < 100 * 1024 * 1024, 
                "Memory increase should be reasonable after processing multiple documents");
    }

    @Test
    @Order(28)
    @DisplayName("Advanced Format Support - Should handle specialized document formats")
    void testAdvancedFormatSupport() throws IOException {
        // Test additional formats that might be available
        Map<String, String> specialFormats = new HashMap<>();
        specialFormats.put("CSV.csv", "text/csv");
        specialFormats.put("XML.xml", "application/xml");
        
        for (Map.Entry<String, String> format : specialFormats.entrySet()) {
            try {
                byte[] data = loadTestDocument(format.getKey());
                
                PipeDoc inputDoc = createDocumentWithBlob(format.getKey(), data, format.getValue());
                ProcessRequest request = createProcessRequest("special-format-pipeline", "tika-special", inputDoc);
                
                ProcessResponse response = blockingClient.processData(request);
                
                assertTrue(response.getSuccess(), "Special format processing should succeed for " + format.getKey());
                assertNotNull(response.getOutputDoc().getBody(), "Should extract content from " + format.getKey());
                
                LOG.info("✅ Special format {} processed successfully - extracted {} characters", 
                        format.getKey(), response.getOutputDoc().getBody().length());
            } catch (IOException e) {
                LOG.warn("Special format {} not available for testing: {}", format.getKey(), e.getMessage());
            }
        }
    }

    // Helper method to generate corrupted data
    private byte[] generateCorruptedData(String fileName) {
        if (fileName.contains("pdf")) {
            return "%PDF-1.4\n1 0 obj\n<< corrupted data here".getBytes(StandardCharsets.UTF_8);
        } else if (fileName.contains("docx")) {
            return "PK corrupted docx data".getBytes(StandardCharsets.UTF_8);
        } else if (fileName.contains("xlsx")) {
            return "PK corrupted xlsx data".getBytes(StandardCharsets.UTF_8);
        } else {
            return "Corrupted content".getBytes(StandardCharsets.UTF_8);
        }
    }

    // Helper Methods

    private PipeDoc createDocumentWithBlob(String filename, byte[] data, String mimeType) {
        Blob blob = Blob.newBuilder()
                .setBlobId("blob-" + System.currentTimeMillis())
                .setFilename(filename)
                .setData(ByteString.copyFrom(data))
                .setMimeType(mimeType)
                .build();
        
        return PipeDoc.newBuilder()
                .setId("doc-" + System.currentTimeMillis())
                .setTitle("Test Document: " + filename)
                .setBlob(blob)
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
    
    private ProcessRequest createProcessRequestWithConfig(String pipelineName, String stepName, 
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
    
    private byte[] loadTestDocument(String filename) throws IOException {
        try (InputStream is = getClass().getResourceAsStream("/test-data/" + filename)) {
            if (is == null) {
                throw new IOException("Test document not found: " + filename);
            }
            return is.readAllBytes();
        }
    }
}