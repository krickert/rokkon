package com.rokkon.echo;

import com.google.protobuf.Empty;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.rokkon.search.model.*;
import com.rokkon.search.sdk.*;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class EchoServiceTestBase {

    protected abstract PipeStepProcessor getEchoService();

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
                .setPipeStepName("echo-step")
                .setStreamId(UUID.randomUUID().toString())
                .setCurrentHopNumber(1)
                .putContextParams("tenant", "test-tenant")
                .build();

        // Create configuration
        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .setCustomJsonConfig(Struct.newBuilder()
                        .putFields("mode", Value.newBuilder().setStringValue("echo").build())
                        .build())
                .putConfigParams("mode", "echo")
                .build();

        // Create request
        ProcessRequest request = ProcessRequest.newBuilder()
                .setDocument(testDoc)
                .setMetadata(metadata)
                .setConfig(config)
                .build();

        // Execute and verify
        var response = getEchoService().processData(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();
        
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.hasOutputDoc()).isTrue();
        assertThat(response.getOutputDoc().getId()).isEqualTo(testDoc.getId());
        assertThat(response.getOutputDoc().getBody()).isEqualTo(testDoc.getBody());
        assertThat(response.getProcessorLogsList()).isNotEmpty();
        assertThat(response.getProcessorLogsList()).anyMatch(log -> log.contains("successfully processed"));
    }

    @Test
    void testProcessDataWithoutDocument() {
        // Test with no document - should still succeed (echo service is tolerant)
        ProcessRequest request = ProcessRequest.newBuilder()
                .setMetadata(ServiceMetadata.newBuilder()
                        .setPipelineName("test-pipeline")
                        .setPipeStepName("echo-step")
                        .build())
                .setConfig(ProcessConfiguration.newBuilder().build())
                // No document set
                .build();

        var response = getEchoService().processData(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();
        
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.hasOutputDoc()).isFalse();
        assertThat(response.getProcessorLogsList()).isNotEmpty();
        assertThat(response.getProcessorLogsList()).anyMatch(log -> log.contains("successfully processed"));
    }

    @Test
    void testGetServiceRegistration() {
        var registration = getEchoService().getServiceRegistration(Empty.getDefaultInstance())
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();
        
        assertThat(registration.getModuleName()).isEqualTo("echo-module");
        // Echo service has no JSON schema - it accepts any input
        assertThat(registration.hasJsonConfigSchema()).isFalse();
    }
}