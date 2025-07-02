package com.rokkon.parser;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.rokkon.search.model.*;
import com.rokkon.search.sdk.*;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class ParserServiceTestBase {

    protected abstract PipeStepProcessor getParserService();

    @Test
    void testProcessData() {
        // Create a test document with blob data for parsing
        String testContent = "This is a sample text document that will be parsed by Tika.";
        Blob testBlob = Blob.newBuilder()
                .setData(com.google.protobuf.ByteString.copyFromUtf8(testContent))
                .setMimeType("text/plain")
                .setFilename("test.txt")
                .build();
        
        PipeDoc testDoc = PipeDoc.newBuilder()
                .setId(UUID.randomUUID().toString())
                .setBlob(testBlob)
                .build();

        // Create service metadata
        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setPipelineName("test-pipeline")
                .setPipeStepName("parser-step")
                .setStreamId(UUID.randomUUID().toString())
                .setCurrentHopNumber(1)
                .putContextParams("tenant", "test-tenant")
                .build();

        // Create configuration
        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .setCustomJsonConfig(Struct.newBuilder()
                        .putFields("mode", Value.newBuilder().setStringValue("parser").build())
                        .build())
                .putConfigParams("mode", "parser")
                .build();

        // Create request
        ProcessRequest request = ProcessRequest.newBuilder()
                .setDocument(testDoc)
                .setMetadata(metadata)
                .setConfig(config)
                .build();

        // Execute and verify
        var response = getParserService().processData(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();
        
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.hasOutputDoc()).isTrue();
        assertThat(response.getOutputDoc().getId()).isEqualTo(testDoc.getId());
        assertThat(response.getOutputDoc().getBody()).contains("sample text document"); // Parsed content
        assertThat(response.getProcessorLogsList()).isNotEmpty();
        assertThat(response.getProcessorLogsList()).anyMatch(log -> log.contains("successfully processed"));
    }

    @Test
    void testProcessDataWithoutDocument() {
        // Test with no document - should still succeed (parser service is tolerant)
        ProcessRequest request = ProcessRequest.newBuilder()
                .setMetadata(ServiceMetadata.newBuilder()
                        .setPipelineName("test-pipeline")
                        .setPipeStepName("parser-step")
                        .build())
                .setConfig(ProcessConfiguration.newBuilder().build())
                // No document set
                .build();

        var response = getParserService().processData(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();
        
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.hasOutputDoc()).isFalse();
        assertThat(response.getProcessorLogsList()).isNotEmpty();
        assertThat(response.getProcessorLogsList()).anyMatch(log -> log.contains("no document"));
    }

    @Test
    void testGetServiceRegistration() {
        RegistrationRequest request = RegistrationRequest.newBuilder().build();
        var registration = getParserService().getServiceRegistration(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();
        
        assertThat(registration.getModuleName()).isEqualTo("parser");
        assertThat(registration.hasJsonConfigSchema()).isTrue();
        assertThat(registration.getHealthCheckPassed()).isTrue();
        assertThat(registration.getHealthCheckMessage()).contains("No health check performed");
    }
}