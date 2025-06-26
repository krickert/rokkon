package com.rokkon.pipeline.engine.test;

import com.google.protobuf.Empty;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.rokkon.search.model.*;
import com.rokkon.search.sdk.PipeStepProcessor;
import com.rokkon.search.sdk.ProcessRequest;
import com.rokkon.search.sdk.ProcessResponse;
import com.rokkon.search.sdk.RegistrationRequest;
import com.rokkon.search.sdk.ServiceRegistrationResponse;
import com.rokkon.search.sdk.ServiceMetadata;
import com.rokkon.search.sdk.ProcessConfiguration;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base test class for testing with the real test-module processor.
 * This replaces DummyProcessorTestBase for new tests.
 */
public abstract class TestModuleProcessorTestBase {

    protected abstract PipeStepProcessor getProcessor();

    @Test
    void testProcessData() {
        // Create a test document
        PipeDoc testDoc = PipeDoc.newBuilder()
                .setId(UUID.randomUUID().toString())
                .setBody("This is a test document for test processor")
                .setTitle("Test Document")
                .build();

        // Create service metadata
        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setPipelineName("test-pipeline")
                .setPipeStepName("test-step")
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
        var response = getProcessor().processData(request)
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
        assertThat(customData.getFieldsMap()).containsKey("test_module_version");
        assertThat(customData.getFieldsOrThrow("processed_by").getStringValue()).isEqualTo("test-processor");
        assertThat(customData.getFieldsOrThrow("test_module_version").getStringValue()).isEqualTo("1.0.0");

        // Check config params were added
        assertThat(customData.getFieldsMap()).containsKey("config_mode");
        assertThat(customData.getFieldsOrThrow("config_mode").getStringValue()).isEqualTo("test");

        assertThat(response.getProcessorLogsList()).isNotEmpty();
        assertThat(response.getProcessorLogsList().toString()).contains("Starting document processing");
        assertThat(response.getProcessorLogsList().toString()).contains("Added metadata to document");
        assertThat(response.getProcessorLogsList().toString()).contains("Document processed successfully");
    }

    @Test
    void testProcessDataWithoutDocument() {
        // Test with no document
        ProcessRequest request = ProcessRequest.newBuilder()
                .setMetadata(ServiceMetadata.newBuilder()
                        .setPipelineName("test-pipeline")
                        .setPipeStepName("test-step")
                        .build())
                .setConfig(ProcessConfiguration.newBuilder().build())
                // No document set
                .build();

        var response = getProcessor().processData(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.hasOutputDoc()).isFalse();
        assertThat(response.getProcessorLogsList()).isNotEmpty();
        assertThat(response.getProcessorLogsList().toString()).contains("No document provided");
    }

    @Test
    void testGetServiceRegistration() {
        var registration = getProcessor().getServiceRegistration(RegistrationRequest.newBuilder().build())
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat(registration.getModuleName()).isEqualTo("test-processor");
        assertThat(registration.hasJsonConfigSchema()).isTrue();
        assertThat(registration.getJsonConfigSchema()).contains("mode");
        assertThat(registration.getJsonConfigSchema()).contains("simulateError");
        assertThat(registration.getJsonConfigSchema()).contains("requireSchema");
    }

    @Test
    void testProcessDataWithSchemaValidation() {
        // Create a document without required fields
        PipeDoc incompleteDoc = PipeDoc.newBuilder()
                .setId(UUID.randomUUID().toString())
                // Missing title and body
                .build();

        // Enable schema validation
        Struct configStruct = Struct.newBuilder()
                .putFields("mode", Value.newBuilder().setStringValue("validate").build())
                .build();

        ProcessRequest request = ProcessRequest.newBuilder()
                .setDocument(incompleteDoc)
                .setConfig(ProcessConfiguration.newBuilder()
                        .setCustomJsonConfig(configStruct)
                        .build())
                .build();

        var response = getProcessor().processData(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        // Should fail validation
        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getProcessorLogsList()).isNotEmpty();
        assertThat(response.getProcessorLogsList().toString()).contains("Schema validation failed");
        assertThat(response.hasErrorDetails()).isTrue();

        Struct errorDetails = response.getErrorDetails();
        assertThat(errorDetails.getFieldsMap().get("error_type").getStringValue())
                .isEqualTo("IllegalArgumentException");
    }

    @Test
    void testProcessDataWithValidDocument() {
        // Create a complete document that passes validation
        PipeDoc validDoc = PipeDoc.newBuilder()
                .setId(UUID.randomUUID().toString())
                .setTitle("Valid Document")
                .setBody("This document has all required fields")
                .build();

        // Enable schema validation
        Struct configStruct = Struct.newBuilder()
                .putFields("mode", Value.newBuilder().setStringValue("validate").build())
                .build();

        ProcessRequest request = ProcessRequest.newBuilder()
                .setDocument(validDoc)
                .setConfig(ProcessConfiguration.newBuilder()
                        .setCustomJsonConfig(configStruct)
                        .build())
                .build();

        var response = getProcessor().processData(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        // Should pass validation
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getProcessorLogsList().toString()).contains("Schema validation passed");
        assertThat(response.hasOutputDoc()).isTrue();
    }

    @Test
    void testProcessDataWithErrorSimulation() {
        PipeDoc testDoc = PipeDoc.newBuilder()
                .setId(UUID.randomUUID().toString())
                .setTitle("Error Test")
                .setBody("Testing error handling")
                .build();

        // Configure to simulate error
        Struct configStruct = Struct.newBuilder()
                .putFields("simulateError", Value.newBuilder().setBoolValue(true).build())
                .build();

        ProcessRequest request = ProcessRequest.newBuilder()
                .setDocument(testDoc)
                .setConfig(ProcessConfiguration.newBuilder()
                        .setCustomJsonConfig(configStruct)
                        .build())
                .build();

        var response = getProcessor().processData(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getProcessorLogsList()).isNotEmpty();
        assertThat(response.getProcessorLogsList().toString()).contains("Error");
        assertThat(response.hasErrorDetails()).isTrue();

        Struct errorDetails = response.getErrorDetails();
        assertThat(errorDetails.getFieldsMap().get("error_message").getStringValue())
                .contains("Simulated error");
    }
}
