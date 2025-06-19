package com.rokkon.testmodule;

import com.rokkon.search.model.PipeDoc;
import com.rokkon.search.sdk.*;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.Test;

import static com.rokkon.testmodule.TestProcessorHelper.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the TestProcessorHelper utility class.
 * Demonstrates how to use the helper to test various scenarios.
 */
@QuarkusTest
class TestProcessorHelperTest {
    
    @GrpcClient
    PipeStepProcessor testProcessor;
    
    @Test
    void testHelperWithSimpleRequest() {
        ProcessRequest request = createSimpleRequest();
        
        UniAssertSubscriber<ProcessResponse> subscriber = testProcessor
                .processData(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create());
        
        ProcessResponse response = subscriber.awaitItem().getItem();
        
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.hasOutputDoc()).isTrue();
    }
    
    @Test
    void testHelperWithSchemaValidation() {
        // Test with valid document
        PipeDoc validDoc = createValidDocument();
        ProcessRequest validRequest = createSchemaValidationRequest(validDoc);
        
        UniAssertSubscriber<ProcessResponse> validSubscriber = testProcessor
                .processData(validRequest)
                .subscribe().withSubscriber(UniAssertSubscriber.create());
        
        ProcessResponse validResponse = validSubscriber.awaitItem().getItem();
        
        assertThat(validResponse.getSuccess()).isTrue();
        assertThat(validResponse.getProcessorLogsList())
                .anyMatch(log -> log.contains("Schema validation passed"));
        
        // Test with invalid document (missing title)
        PipeDoc invalidDoc = createDocumentWithoutTitle();
        ProcessRequest invalidRequest = createSchemaValidationRequest(invalidDoc);
        
        UniAssertSubscriber<ProcessResponse> invalidSubscriber = testProcessor
                .processData(invalidRequest)
                .subscribe().withSubscriber(UniAssertSubscriber.create());
        
        ProcessResponse invalidResponse = invalidSubscriber.awaitItem().getItem();
        
        assertThat(invalidResponse.getSuccess()).isFalse();
        assertThat(invalidResponse.hasErrorDetails()).isTrue();
        assertThat(invalidResponse.getErrorDetails().getFieldsMap().get("error_message").getStringValue())
                .contains("Schema validation failed: title is required");
    }
    
    @Test
    void testHelperWithCustomConfiguration() {
        PipeDoc document = documentBuilder()
                .withTitle("Custom Test Document")
                .withBody("Testing custom configuration")
                .withCustomField("version", "1.0.0")
                .withCustomField("processed", false)
                .withCustomField("score", 0.95)
                .build();
        
        ProcessRequest request = requestBuilder()
                .withDocument(document)
                .withPipelineName("custom-pipeline")
                .withStepName("custom-test-step")
                .withStreamId("custom-stream-123")
                .withHopNumber(5)
                .withMode(ProcessingMode.TRANSFORM)
                .withSchemaValidation(false)
                .withAddMetadata(true)
                .build();
        
        UniAssertSubscriber<ProcessResponse> subscriber = testProcessor
                .processData(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create());
        
        ProcessResponse response = subscriber.awaitItem().getItem();
        
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.hasOutputDoc()).isTrue();
        
        // Verify metadata was added
        assertThat(response.getOutputDoc().getCustomData().getFieldsMap())
                .containsKey("processed_by")
                .containsKey("processing_timestamp")
                .containsKey("test_module_version");
    }
    
    @Test
    void testHelperWithErrorSimulation() {
        ProcessRequest errorRequest = createErrorRequest();
        
        UniAssertSubscriber<ProcessResponse> subscriber = testProcessor
                .processData(errorRequest)
                .subscribe().withSubscriber(UniAssertSubscriber.create());
        
        ProcessResponse response = subscriber.awaitItem().getItem();
        
        assertThat(response.getSuccess()).isFalse();
        assertThat(response.hasErrorDetails()).isTrue();
        assertThat(response.getErrorDetails().getFieldsMap().get("error_type").getStringValue())
                .isEqualTo("RuntimeException");
        assertThat(response.getErrorDetails().getFieldsMap().get("error_message").getStringValue())
                .contains("Simulated error for testing");
    }
    
    @Test
    void testComplexScenarioWithMultipleSteps() {
        // Simulate a multi-step pipeline processing
        PipeDoc initialDoc = documentBuilder()
                .withTitle("Multi-Step Document")
                .withBody("This document will go through multiple processing steps")
                .withCustomField("step_count", 0.0)
                .build();
        
        // Step 1: Initial processing
        ProcessRequest step1Request = requestBuilder()
                .withDocument(initialDoc)
                .withStepName("step-1")
                .withHopNumber(1)
                .withMode(ProcessingMode.TEST)
                .build();
        
        ProcessResponse step1Response = testProcessor
                .processData(step1Request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem().getItem();
        
        assertThat(step1Response.getSuccess()).isTrue();
        
        // Step 2: Validation
        ProcessRequest step2Request = requestBuilder()
                .withDocument(step1Response.getOutputDoc())
                .withStepName("step-2-validation")
                .withHopNumber(2)
                .withMode(ProcessingMode.VALIDATE)
                .build();
        
        ProcessResponse step2Response = testProcessor
                .processData(step2Request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem().getItem();
        
        assertThat(step2Response.getSuccess()).isTrue();
        assertThat(step2Response.getProcessorLogsList())
                .anyMatch(log -> log.contains("Schema validation passed"));
        
        // Step 3: Transform
        ProcessRequest step3Request = requestBuilder()
                .withDocument(step2Response.getOutputDoc())
                .withStepName("step-3-transform")
                .withHopNumber(3)
                .withMode(ProcessingMode.TRANSFORM)
                .withAddMetadata(true)
                .build();
        
        ProcessResponse step3Response = testProcessor
                .processData(step3Request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem().getItem();
        
        assertThat(step3Response.getSuccess()).isTrue();
        assertThat(step3Response.getOutputDoc().getCustomData().getFieldsMap())
                .containsKey("processed_by");
    }
}