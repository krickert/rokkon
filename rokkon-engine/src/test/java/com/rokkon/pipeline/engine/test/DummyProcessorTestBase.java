package com.rokkon.pipeline.engine.test;

import com.google.protobuf.Empty;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.rokkon.search.model.*;
import com.rokkon.search.sdk.*;
import com.rokkon.search.sdk.RegistrationRequest;
import com.rokkon.search.sdk.ServiceRegistrationResponse;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class DummyProcessorTestBase {

    protected abstract PipeStepProcessor getDummyProcessor();

    @Test
    void testProcessData() {
        // Create a test document
        PipeDoc testDoc = PipeDoc.newBuilder()
                .setId(UUID.randomUUID().toString())
                .setBody("This is a test document for dummy processor")
                .setTitle("Dummy Test Document")
                .build();

        // Create service metadata
        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setPipelineName("test-pipeline")
                .setPipeStepName("dummy-step")
                .setStreamId(UUID.randomUUID().toString())
                .setCurrentHopNumber(1)
                .putContextParams("test_run", "true")
                .build();

        // Create configuration
        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .putConfigParams("mode", "test")
                .build();

        // Create request
        ProcessRequest request = ProcessRequest.newBuilder()
                .setDocument(testDoc)
                .setMetadata(metadata)
                .setConfig(config)
                .build();

        // Execute and verify
        var response = getDummyProcessor().processData(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.hasOutputDoc()).isTrue();
        assertThat(response.getOutputDoc().getId()).isEqualTo(testDoc.getId());
        assertThat(response.getOutputDoc().hasCustomData()).isTrue();

        // Check that our processor added metadata
        Struct customData = response.getOutputDoc().getCustomData();
        assertThat(customData.getFieldsMap()).containsKey("processed_by");
        assertThat(customData.getFieldsMap()).containsKey("processing_timestamp");
        assertThat(customData.getFieldsOrThrow("processed_by").getStringValue()).isEqualTo("test-processor");

        assertThat(response.getProcessorLogsList()).isNotEmpty();
        assertThat(response.getProcessorLogsList()).contains("DummyProcessor: Processing document");
        assertThat(response.getProcessorLogsList()).contains("DummyProcessor: Added metadata to document");
    }

    @Test
    void testProcessDataWithoutDocument() {
        // Test with no document
        ProcessRequest request = ProcessRequest.newBuilder()
                .setMetadata(ServiceMetadata.newBuilder()
                        .setPipelineName("test-pipeline")
                        .setPipeStepName("dummy-step")
                        .build())
                .setConfig(ProcessConfiguration.newBuilder().build())
                // No document set
                .build();

        var response = getDummyProcessor().processData(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.hasOutputDoc()).isFalse();
        assertThat(response.getProcessorLogsList()).isNotEmpty();
        assertThat(response.getProcessorLogsList()).contains("DummyProcessor: Processing document");
    }

    @Test
    void testGetServiceRegistration() {
        var registration = getDummyProcessor().getServiceRegistration(RegistrationRequest.newBuilder().build())
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat(registration.getModuleName()).isEqualTo("test-processor");
        assertThat(registration.hasJsonConfigSchema()).isTrue();
        assertThat(registration.getJsonConfigSchema()).contains("prefix");
    }
}
