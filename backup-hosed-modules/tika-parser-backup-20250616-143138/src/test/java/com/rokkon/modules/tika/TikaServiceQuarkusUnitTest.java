package com.rokkon.modules.tika;

import com.rokkon.test.data.TestDocumentFactory;
import com.rokkon.test.registration.ModuleRegistrationTester;
import com.rokkon.search.model.PipeDoc;
import com.rokkon.search.sdk.ProcessRequest;
import com.rokkon.search.sdk.ProcessResponse;
import com.rokkon.search.sdk.ProcessConfiguration;
import com.rokkon.search.sdk.ServiceMetadata;
import com.rokkon.search.sdk.ServiceRegistrationData;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TikaService using @QuarkusTest.
 * Focuses on service logic, configuration handling, and edge cases.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TikaServiceQuarkusUnitTest {

    private static final Logger LOG = LoggerFactory.getLogger(TikaServiceQuarkusUnitTest.class);

    @Inject
    TikaService tikaService;

    @Inject
    TestDocumentFactory documentFactory;

    @Inject
    ModuleRegistrationTester registrationTester;

    @Test
    @Order(1)
    @DisplayName("Service Registration - Should provide valid Tika service metadata")
    void testServiceRegistration() {
        LOG.info("Testing comprehensive Tika service registration");

        registrationTester.verifyTikaServiceRegistration(tikaService.getServiceRegistration(null));
        registrationTester.verifyNullInputHandling(tikaService.getServiceRegistration(null));

        // Additional Tika-specific validation
        Uni<ServiceRegistrationData> registrationUni = tikaService.getServiceRegistration(null);
        ServiceRegistrationData registration = registrationUni.await().indefinitely();

        String schema = registration.getJsonConfigSchema();
        assertTrue(schema.contains("extractMetadata"), "Schema should include extractMetadata option");
        assertTrue(schema.contains("maxContentLength"), "Schema should include maxContentLength option");
        assertTrue(schema.contains("parseEmbeddedDocs"), "Schema should include parseEmbeddedDocs option");

        LOG.info("✅ Tika service registration comprehensive test completed");
    }

    @Test
    @Order(2)
    @DisplayName("Default Configuration - Should process with default settings")
    void testDefaultConfiguration() {
        LOG.info("Testing Tika processing with default configuration");

        PipeDoc testDoc = documentFactory.createSimpleTextDocument(
            "default-test", "Default Test Document", 
            "This is test content that should be processed by Tika service.");

        ProcessConfiguration config = ProcessConfiguration.newBuilder().build(); // Empty = defaults

        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setStreamId("default-test-stream")
                .setPipeStepName("tika-default")
                .build();

        ProcessRequest request = ProcessRequest.newBuilder()
                .setDocument(testDoc)
                .setConfig(config)
                .setMetadata(metadata)
                .build();

        Uni<ProcessResponse> responseUni = tikaService.processData(request);
        ProcessResponse response = responseUni.await().indefinitely();

        assertTrue(response.getSuccess(), "Default configuration should succeed");
        assertEquals(testDoc.getId(), response.getOutputDoc().getId());
        assertFalse(response.getProcessorLogsList().isEmpty());

        LOG.info("✅ Default configuration test completed");
    }

    @Test
    @Order(3)
    @DisplayName("Simple Document Processing - Should handle text documents")
    void testSimpleDocumentProcessing() {
        LOG.info("Testing simple document processing");

        List<PipeDoc> testDocs = documentFactory.createSimpleTestDocuments(3);

        for (PipeDoc doc : testDocs) {
            ProcessConfiguration config = ProcessConfiguration.newBuilder()
                    .putConfigParams("extractMetadata", "false")
                    .putConfigParams("maxContentLength", "10000")
                    .build();

            ServiceMetadata metadata = ServiceMetadata.newBuilder()
                    .setStreamId("simple-test-stream")
                    .setPipeStepName("tika-simple")
                    .build();

            ProcessRequest request = ProcessRequest.newBuilder()
                    .setDocument(doc)
                    .setConfig(config)
                    .setMetadata(metadata)
                    .build();

            Uni<ProcessResponse> responseUni = tikaService.processData(request);
            ProcessResponse response = responseUni.await().indefinitely();

            assertTrue(response.getSuccess(), "Simple document processing should succeed");
            assertEquals(doc.getId(), response.getOutputDoc().getId());

            LOG.debug("Processed document: {} successfully", doc.getId());
        }

        LOG.info("✅ Simple document processing test completed");
    }

    @Test
    @Order(4)
    @DisplayName("Configuration Validation - Should handle various config parameters")
    void testConfigurationValidation() {
        LOG.info("Testing configuration parameter validation");

        PipeDoc testDoc = documentFactory.createSimpleTextDocument(
            "config-validation", "Config Test", "Test content for configuration validation.");

        // Test various configuration combinations
        testConfigurationScenario(testDoc, "metadata-enabled", 
            ProcessConfiguration.newBuilder()
                .putConfigParams("extractMetadata", "true")
                .putConfigParams("maxContentLength", "50000")
                .build());

        testConfigurationScenario(testDoc, "metadata-disabled",
            ProcessConfiguration.newBuilder()
                .putConfigParams("extractMetadata", "false")
                .putConfigParams("maxContentLength", "50000")
                .build());

        testConfigurationScenario(testDoc, "limited-length",
            ProcessConfiguration.newBuilder()
                .putConfigParams("extractMetadata", "true")
                .putConfigParams("maxContentLength", "100")
                .build());

        testConfigurationScenario(testDoc, "embedded-docs",
            ProcessConfiguration.newBuilder()
                .putConfigParams("extractMetadata", "true")
                .putConfigParams("parseEmbeddedDocs", "true")
                .build());

        LOG.info("✅ Configuration validation test completed");
    }

    @Test
    @Order(5)
    @DisplayName("Error Handling - Should handle edge cases gracefully")
    void testErrorHandling() {
        LOG.info("Testing error handling and edge cases");

        // Test 1: Null request
        Uni<ProcessResponse> nullResponseUni = tikaService.processData(null);
        ProcessResponse nullResponse = nullResponseUni.await().indefinitely();

        assertFalse(nullResponse.getSuccess(), "Null request should fail gracefully");
        assertTrue(nullResponse.getProcessorLogsList().size() > 0, "Should have error logs");
        assertTrue(nullResponse.getProcessorLogs(0).contains("Request cannot be null"));

        // Test 2: Document without ID
        PipeDoc docWithoutId = PipeDoc.newBuilder()
                .setTitle("Document without ID")
                .setBody("This document has no ID")
                .build();

        ProcessRequest requestWithoutId = ProcessRequest.newBuilder()
                .setDocument(docWithoutId)
                .setConfig(ProcessConfiguration.getDefaultInstance())
                .setMetadata(ServiceMetadata.newBuilder()
                    .setStreamId("error-test")
                    .setPipeStepName("tika-error")
                    .build())
                .build();

        Uni<ProcessResponse> responseUni = tikaService.processData(requestWithoutId);
        ProcessResponse response = responseUni.await().indefinitely();

        // Should either succeed (pass-through) or fail gracefully
        assertNotNull(response, "Response should not be null");

        // Test 3: Empty configuration
        PipeDoc normalDoc = documentFactory.createSimpleTextDocument(
            "normal-doc", "Normal Document", "Normal content");

        ProcessRequest emptyConfigRequest = ProcessRequest.newBuilder()
                .setDocument(normalDoc)
                .setConfig(ProcessConfiguration.getDefaultInstance())
                .setMetadata(ServiceMetadata.getDefaultInstance())
                .build();

        Uni<ProcessResponse> emptyConfigResponseUni = tikaService.processData(emptyConfigRequest);
        ProcessResponse emptyConfigResponse = emptyConfigResponseUni.await().indefinitely();

        assertTrue(emptyConfigResponse.getSuccess(), "Empty config should use defaults");

        LOG.info("✅ Error handling test completed");
    }

    @Test
    @Order(6)
    @DisplayName("Performance Baseline - Should meet basic performance requirements")
    void testPerformanceBaseline() {
        LOG.info("Testing basic performance requirements");

        List<PipeDoc> testDocs = documentFactory.createSimpleTestDocuments(5);

        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .putConfigParams("extractMetadata", "false") // Faster processing
                .putConfigParams("maxContentLength", "10000")
                .build();

        long startTime = System.currentTimeMillis();
        int successCount = 0;

        for (PipeDoc doc : testDocs) {
            ServiceMetadata metadata = ServiceMetadata.newBuilder()
                    .setStreamId("performance-test")
                    .setPipeStepName("tika-performance")
                    .build();

            ProcessRequest request = ProcessRequest.newBuilder()
                    .setDocument(doc)
                    .setConfig(config)
                    .setMetadata(metadata)
                    .build();

            Uni<ProcessResponse> responseUni = tikaService.processData(request);
            ProcessResponse response = responseUni.await().indefinitely();

            if (response.getSuccess()) {
                successCount++;
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        double avgProcessingTime = (double) duration / testDocs.size();

        LOG.info("Performance results: {}/{} docs in {}ms (avg: {:.1f}ms/doc)", 
            successCount, testDocs.size(), duration, avgProcessingTime);

        assertEquals(testDocs.size(), successCount, "All documents should process successfully");
        assertTrue(avgProcessingTime < 1000, "Average processing time should be reasonable (< 1s)");

        LOG.info("✅ Performance baseline test completed");
    }

    @Test
    @Order(7)
    @DisplayName("Content Validation - Should preserve document structure")
    void testContentValidation() {
        LOG.info("Testing content validation and document structure preservation");

        PipeDoc originalDoc = documentFactory.createSimpleTextDocument(
            "content-test", "Content Validation Test", 
            "This is original content that should be preserved through Tika processing.");

        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .putConfigParams("extractMetadata", "true")
                .putConfigParams("maxContentLength", "100000")
                .build();

        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setStreamId("content-test-stream")
                .setPipeStepName("tika-content")
                .build();

        ProcessRequest request = ProcessRequest.newBuilder()
                .setDocument(originalDoc)
                .setConfig(config)
                .setMetadata(metadata)
                .build();

        Uni<ProcessResponse> responseUni = tikaService.processData(request);
        ProcessResponse response = responseUni.await().indefinitely();

        assertTrue(response.getSuccess(), "Content validation should succeed");

        PipeDoc processedDoc = response.getOutputDoc();

        // Verify document structure is preserved
        assertEquals(originalDoc.getId(), processedDoc.getId(), "Document ID should be preserved");
        assertEquals(originalDoc.getTitle(), processedDoc.getTitle(), "Document title should be preserved");
        
        // Content should either be preserved or enhanced (not lost)
        assertTrue(processedDoc.hasBody(), "Processed document should have body content");
        assertFalse(processedDoc.getBody().trim().isEmpty(), "Body content should not be empty");

        LOG.info("Original content length: {}, Processed content length: {}", 
            originalDoc.getBody().length(), processedDoc.getBody().length());

        LOG.info("✅ Content validation test completed");
    }

    private void testConfigurationScenario(PipeDoc testDoc, String scenarioName, ProcessConfiguration config) {
        LOG.debug("Testing configuration scenario: {}", scenarioName);

        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setStreamId("config-scenario-test")
                .setPipeStepName("tika-" + scenarioName)
                .build();

        ProcessRequest request = ProcessRequest.newBuilder()
                .setDocument(testDoc)
                .setConfig(config)
                .setMetadata(metadata)
                .build();

        Uni<ProcessResponse> responseUni = tikaService.processData(request);
        ProcessResponse response = responseUni.await().indefinitely();

        assertTrue(response.getSuccess(), 
            "Configuration scenario '" + scenarioName + "' should succeed");

        LOG.debug("✅ Configuration scenario '{}' completed", scenarioName);
    }
}