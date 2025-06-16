package com.rokkon.modules.echo;

import com.rokkon.search.model.PipeDoc;
import com.rokkon.search.sdk.ProcessRequest;
import com.rokkon.search.sdk.ProcessResponse;
import com.rokkon.search.sdk.ProcessConfiguration;
import com.rokkon.search.sdk.ServiceMetadata;
import com.rokkon.search.sdk.ServiceRegistrationData;
import com.google.protobuf.Empty;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Echo service that don't require gRPC connectivity.
 * These test the service logic in isolation.
 */
class EchoServiceUnitTest {
    
    private EchoService echoService;
    
    @BeforeEach
    void setUp() {
        echoService = new EchoService();
    }
    
    @Test
    void testProcessDataEchosSingleDocument() {
        // Given: A ProcessRequest with a single document
        PipeDoc inputDoc = PipeDoc.newBuilder()
                .setId("test-doc-1")
                .setTitle("Test Document")
                .setBody("This is test content for echo service")
                .build();
        
        ProcessRequest request = ProcessRequest.newBuilder()
                .setDocument(inputDoc)
                .setConfig(ProcessConfiguration.newBuilder().build())
                .setMetadata(ServiceMetadata.newBuilder()
                        .setPipelineName("test-pipeline")
                        .setPipeStepName("echo-step")
                        .setStreamId("test-stream-123")
                        .setCurrentHopNumber(1)
                        .build())
                .build();
        
        // When: Processing the request
        Uni<ProcessResponse> responseUni = echoService.processData(request);
        ProcessResponse response = responseUni.await().indefinitely();
        
        // Then: Should echo back the same document
        assertTrue(response.getSuccess(), "Processing should be successful");
        assertTrue(response.hasOutputDoc(), "Should return output document");
        
        PipeDoc outputDoc = response.getOutputDoc();
        assertEquals(inputDoc.getId(), outputDoc.getId(), "Document ID should match");
        assertEquals(inputDoc.getTitle(), outputDoc.getTitle(), "Document title should match");
        assertEquals(inputDoc.getBody(), outputDoc.getBody(), "Document body should match");

        assertFalse(response.getProcessorLogsList().isEmpty(), "Should have processor logs");
        System.out.println("✅ Echo service unit test passed: echoed document correctly");
    }
    
    @Test
    void testProcessDataWithNoDocument() {
        // Given: A ProcessRequest with no document
        ProcessRequest request = ProcessRequest.newBuilder()
                .setConfig(ProcessConfiguration.newBuilder().build())
                .setMetadata(ServiceMetadata.newBuilder()
                        .setPipelineName("empty-pipeline")
                        .setPipeStepName("echo-step")
                        .setStreamId("empty-stream-789")
                        .setCurrentHopNumber(1)
                        .build())
                .build(); // No document
        
        // When: Processing the empty request
        Uni<ProcessResponse> responseUni = echoService.processData(request);
        ProcessResponse response = responseUni.await().indefinitely();
        
        // Then: Should return successful response with no output document
        assertTrue(response.getSuccess(), "Processing should be successful even with no input document");
        assertFalse(response.hasOutputDoc(), "Should not return output document for empty input");
        assertFalse(response.getProcessorLogsList().isEmpty(), "Should have processor logs");
        
        System.out.println("✅ Echo service unit test passed: handled empty request correctly");
    }
    
    @Test
    void testGetServiceRegistration() {
        // Given: Empty request for service registration
        Empty request = Empty.newBuilder().build();
        
        // When: Getting service registration
        Uni<ServiceRegistrationData> responseUni = echoService.getServiceRegistration(request);
        ServiceRegistrationData response = responseUni.await().indefinitely();
        
        // Then: Should return registration data
        assertEquals("echo-service", response.getModuleName());
        assertFalse(response.hasJsonConfigSchema(), "Echo service should not have a JSON schema");
        
        System.out.println("✅ Echo service unit test passed: service registration works correctly");
    }
}