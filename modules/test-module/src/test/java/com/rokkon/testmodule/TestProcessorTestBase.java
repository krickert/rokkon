package com.rokkon.testmodule;

import com.google.protobuf.Empty;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.rokkon.search.model.PipeDoc;
import com.rokkon.search.sdk.*;
import com.rokkon.search.sdk.RegistrationRequest;
import com.rokkon.search.sdk.ServiceRegistrationResponse;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.Test;
import org.jboss.logging.Logger;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base test class for TestProcessor service testing.
 * This abstract class can be extended by both unit tests (@QuarkusTest) 
 * and integration tests (@QuarkusIntegrationTest).
 */
public abstract class TestProcessorTestBase {

    private static final Logger LOG = Logger.getLogger(TestProcessorTestBase.class);

    protected abstract PipeStepProcessor getTestProcessor();

    @Test
    void testProcessData() {
        // Create test document
        PipeDoc document = PipeDoc.newBuilder()
                .setId(UUID.randomUUID().toString())
                .setTitle("Test Document")
                .setBody("This is test content for the TestProcessor")
                .setCustomData(Struct.newBuilder()
                        .putFields("source", Value.newBuilder().setStringValue("test").build())
                        .build())
                .build();

        // Create request with metadata
        ProcessRequest request = ProcessRequest.newBuilder()
                .setDocument(document)
                .setMetadata(ServiceMetadata.newBuilder()
                        .setPipelineName("test-pipeline")
                        .setPipeStepName("test-processor")
                        .setStreamId("test-stream-1")
                        .setCurrentHopNumber(1)
                        .build())
                .setConfig(ProcessConfiguration.newBuilder()
                        .putConfigParams("mode", "test")
                        .putConfigParams("addMetadata", "true")
                        .build())
                .build();

        // Process and verify
        LOG.debugf("Sending test request with document: %s", document.getId());

        UniAssertSubscriber<ProcessResponse> subscriber = getTestProcessor()
                .processData(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        ProcessResponse response = subscriber.awaitItem().getItem();

        assertThat(response).isNotNull();
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.hasOutputDoc()).isTrue();
        assertThat(response.getOutputDoc().getId()).isEqualTo(document.getId());

        // Verify custom_data was enhanced
        Struct customData = response.getOutputDoc().getCustomData();
        assertThat(customData.getFieldsMap()).containsKey("processed_by");
        assertThat(customData.getFieldsMap()).containsKey("processing_timestamp");
        assertThat(customData.getFieldsMap()).containsKey("test_module_version");
        assertThat(customData.getFieldsMap()).containsKey("config_mode");
        assertThat(customData.getFieldsMap()).containsKey("config_addMetadata");

        // Verify logs
        assertThat(response.getProcessorLogsList()).isNotEmpty();
        assertThat(response.getProcessorLogsList()).anyMatch(log -> log.contains("TestProcessor: Document processed successfully"));

        LOG.debugf("Test completed successfully, response: %s", response.getSuccess());
    }

    @Test
    void testProcessDataWithoutDocument() {
        ProcessRequest request = ProcessRequest.newBuilder()
                .setMetadata(ServiceMetadata.newBuilder()
                        .setPipelineName("test-pipeline")
                        .setPipeStepName("test-processor")
                        .setStreamId("test-stream-1")
                        .setCurrentHopNumber(1)
                        .build())
                .build();

        LOG.debugf("Sending test request without document");

        UniAssertSubscriber<ProcessResponse> subscriber = getTestProcessor()
                .processData(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        ProcessResponse response = subscriber.awaitItem().getItem();

        assertThat(response).isNotNull();
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.hasOutputDoc()).isFalse();
        assertThat(response.getProcessorLogsList()).anyMatch(log -> log.contains("TestProcessor: No document provided"));

        LOG.debugf("Test without document completed successfully");
    }

    @Test
    void testGetServiceRegistration() {
        LOG.debugf("Testing service registration");

        UniAssertSubscriber<ServiceRegistrationResponse> subscriber = getTestProcessor()
                .getServiceRegistration(RegistrationRequest.newBuilder().build())
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        ServiceRegistrationResponse registration = subscriber.awaitItem().getItem();

        assertThat(registration).isNotNull();
        assertThat(registration.getModuleName()).isNotEmpty();
        assertThat(registration.getJsonConfigSchema()).isNotEmpty();

        // Verify the schema is valid JSON
        assertThat(registration.getJsonConfigSchema()).contains("\"type\": \"object\"");
        assertThat(registration.getJsonConfigSchema()).contains("\"properties\"");
        assertThat(registration.getJsonConfigSchema()).contains("mode");
        assertThat(registration.getJsonConfigSchema()).contains("addMetadata");
        assertThat(registration.getJsonConfigSchema()).contains("simulateError");

        LOG.debugf("Service registration test completed, module name: %s", registration.getModuleName());
    }

    @Test
    void testProcessDataWithDelay() {
        // Create test document
        PipeDoc document = PipeDoc.newBuilder()
                .setId(UUID.randomUUID().toString())
                .setTitle("Delay Test Document")
                .setBody("Testing processing delay")
                .build();

        ProcessRequest request = ProcessRequest.newBuilder()
                .setDocument(document)
                .setMetadata(ServiceMetadata.newBuilder()
                        .setPipelineName("test-pipeline")
                        .setPipeStepName("test-processor-delay")
                        .setStreamId("test-stream-1")
                        .setCurrentHopNumber(1)
                        .build())
                .build();

        LOG.debugf("Sending test request with delay");

        long startTime = System.currentTimeMillis();

        UniAssertSubscriber<ProcessResponse> subscriber = getTestProcessor()
                .processData(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        ProcessResponse response = subscriber.awaitItem().getItem();

        long endTime = System.currentTimeMillis();

        assertThat(response).isNotNull();
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.hasOutputDoc()).isTrue();

        LOG.debugf("Test with delay completed in %d ms", endTime - startTime);
    }

    @Test
    void testSchemaValidationMode() {
        // Test with valid document
        PipeDoc validDocument = PipeDoc.newBuilder()
                .setId(UUID.randomUUID().toString())
                .setTitle("Valid Document")
                .setBody("This document has all required fields")
                .build();

        ProcessRequest request = ProcessRequest.newBuilder()
                .setDocument(validDocument)
                .setMetadata(ServiceMetadata.newBuilder()
                        .setPipelineName("test-pipeline")
                        .setPipeStepName("test-processor")
                        .setStreamId("test-stream-1")
                        .setCurrentHopNumber(1)
                        .build())
                .setConfig(ProcessConfiguration.newBuilder()
                        .setCustomJsonConfig(Struct.newBuilder()
                                .putFields("mode", Value.newBuilder().setStringValue("validate").build())
                                .build())
                        .build())
                .build();

        LOG.debugf("Testing schema validation with valid document");

        UniAssertSubscriber<ProcessResponse> subscriber = getTestProcessor()
                .processData(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        ProcessResponse response = subscriber.awaitItem().getItem();

        assertThat(response).isNotNull();
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getProcessorLogsList()).anyMatch(log -> log.contains("Schema validation passed"));

        LOG.debugf("Schema validation test with valid document passed");
    }

    @Test
    void testSchemaValidationModeWithMissingTitle() {
        // Test with document missing title
        PipeDoc invalidDocument = PipeDoc.newBuilder()
                .setId(UUID.randomUUID().toString())
                .setBody("This document is missing title")
                .build();

        ProcessRequest request = ProcessRequest.newBuilder()
                .setDocument(invalidDocument)
                .setMetadata(ServiceMetadata.newBuilder()
                        .setPipelineName("test-pipeline")
                        .setPipeStepName("test-processor")
                        .setStreamId("test-stream-1")
                        .setCurrentHopNumber(1)
                        .build())
                .setConfig(ProcessConfiguration.newBuilder()
                        .setCustomJsonConfig(Struct.newBuilder()
                                .putFields("mode", Value.newBuilder().setStringValue("validate").build())
                                .build())
                        .build())
                .build();

        LOG.debugf("Testing schema validation with missing title");

        UniAssertSubscriber<ProcessResponse> subscriber = getTestProcessor()
                .processData(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        ProcessResponse response = subscriber.awaitItem().getItem();

        assertThat(response).isNotNull();
        assertThat(response.getSuccess()).isFalse();
        assertThat(response.hasErrorDetails()).isTrue();
        assertThat(response.getErrorDetails().getFieldsMap().get("error_message").getStringValue())
                .contains("Schema validation failed: title is required");

        LOG.debugf("Schema validation test with missing title correctly failed");
    }

    @Test
    void testSchemaValidationWithRequireSchemaFlag() {
        // Test requireSchema flag overrides mode
        PipeDoc validDocument = PipeDoc.newBuilder()
                .setId(UUID.randomUUID().toString())
                .setTitle("Valid Document")
                .setBody("Testing requireSchema flag")
                .build();

        ProcessRequest request = ProcessRequest.newBuilder()
                .setDocument(validDocument)
                .setMetadata(ServiceMetadata.newBuilder()
                        .setPipelineName("test-pipeline")
                        .setPipeStepName("test-processor")
                        .setStreamId("test-stream-1")
                        .setCurrentHopNumber(1)
                        .build())
                .setConfig(ProcessConfiguration.newBuilder()
                        .setCustomJsonConfig(Struct.newBuilder()
                                .putFields("mode", Value.newBuilder().setStringValue("test").build())
                                .putFields("requireSchema", Value.newBuilder().setBoolValue(true).build())
                                .build())
                        .build())
                .build();

        LOG.debugf("Testing requireSchema flag");

        UniAssertSubscriber<ProcessResponse> subscriber = getTestProcessor()
                .processData(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        ProcessResponse response = subscriber.awaitItem().getItem();

        assertThat(response).isNotNull();
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getProcessorLogsList()).anyMatch(log -> log.contains("Schema validation passed"));

        LOG.debugf("RequireSchema flag test passed");
    }

    @Test
    void testSimulateError() {
        PipeDoc document = PipeDoc.newBuilder()
                .setId(UUID.randomUUID().toString())
                .setTitle("Error Test Document")
                .setBody("Testing error simulation")
                .build();

        ProcessRequest request = ProcessRequest.newBuilder()
                .setDocument(document)
                .setMetadata(ServiceMetadata.newBuilder()
                        .setPipelineName("test-pipeline")
                        .setPipeStepName("test-processor")
                        .setStreamId("test-stream-1")
                        .setCurrentHopNumber(1)
                        .build())
                .setConfig(ProcessConfiguration.newBuilder()
                        .setCustomJsonConfig(Struct.newBuilder()
                                .putFields("simulateError", Value.newBuilder().setBoolValue(true).build())
                                .build())
                        .build())
                .build();

        LOG.debugf("Testing error simulation");

        UniAssertSubscriber<ProcessResponse> subscriber = getTestProcessor()
                .processData(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        ProcessResponse response = subscriber.awaitItem().getItem();

        assertThat(response).isNotNull();
        assertThat(response.getSuccess()).isFalse();
        assertThat(response.hasErrorDetails()).isTrue();
        assertThat(response.getErrorDetails().getFieldsMap().get("error_message").getStringValue())
                .contains("Simulated error for testing");

        LOG.debugf("Error simulation test passed");
    }
}
