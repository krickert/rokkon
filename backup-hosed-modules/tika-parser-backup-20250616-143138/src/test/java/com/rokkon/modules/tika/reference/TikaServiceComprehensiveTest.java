package com.rokkon.modules.tika;

import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import com.rokkon.search.model.Blob;
import com.rokkon.search.model.PipeDoc;
import com.rokkon.search.sdk.ProcessRequest;
import com.rokkon.search.sdk.ProcessResponse;
import com.rokkon.search.sdk.ProcessConfiguration;
import com.rokkon.search.sdk.ServiceMetadata;
import com.rokkon.search.sdk.ServiceRegistrationData;
import io.smallrye.mutiny.Uni;
import io.quarkus.grpc.GrpcService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for TikaService implementation using proper Quarkus CDI injection.
 * These tests verify the service functionality with full Quarkus application context.
 */
@QuarkusTest
class TikaServiceComprehensiveTest {

    @Inject
    @GrpcService
    TikaService tikaService;

    @Test
    void testProcessSimpleTextDocument() {
        // Prepare test data
        String content = "This is a test document for Tika processing.";
        ByteString document = ByteString.copyFromUtf8(content);
        
        Blob blob = Blob.newBuilder()
                .setData(document)
                .setMimeType("text/plain")
                .setFilename("test.txt")
                .build();
        
        PipeDoc pipeDoc = PipeDoc.newBuilder()
                .setId("test-doc-1")
                .setBlob(blob)
                .build();
        
        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .putConfigParams("filename", "test.txt")
                .putConfigParams("maxContentLength", "100000")
                .build();
        
        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setPipelineName("test-pipeline")
                .setPipeStepName("tika-step")
                .setStreamId("test-stream-1")
                .setCurrentHopNumber(1)
                .build();
        
        ProcessRequest request = ProcessRequest.newBuilder()
                .setDocument(pipeDoc)
                .setConfig(config)
                .setMetadata(metadata)
                .build();
        
        // Execute using reactive pattern with CDI injected service
        Uni<ProcessResponse> responseUni = tikaService.processData(request);
        ProcessResponse response = responseUni.await().indefinitely();
        assertNotNull(response);
        assertTrue(response.getSuccess());
        assertTrue(response.hasOutputDoc());
        
        PipeDoc resultDoc = response.getOutputDoc();
        assertEquals(content, resultDoc.getBody());
        assertNotNull(resultDoc.getTitle());
    }

    @Test
    void testProcessEmptyDocument() {
        // Prepare test data
        ByteString emptyDocument = ByteString.EMPTY;
        
        Blob blob = Blob.newBuilder()
                .setData(emptyDocument)
                .setMimeType("text/plain")
                .setFilename("empty.txt")
                .build();
        
        PipeDoc pipeDoc = PipeDoc.newBuilder()
                .setId("empty-doc")
                .setBlob(blob)
                .build();
        
        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .putConfigParams("filename", "empty.txt")
                .build();
        
        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setPipelineName("test-pipeline")
                .setPipeStepName("tika-step")
                .setStreamId("test-stream-2")
                .setCurrentHopNumber(1)
                .build();
        
        ProcessRequest request = ProcessRequest.newBuilder()
                .setDocument(pipeDoc)
                .setConfig(config)
                .setMetadata(metadata)
                .build();
        
        // Execute using reactive pattern with CDI injected service
        Uni<ProcessResponse> responseUni = tikaService.processData(request);
        ProcessResponse response = responseUni.await().indefinitely();
        
        // Verify response - empty documents should fail parsing
        assertNotNull(response);
        assertFalse(response.getSuccess());
    }

    @Test
    void testProcessWithCustomConfiguration() {
        // Prepare test data
        String content = "Test document with custom configuration.";
        ByteString document = ByteString.copyFromUtf8(content);
        
        Blob blob = Blob.newBuilder()
                .setData(document)
                .setMimeType("text/plain")
                .setFilename("custom.txt")
                .build();
        
        PipeDoc pipeDoc = PipeDoc.newBuilder()
                .setId("custom-doc")
                .setBlob(blob)
                .build();
        
        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .putConfigParams("filename", "custom.txt")
                .putConfigParams("maxContentLength", "50000")
                .putConfigParams("extractMetadata", "true")
                .putConfigParams("enableGeoTopicParser", "false")
                .build();
        
        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setPipelineName("test-pipeline")
                .setPipeStepName("tika-step")
                .setStreamId("test-stream-3")
                .setCurrentHopNumber(1)
                .build();
        
        ProcessRequest request = ProcessRequest.newBuilder()
                .setDocument(pipeDoc)
                .setConfig(config)
                .setMetadata(metadata)
                .build();
        
        // Execute using reactive pattern with CDI injected service
        Uni<ProcessResponse> responseUni = tikaService.processData(request);
        ProcessResponse response = responseUni.await().indefinitely();
        assertNotNull(response);
        assertTrue(response.getSuccess());
        assertTrue(response.hasOutputDoc());
        
        PipeDoc resultDoc = response.getOutputDoc();
        assertEquals(content, resultDoc.getBody());
    }

    @Test
    void testProcessWithNullRequest() {
        // Execute with null request using reactive pattern with CDI injected service - should handle gracefully
        Uni<ProcessResponse> responseUni = tikaService.processData(null);
        ProcessResponse response = responseUni.await().indefinitely();
        
        // Verify error response
        assertNotNull(response);
        assertFalse(response.getSuccess());
    }

    @Test
    void testGetServiceRegistration() {
        Empty request = Empty.newBuilder().build();
        
        // Execute using reactive pattern with CDI injected service
        Uni<ServiceRegistrationData> responseUni = tikaService.getServiceRegistration(request);
        ServiceRegistrationData registration = responseUni.await().indefinitely();
        assertNotNull(registration);
        assertEquals("tika-parser", registration.getModuleName());
    }

    @Test
    void testProcessDataIntegrationWithReactivePattern() {
        // This test uses the reactive pattern for integration testing
        String content = "Integration test document content.";
        ByteString document = ByteString.copyFromUtf8(content);
        
        Blob blob = Blob.newBuilder()
                .setData(document)
                .setMimeType("text/plain")
                .setFilename("integration.txt")
                .build();
        
        PipeDoc pipeDoc = PipeDoc.newBuilder()
                .setId("integration-doc")
                .setBlob(blob)
                .build();
        
        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .putConfigParams("filename", "integration.txt")
                .putConfigParams("maxContentLength", "100000")
                .build();
        
        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setPipelineName("integration-pipeline")
                .setPipeStepName("tika-integration-step")
                .setStreamId("integration-stream")
                .setCurrentHopNumber(1)
                .build();
        
        ProcessRequest request = ProcessRequest.newBuilder()
                .setDocument(pipeDoc)
                .setConfig(config)
                .setMetadata(metadata)
                .build();
        
        // Execute using reactive pattern with CDI injected service
        Uni<ProcessResponse> responseUni = tikaService.processData(request);
        ProcessResponse response = responseUni.await().indefinitely();
        assertNotNull(response);
        assertTrue(response.getSuccess());
        assertTrue(response.hasOutputDoc());
        
        PipeDoc resultDoc = response.getOutputDoc();
        assertEquals(content, resultDoc.getBody());
        // Single line text documents may not have a title extracted
        assertNotNull(resultDoc.getTitle());
    }
}