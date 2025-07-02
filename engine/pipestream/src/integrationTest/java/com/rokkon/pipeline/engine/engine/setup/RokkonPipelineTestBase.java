package com.rokkon.pipeline.engine.setup;

import com.google.protobuf.Empty;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.rokkon.search.model.PipeDoc;
import com.rokkon.search.sdk.PipeStepProcessor;
import com.rokkon.search.sdk.ProcessRequest;
import com.rokkon.search.sdk.ProcessResponse;
import com.rokkon.search.sdk.RegistrationRequest;
import com.rokkon.search.sdk.ServiceRegistrationResponse;
import com.rokkon.search.sdk.ServiceMetadata;
import com.rokkon.search.sdk.ProcessConfiguration;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.Test;
import org.jboss.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base test class for pipeline processing testing.
 * This abstract class can be extended by both unit tests (@QuarkusTest) 
 * and integration tests (@QuarkusIntegrationTest).
 */
public abstract class RokkonPipelineTestBase {

    private static final Logger LOG = Logger.getLogger(RokkonPipelineTestBase.class);

    protected abstract PipeStepProcessor getProcessor();

    @Test
    void testProcessDataWithDocument() {
        LOG.info("Testing process data with document");

        // Create test document
        PipeDoc testDoc = PipeDoc.newBuilder()
                .setId("test-doc-123")
                .setTitle("Test Document")
                .setBody("This is a test document for processing")
                .build();

        ProcessRequest request = ProcessRequest.newBuilder()
                .setDocument(testDoc)
                .build();

        // Test with UniAssertSubscriber
        getProcessor().processData(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .assertCompleted()
                .getItem();

        // Test with await
        ProcessResponse response = getProcessor()
                .processData(request)
                .await().indefinitely();

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getProcessorLogsList()).isNotEmpty();

        // Check that document was processed
        if (response.hasOutputDoc()) {
            assertThat(response.getOutputDoc().getId()).isEqualTo("test-doc-123");
            assertThat(response.getOutputDoc().hasCustomData()).isTrue();

            // Verify processor added metadata
            Struct customData = response.getOutputDoc().getCustomData();
            assertThat(customData.containsFields("processed_by")).isTrue();
            assertThat(customData.containsFields("processing_timestamp")).isTrue();
        }
    }

    @Test
    void testProcessDataWithoutDocument() {
        LOG.info("Testing process data without document");

        ProcessRequest request = ProcessRequest.newBuilder().build();

        ProcessResponse response = getProcessor()
                .processData(request)
                .await().indefinitely();

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getProcessorLogsList()).isNotEmpty();
    }

    @Test
    void testProcessDataWithMetadata() {
        LOG.info("Testing process data with metadata");

        PipeDoc testDoc = PipeDoc.newBuilder()
                .setId("test-doc-metadata")
                .setTitle("Metadata Test Document")
                .setBody("Testing with metadata")
                .build();

        ProcessRequest request = ProcessRequest.newBuilder()
                .setDocument(testDoc)
                .setMetadata(ServiceMetadata.newBuilder()
                        .setPipelineName("test-pipeline")
                        .setPipeStepName("test-step")
                        .setCurrentHopNumber(1)
                        .build())
                .build();

        ProcessResponse response = getProcessor()
                .processData(request)
                .await().indefinitely();

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.hasOutputDoc()).isTrue();
    }

    @Test
    void testProcessDataWithConfig() {
        LOG.info("Testing process data with configuration");

        PipeDoc testDoc = PipeDoc.newBuilder()
                .setId("test-doc-config")
                .setTitle("Config Test Document")
                .setBody("Testing with configuration")
                .build();

        // Create configuration
        Struct configStruct = Struct.newBuilder()
                .putFields("mode", Value.newBuilder().setStringValue("test").build())
                .putFields("addMetadata", Value.newBuilder().setBoolValue(true).build())
                .build();

        ProcessRequest request = ProcessRequest.newBuilder()
                .setDocument(testDoc)
                .setConfig(ProcessConfiguration.newBuilder()
                        .setCustomJsonConfig(configStruct)
                        .putConfigParams("test_param", "test_value")
                        .build())
                .build();

        ProcessResponse response = getProcessor()
                .processData(request)
                .await().indefinitely();

        assertThat(response.getSuccess()).isTrue();

        if (response.hasOutputDoc() && response.getOutputDoc().hasCustomData()) {
            Struct customData = response.getOutputDoc().getCustomData();
            // Check if config params were applied
            assertThat(customData.containsFields("config_test_param")).isTrue();
            assertThat(customData.getFieldsMap().get("config_test_param").getStringValue())
                    .isEqualTo("test_value");
        }
    }

    @Test
    void testGetServiceRegistration() {
        LOG.info("Testing service registration");

        ServiceRegistrationResponse registration = getProcessor()
                .getServiceRegistration(RegistrationRequest.newBuilder().build())
                .await().indefinitely();

        assertThat(registration.getModuleName()).isNotEmpty();
        assertThat(registration.getJsonConfigSchema()).isNotEmpty();

        // Verify schema is valid JSON
        assertThat(registration.getJsonConfigSchema()).contains("type");
        assertThat(registration.getJsonConfigSchema()).contains("properties");
    }

    @Test
    void testProcessDataWithSchemaValidation() {
        LOG.info("Testing process data with schema validation mode");

        // Create document without required fields for schema validation
        PipeDoc incompleteDoc = PipeDoc.newBuilder()
                .setId("test-doc-validation")
                // Missing title and body - should fail validation if mode is "validate"
                .build();

        // Configure for validation mode
        Struct configStruct = Struct.newBuilder()
                .putFields("mode", Value.newBuilder().setStringValue("validate").build())
                .putFields("requireSchema", Value.newBuilder().setBoolValue(true).build())
                .build();

        ProcessRequest request = ProcessRequest.newBuilder()
                .setDocument(incompleteDoc)
                .setConfig(ProcessConfiguration.newBuilder()
                        .setCustomJsonConfig(configStruct)
                        .build())
                .build();

        ProcessResponse response = getProcessor()
                .processData(request)
                .await().indefinitely();

        // With schema validation, this should fail
        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getProcessorLogsList()).isNotEmpty();
        assertThat(response.getProcessorLogsList().toString())
                .contains("Schema validation failed");
    }

    @Test
    void testProcessDataWithErrorSimulation() {
        LOG.info("Testing process data with error simulation");

        PipeDoc testDoc = PipeDoc.newBuilder()
                .setId("test-doc-error")
                .setTitle("Error Test Document")
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

        ProcessResponse response = getProcessor()
                .processData(request)
                .await().indefinitely();

        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getProcessorLogsList()).isNotEmpty();
        assertThat(response.hasErrorDetails()).isTrue();

        Struct errorDetails = response.getErrorDetails();
        assertThat(errorDetails.containsFields("error_type")).isTrue();
        assertThat(errorDetails.containsFields("error_message")).isTrue();
        assertThat(errorDetails.getFieldsMap().get("error_message").getStringValue())
                .contains("Simulated error");
    }
}
