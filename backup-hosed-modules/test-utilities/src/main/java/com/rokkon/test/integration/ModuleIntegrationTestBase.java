package com.rokkon.test.integration;

import com.rokkon.search.model.PipeDoc;
import com.rokkon.search.sdk.*;
import com.rokkon.test.data.TestDocumentFactory;
import io.grpc.Channel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Base class for module integration tests that provides common test patterns
 * for all modules implementing the PipeStepProcessor gRPC interface.
 * 
 * All Rokkon modules (tika-parser, chunker, embedder, etc.) share the same
 * gRPC interface, so this base class provides standard connectivity and
 * functionality tests that every module should pass.
 * 
 * Subclasses only need to implement the channel() method to specify
 * how to connect to their specific module.
 */
public abstract class ModuleIntegrationTestBase {
    
    private static final Logger LOG = LoggerFactory.getLogger(ModuleIntegrationTestBase.class);
    
    private final TestDocumentFactory documentFactory = new TestDocumentFactory();
    
    /**
     * Subclasses must implement this to provide a gRPC channel to their module.
     * 
     * @return Channel configured to connect to the module under test
     */
    protected abstract Channel channel();
    
    /**
     * Gets a blocking stub client for the PipeStepProcessor service.
     * 
     * @return Configured blocking stub with timeout
     */
    protected PipeStepProcessorGrpc.PipeStepProcessorBlockingStub getClient() {
        return PipeStepProcessorGrpc.newBlockingStub(channel())
                .withDeadlineAfter(30, TimeUnit.SECONDS);
    }
    
    /**
     * Gets a blocking stub client with custom timeout.
     * 
     * @param timeout timeout value
     * @param unit timeout unit
     * @return Configured blocking stub with custom timeout
     */
    protected PipeStepProcessorGrpc.PipeStepProcessorBlockingStub getClient(long timeout, TimeUnit unit) {
        return PipeStepProcessorGrpc.newBlockingStub(channel())
                .withDeadlineAfter(timeout, unit);
    }
    
    @Test
    @DisplayName("Basic Connectivity - Should connect to module service")
    void testBasicConnectivity() {
        LOG.info("Testing basic connectivity to module service");
        
        PipeDoc testDoc = documentFactory.createSimpleTextDocument(
            "connectivity-test", 
            "Basic Connectivity Test", 
            "This is a test document to verify basic module connectivity."
        );
        
        ProcessRequest request = createBasicProcessRequest("connectivity-test", testDoc);
        
        PipeStepProcessorGrpc.PipeStepProcessorBlockingStub client = getClient();
        ProcessResponse response = client.processData(request);
        
        assertNotNull(response, "Response should not be null");
        assertTrue(response.getSuccess(), "Basic connectivity test should succeed");
        assertNotNull(response.getOutputDoc(), "Output document should be present");
        assertEquals(testDoc.getId(), response.getOutputDoc().getId(), "Document ID should be preserved");
        
        LOG.info("✅ Basic connectivity test passed");
    }
    
    @Test
    @DisplayName("Process Data - Should handle standard document processing")
    void testProcessData() {
        LOG.info("Testing standard document processing");
        
        PipeDoc testDoc = documentFactory.createSimpleTextDocument(
            "process-test",
            "Process Data Test",
            "This is a longer test document with multiple sentences. " +
            "It contains various content that the module should process correctly. " +
            "The module should handle this content appropriately for its specific function."
        );
        
        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .putConfigParams("testMode", "true")
                .build();
        
        ProcessRequest request = createProcessRequest("process-test-pipeline", "module-process", testDoc, config);
        
        PipeStepProcessorGrpc.PipeStepProcessorBlockingStub client = getClient();
        ProcessResponse response = client.processData(request);
        
        assertTrue(response.getSuccess(), "Process data should succeed");
        assertNotNull(response.getOutputDoc(), "Output document should be present");
        assertEquals(testDoc.getId(), response.getOutputDoc().getId(), "Document ID should be preserved");
        
        // Check that processing actually occurred (output should be different from input in some way)
        // This is module-specific, but we can check basic properties
        PipeDoc outputDoc = response.getOutputDoc();
        assertNotNull(outputDoc.getTitle(), "Output document should have a title");
        
        LOG.info("✅ Process data test passed");
    }
    
    @Test
    @DisplayName("Error Handling - Should handle invalid inputs gracefully")
    void testErrorHandling() {
        LOG.info("Testing error handling with edge cases");
        
        // Test with empty document
        PipeDoc emptyDoc = PipeDoc.newBuilder()
                .setId("empty-test")
                .setTitle("Empty Document Test")
                .build();
        
        ProcessRequest emptyRequest = createBasicProcessRequest("error-test", emptyDoc);
        
        PipeStepProcessorGrpc.PipeStepProcessorBlockingStub client = getClient();
        ProcessResponse response = client.processData(emptyRequest);
        
        assertNotNull(response, "Response should not be null even for edge cases");
        // Note: Some modules might succeed with empty docs (pass-through), others might fail gracefully
        // The important thing is that we get a response, not an exception
        
        if (!response.getSuccess()) {
            assertFalse(response.getProcessorLogsList().isEmpty(), 
                "Failed responses should include processor logs");
            LOG.info("Module handled empty document gracefully with error: {}", 
                response.getProcessorLogsList().get(0));
        } else {
            LOG.info("Module passed through empty document successfully");
        }
        
        LOG.info("✅ Error handling test passed");
    }
    
    @Test
    @DisplayName("Configuration Handling - Should respect processing configuration")
    void testConfigurationHandling() {
        LOG.info("Testing configuration parameter handling");
        
        PipeDoc testDoc = documentFactory.createSimpleTextDocument(
            "config-test",
            "Configuration Test",
            "This document tests whether the module properly handles configuration parameters."
        );
        
        // Test with different configurations
        ProcessConfiguration defaultConfig = ProcessConfiguration.getDefaultInstance();
        ProcessConfiguration customConfig = ProcessConfiguration.newBuilder()
                .putConfigParams("maxOutputLength", "1000")
                .putConfigParams("enableDebugMode", "true")
                .build();
        
        // Test with default config
        ProcessRequest defaultRequest = createProcessRequest("config-default", "module-config-default", testDoc, defaultConfig);
        ProcessResponse defaultResponse = getClient().processData(defaultRequest);
        assertTrue(defaultResponse.getSuccess(), "Default configuration should work");
        
        // Test with custom config
        ProcessRequest customRequest = createProcessRequest("config-custom", "module-config-custom", testDoc, customConfig);
        ProcessResponse customResponse = getClient().processData(customRequest);
        assertTrue(customResponse.getSuccess(), "Custom configuration should work");
        
        LOG.info("✅ Configuration handling test passed");
    }
    
    @Test
    @DisplayName("Performance - Should handle reasonable processing loads")
    void testBasicPerformance() {
        LOG.info("Testing basic performance with multiple documents");
        
        int documentCount = 5; // Keep it reasonable for integration tests
        long startTime = System.currentTimeMillis();
        int successCount = 0;
        
        for (int i = 0; i < documentCount; i++) {
            PipeDoc testDoc = documentFactory.createSimpleTextDocument(
                "perf-test-" + i,
                "Performance Test Document " + (i + 1),
                "This is performance test document number " + (i + 1) + ". " +
                "It contains some content to process. The module should handle this efficiently."
            );
            
            ProcessRequest request = createBasicProcessRequest("performance-test-" + i, testDoc);
            
            try {
                ProcessResponse response = getClient(10, TimeUnit.SECONDS).processData(request);
                if (response.getSuccess()) {
                    successCount++;
                }
            } catch (Exception e) {
                LOG.warn("Performance test document {} failed: {}", i, e.getMessage());
            }
        }
        
        long duration = System.currentTimeMillis() - startTime;
        double successRate = (double) successCount / documentCount;
        
        LOG.info("Performance test results: {}/{} documents in {}ms ({:.1f}% success rate)", 
            successCount, documentCount, duration, successRate * 100);
        
        assertTrue(successRate >= 0.8, "Should successfully process at least 80% of documents");
        assertTrue(duration < 30000, "Should complete within 30 seconds");
        
        LOG.info("✅ Basic performance test passed");
    }
    
    /**
     * Creates a basic ProcessRequest with minimal configuration.
     */
    protected ProcessRequest createBasicProcessRequest(String stepName, PipeDoc document) {
        return createProcessRequest("integration-test-pipeline", stepName, document, ProcessConfiguration.getDefaultInstance());
    }
    
    /**
     * Creates a ProcessRequest with custom configuration.
     */
    protected ProcessRequest createProcessRequest(String pipelineName, String stepName, PipeDoc document, ProcessConfiguration config) {
        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setPipelineName(pipelineName)
                .setPipeStepName(stepName)
                .setStreamId("stream-" + System.currentTimeMillis())
                .setCurrentHopNumber(1)
                .build();
        
        return ProcessRequest.newBuilder()
                .setDocument(document)
                .setConfig(config)
                .setMetadata(metadata)
                .build();
    }
    
    /**
     * Gets the test document factory for creating test documents.
     */
    protected TestDocumentFactory getDocumentFactory() {
        return documentFactory;
    }
}