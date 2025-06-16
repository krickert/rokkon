package com.rokkon.modules.tika.reference;

import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import com.rokkon.modules.tika.TikaService;
import com.rokkon.search.model.Blob;
import com.rokkon.search.model.PipeDoc;
import com.rokkon.search.sdk.*;
import io.smallrye.mutiny.Uni;
import io.quarkus.grpc.GrpcService;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for TikaService using proper Quarkus dependency injection.
 * These tests exercise the service with proper CDI injection without mocks.
 */
@QuarkusTest
public class TikaServiceReferenceIntegrationTest {

    @Inject
    @GrpcService
    TikaService tikaService;

    @Test
    public void testProcessSimpleTextDocument() {
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
        
        // Execute direct service call with CDI injection using reactive pattern
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
    public void testProcessEmptyDocument() {
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
        
        // Execute direct service call with CDI injection using reactive pattern
        Uni<ProcessResponse> responseUni = tikaService.processData(request);
        ProcessResponse response = responseUni.await().indefinitely();
        
        // Verify - empty documents should fail parsing
        assertNotNull(response);
        assertFalse(response.getSuccess());
    }

    @Test
    public void testProcessWithCustomConfiguration() {
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
        
        // Execute direct service call with CDI injection using reactive pattern
        Uni<ProcessResponse> responseUni = tikaService.processData(request);
        ProcessResponse response = responseUni.await().indefinitely();
        
        // Verify
        assertNotNull(response);
        assertTrue(response.getSuccess());
        assertTrue(response.hasOutputDoc());
        
        PipeDoc resultDoc = response.getOutputDoc();
        assertEquals(content, resultDoc.getBody());
        // Verify metadata was extracted in the blob
        if (resultDoc.hasBlob()) {
            assertFalse(resultDoc.getBlob().getMetadataMap().isEmpty());
        }
    }

    @Test
    public void testGetServiceRegistration() {
        Empty request = Empty.newBuilder().build();
        
        // Execute direct service call with CDI injection using reactive pattern
        Uni<ServiceRegistrationData> responseUni = tikaService.getServiceRegistration(request);
        ServiceRegistrationData registration = responseUni.await().indefinitely();
        assertNotNull(registration);
        assertEquals("tika-parser", registration.getModuleName());
        assertTrue(registration.hasJsonConfigSchema());
        assertFalse(registration.getJsonConfigSchema().isEmpty());
    }

    @Test
    public void testProcessLargeDocument() {
        // Create a large document to test content length limits
        StringBuilder largeContent = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            largeContent.append("This is line ").append(i).append(" of a large test document. ");
        }
        
        ByteString document = ByteString.copyFromUtf8(largeContent.toString());
        
        Blob blob = Blob.newBuilder()
                .setData(document)
                .setMimeType("text/plain")
                .setFilename("large.txt")
                .build();
        
        PipeDoc pipeDoc = PipeDoc.newBuilder()
                .setId("large-doc")
                .setBlob(blob)
                .build();
        
        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .putConfigParams("filename", "large.txt")
                .putConfigParams("maxContentLength", "1000000")
                .build();
        
        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setPipelineName("test-pipeline")
                .setPipeStepName("tika-step")
                .setStreamId("test-stream-large")
                .setCurrentHopNumber(1)
                .build();
        
        ProcessRequest request = ProcessRequest.newBuilder()
                .setDocument(pipeDoc)
                .setConfig(config)
                .setMetadata(metadata)
                .build();
        
        // Execute direct service call with CDI injection using reactive pattern
        Uni<ProcessResponse> responseUni = tikaService.processData(request);
        ProcessResponse response = responseUni.await().indefinitely();
        assertNotNull(response);
        assertTrue(response.getSuccess());
        assertTrue(response.hasOutputDoc());
        
        PipeDoc resultDoc = response.getOutputDoc();
        assertEquals(largeContent.toString(), resultDoc.getBody());
    }

    @Test
    public void testProcessPdfDocument() {
        // Test with a simple PDF-like binary content (mock PDF header)
        byte[] pdfContent = "%PDF-1.4\n1 0 obj\n<<\n/Type /Catalog\n/Pages 2 0 R\n>>\nendobj\n".getBytes();
        ByteString document = ByteString.copyFrom(pdfContent);
        
        Blob blob = Blob.newBuilder()
                .setData(document)
                .setMimeType("application/pdf")
                .setFilename("test.pdf")
                .build();
        
        PipeDoc pipeDoc = PipeDoc.newBuilder()
                .setId("pdf-doc")
                .setBlob(blob)
                .build();
        
        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .putConfigParams("filename", "test.pdf")
                .putConfigParams("extractMetadata", "true")
                .build();
        
        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setPipelineName("test-pipeline")
                .setPipeStepName("tika-step")
                .setStreamId("test-stream-pdf")
                .setCurrentHopNumber(1)
                .build();
        
        ProcessRequest request = ProcessRequest.newBuilder()
                .setDocument(pipeDoc)
                .setConfig(config)
                .setMetadata(metadata)
                .build();
        
        // Execute direct service call with CDI injection using reactive pattern
        Uni<ProcessResponse> responseUni = tikaService.processData(request);
        ProcessResponse response = responseUni.await().indefinitely();
        
        // Verify - this should succeed even if it's not a complete PDF
        assertNotNull(response);
        assertTrue(response.getSuccess() || response.hasErrorDetails());
        
        if (response.getSuccess()) {
            assertTrue(response.hasOutputDoc());
            PipeDoc resultDoc = response.getOutputDoc();
            assertNotNull(resultDoc.getBody());
        }
    }
}