package com.rokkon.modules.chunker;

import com.google.protobuf.Empty;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.rokkon.search.model.PipeDoc;
import com.rokkon.search.sdk.ProcessRequest;
import com.rokkon.search.sdk.ProcessResponse;
import com.rokkon.search.sdk.ProcessConfiguration;
import com.rokkon.search.sdk.ServiceMetadata;
import com.rokkon.search.sdk.ServiceRegistrationData;
import com.rokkon.search.sdk.PipeStepProcessorGrpc;
import io.quarkus.grpc.GrpcService;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for ChunkerService using @QuarkusTest.
 * Tests the service within the Quarkus application context with full CDI injection.
 */
@QuarkusTest
class ChunkerServiceQuarkusTest {

    @Inject
    @GrpcService
    ChunkerService chunkerService;

    @Test
    void testQuarkusApplicationStartup() {
        assertNotNull(chunkerService, "ChunkerService should be injected by Quarkus CDI");
    }

    @Test
    void testServiceImplementsCorrectInterface() {
        assertTrue(chunkerService instanceof PipeStepProcessorGrpc.PipeStepProcessorImplBase,
                "ChunkerService should extend PipeStepProcessorImplBase");
    }

    @Test
    void testServiceHasGrpcServiceAnnotation() {
        assertTrue(chunkerService.getClass().isAnnotationPresent(GrpcService.class),
                "ChunkerService should have @GrpcService annotation");
    }

    @Test
    void testProcessDataReturnsUni() {
        PipeDoc testDoc = PipeDoc.newBuilder()
                .setId("test-doc")
                .setBody("Test content for chunking")
                .build();

        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .setCustomJsonConfig(Struct.newBuilder()
                        .putFields("source_field", Value.newBuilder().setStringValue("body").build())
                        .putFields("chunk_size", Value.newBuilder().setNumberValue(100).build())
                        .build())
                .build();

        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setStreamId("test-stream")
                .setPipeStepName("chunker-test")
                .build();

        ProcessRequest request = ProcessRequest.newBuilder()
                .setDocument(testDoc)
                .setConfig(config)
                .setMetadata(metadata)
                .build();

        Uni<ProcessResponse> responseUni = chunkerService.processData(request);
        assertNotNull(responseUni, "processData should return a Uni<ProcessResponse>");

        ProcessResponse response = responseUni.await().indefinitely();
        assertNotNull(response, "Response should not be null");
        assertTrue(response.getSuccess(), "Processing should be successful");
        assertEquals("test-doc", response.getOutputDoc().getId(), "Output document should maintain original ID");
    }

    @Test
    void testGetServiceRegistrationReturnsUni() {
        Uni<ServiceRegistrationData> registrationUni = chunkerService.getServiceRegistration(Empty.getDefaultInstance());
        assertNotNull(registrationUni, "getServiceRegistration should return a Uni<ServiceRegistrationData>");

        ServiceRegistrationData registration = registrationUni.await().indefinitely();
        assertNotNull(registration, "Registration should not be null");
        assertEquals("chunker", registration.getModuleName(), "Module name should be 'chunker'");
        assertFalse(registration.getJsonConfigSchema().isEmpty(), "JSON schema should not be empty");
    }

    @Test
    void testProcessDataWithMinimalRequest() {
        PipeDoc minimalDoc = PipeDoc.newBuilder()
                .setId("minimal-doc")
                .setBody("Short text")
                .build();

        ProcessRequest minimalRequest = ProcessRequest.newBuilder()
                .setDocument(minimalDoc)
                .setConfig(ProcessConfiguration.newBuilder().build())
                .setMetadata(ServiceMetadata.newBuilder()
                        .setStreamId("minimal-stream")
                        .setPipeStepName("minimal-chunker")
                        .build())
                .build();

        Uni<ProcessResponse> responseUni = chunkerService.processData(minimalRequest);
        ProcessResponse response = responseUni.await().indefinitely();

        assertNotNull(response);
        assertTrue(response.getSuccess());
        assertEquals("minimal-doc", response.getOutputDoc().getId());
    }

    @Test
    void testServiceRegistrationJsonSchema() {
        Uni<ServiceRegistrationData> registrationUni = chunkerService.getServiceRegistration(Empty.getDefaultInstance());
        ServiceRegistrationData registration = registrationUni.await().indefinitely();

        String schema = registration.getJsonConfigSchema();
        assertTrue(schema.contains("ChunkerOptions"), "Schema should contain ChunkerOptions title");
        assertTrue(schema.contains("source_field"), "Schema should define source_field property");
        assertTrue(schema.contains("chunk_size"), "Schema should define chunk_size property");
        assertTrue(schema.contains("chunk_overlap"), "Schema should define chunk_overlap property");
        assertTrue(schema.contains("preserve_urls"), "Schema should define preserve_urls property");
    }
}