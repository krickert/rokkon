package com.rokkon.modules.tika;

import com.google.protobuf.ByteString;
import com.rokkon.search.model.Blob;
import com.rokkon.search.model.PipeDoc;
import com.rokkon.search.sdk.ProcessRequest;
import com.rokkon.search.sdk.ProcessResponse;
import com.rokkon.search.sdk.ProcessConfiguration;
import com.rokkon.search.sdk.ServiceMetadata;
import com.rokkon.search.sdk.ServiceRegistrationData;
import com.google.protobuf.Empty;
import io.smallrye.mutiny.Uni;
import io.quarkus.grpc.GrpcService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Tika service using proper Quarkus CDI injection.
 * These test the service logic with full Quarkus application context.
 */
@QuarkusTest
class TikaServiceUnitTest {
    
    @Inject
    @GrpcService
    TikaService tikaService;
    
    @Test
    void testProcessDataSimpleTextDocument() {
        // Given: A ProcessRequest with a simple text document
        String content = "This is test content for Tika processing unit test.";
        ByteString documentData = ByteString.copyFromUtf8(content);
        
        Blob blob = Blob.newBuilder()
                .setData(documentData)
                .setMimeType("text/plain")
                .setFilename("test.txt")
                .build();
        
        PipeDoc inputDoc = PipeDoc.newBuilder()
                .setId("test-doc-1")
                .setTitle("Test Document")
                .setBlob(blob)
                .build();
        
        ProcessRequest request = ProcessRequest.newBuilder()
                .setDocument(inputDoc)
                .setConfig(ProcessConfiguration.newBuilder()
                        .putConfigParams("maxContentLength", "10000")
                        .build())
                .setMetadata(ServiceMetadata.newBuilder()
                        .setPipelineName("test-pipeline")
                        .setPipeStepName("tika-step")
                        .setStreamId("test-stream-123")
                        .setCurrentHopNumber(1)
                        .build())
                .build();
        
        // When: Processing the request using CDI injected service
        Uni<ProcessResponse> responseUni = tikaService.processData(request);
        ProcessResponse response = responseUni.await().indefinitely();
        
        // Then: Should successfully parse the text document
        assertTrue(response.getSuccess(), "Processing should be successful");
        assertTrue(response.hasOutputDoc(), "Should return output document");
        
        PipeDoc outputDoc = response.getOutputDoc();
        assertEquals(inputDoc.getId(), outputDoc.getId(), "Document ID should match");
        assertFalse(outputDoc.getBody().isEmpty(), "Should extract text content");
        assertTrue(outputDoc.getBody().contains("test content"), "Should contain original text");
        
        assertTrue(response.getProcessorLogsList().size() > 0, "Should have processor logs");
        System.out.println("✅ Tika service unit test passed: processed text document correctly");
    }
    
    @Test
    void testProcessDataWithNoDocument() {
        // Given: A ProcessRequest with no document
        ProcessRequest request = ProcessRequest.newBuilder()
                .setConfig(ProcessConfiguration.newBuilder().build())
                .setMetadata(ServiceMetadata.newBuilder()
                        .setPipelineName("empty-pipeline")
                        .setPipeStepName("tika-step")
                        .setStreamId("empty-stream-789")
                        .setCurrentHopNumber(1)
                        .build())
                .build(); // No document
        
        // When: Processing the empty request using CDI injected service
        Uni<ProcessResponse> responseUni = tikaService.processData(request);
        ProcessResponse response = responseUni.await().indefinitely();
        
        // Then: Should handle gracefully but indicate processing issue
        assertFalse(response.getSuccess(), "Processing should fail with no input document");
        assertTrue(response.getProcessorLogsList().size() > 0, "Should have processor logs");
        assertTrue(response.hasErrorDetails(), "Should have error details");
        
        System.out.println("✅ Tika service unit test passed: handled empty request correctly");
    }
    
    @Test
    void testProcessDataWithNullRequest() {
        // When: Processing a null request using CDI injected service
        Uni<ProcessResponse> responseUni = tikaService.processData(null);
        ProcessResponse response = responseUni.await().indefinitely();
        
        // Then: Should handle gracefully
        assertFalse(response.getSuccess(), "Processing should fail with null request");
        assertTrue(response.hasErrorDetails(), "Should have error details");
        assertTrue(response.getProcessorLogsList().size() > 0, "Should have processor logs");
        
        System.out.println("✅ Tika service unit test passed: handled null request correctly");
    }
    
    @Test
    void testGetServiceRegistration() {
        // Given: Empty request for service registration
        Empty request = Empty.newBuilder().build();
        
        // When: Getting service registration using CDI injected service
        Uni<ServiceRegistrationData> responseUni = tikaService.getServiceRegistration(request);
        ServiceRegistrationData response = responseUni.await().indefinitely();
        
        // Then: Should return registration data
        assertEquals("tika-parser", response.getModuleName());
        // JSON schema is optional for Tika service, may or may not be present
        
        System.out.println("✅ Tika service unit test passed: service registration works correctly");
    }
    
    @Test
    void testProcessDataWithConfiguration() {
        // Given: A ProcessRequest with custom configuration
        String content = "Test document for configuration testing.";
        ByteString documentData = ByteString.copyFromUtf8(content);
        
        Blob blob = Blob.newBuilder()
                .setData(documentData)
                .setMimeType("text/plain")
                .setFilename("config-test.txt")
                .build();
        
        PipeDoc inputDoc = PipeDoc.newBuilder()
                .setId("config-test-doc")
                .setBlob(blob)
                .build();
        
        ProcessRequest request = ProcessRequest.newBuilder()
                .setDocument(inputDoc)
                .setConfig(ProcessConfiguration.newBuilder()
                        .putConfigParams("extractMetadata", "true")
                        .putConfigParams("maxContentLength", "5000")
                        .build())
                .setMetadata(ServiceMetadata.newBuilder()
                        .setPipelineName("config-test-pipeline")
                        .setPipeStepName("tika-config-step")
                        .setStreamId("config-test-stream")
                        .setCurrentHopNumber(1)
                        .build())
                .build();
        
        // When: Processing with configuration using CDI injected service
        Uni<ProcessResponse> responseUni = tikaService.processData(request);
        ProcessResponse response = responseUni.await().indefinitely();
        
        // Then: Should process successfully with configuration
        assertTrue(response.getSuccess(), "Processing with configuration should be successful");
        assertTrue(response.hasOutputDoc(), "Should return output document");
        
        PipeDoc outputDoc = response.getOutputDoc();
        assertFalse(outputDoc.getBody().isEmpty(), "Should extract content");
        
        System.out.println("✅ Tika service unit test passed: processed with configuration correctly");
    }
}