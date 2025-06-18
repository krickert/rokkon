package com.rokkon.modules.embedder;

import com.google.protobuf.Empty;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.rokkon.search.model.*;
import com.rokkon.search.sdk.*;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class EmbedderServiceTestBase {

    protected abstract PipeStepProcessor getEmbedderService();

    @Test
    void testProcessData() {
        // Create a test document
        PipeDoc testDoc = PipeDoc.newBuilder()
                .setId(UUID.randomUUID().toString())
                .setBody("This is a test document body")
                .setTitle("Test Document")
                .build();

        // Create service metadata
        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setPipelineName("test-pipeline")
                .setPipeStepName("embedder-step")
                .setStreamId(UUID.randomUUID().toString())
                .setCurrentHopNumber(1)
                .putContextParams("tenant", "test-tenant")
                .build();

        // Create configuration
        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .setCustomJsonConfig(Struct.newBuilder()
                        .putFields("check_chunks", Value.newBuilder().setBoolValue(true).build())
                        .putFields("check_document_fields", Value.newBuilder().setBoolValue(true).build())
                        .build())
                .build();

        // Create request
        ProcessRequest request = ProcessRequest.newBuilder()
                .setDocument(testDoc)
                .setMetadata(metadata)
                .setConfig(config)
                .build();

        // Execute and verify
        var response = getEmbedderService().processData(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();
        
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.hasOutputDoc()).isTrue();
        assertThat(response.getOutputDoc().getId()).isEqualTo(testDoc.getId());
        assertThat(response.getProcessorLogsList()).isNotEmpty();
        assertThat(response.getProcessorLogsList()).anyMatch(log -> log.contains("processed document fields"));
    }

    @Test
    void testProcessDataWithoutDocument() {
        // Test with no document - should still succeed but with error logs
        ProcessRequest request = ProcessRequest.newBuilder()
                .setMetadata(ServiceMetadata.newBuilder()
                        .setPipelineName("test-pipeline")
                        .setPipeStepName("embedder-step")
                        .build())
                .setConfig(ProcessConfiguration.newBuilder().build())
                // No document set
                .build();

        var response = getEmbedderService().processData(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();
        
        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getProcessorLogsList()).isNotEmpty();
        assertThat(response.getProcessorLogsList()).anyMatch(log -> log.contains("Error in EmbedderService"));
    }

    @Test
    void testGetServiceRegistration() {
        var registration = getEmbedderService().getServiceRegistration(Empty.getDefaultInstance())
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();
        
        assertThat(registration.getModuleName()).isEqualTo("embedder");
        assertThat(registration.hasJsonConfigSchema()).isTrue();
        assertThat(registration.getJsonConfigSchema()).contains("EmbedderOptions");
    }
}