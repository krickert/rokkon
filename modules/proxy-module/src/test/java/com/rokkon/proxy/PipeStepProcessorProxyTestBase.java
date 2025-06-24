package com.rokkon.proxy;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.rokkon.search.model.PipeDoc;
import com.rokkon.search.sdk.*;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Base test class for PipeStepProcessorProxy testing.
 * This abstract class can be extended by both unit tests and integration tests.
 * 
 * The class provides a comprehensive set of tests for the PipeStepProcessor interface:
 * 
 * - testProcessData: Tests the basic processing functionality with a valid document
 * - testTestProcessData: Tests the test processing functionality
 * - testGetServiceRegistration: Tests retrieving service registration information
 * - testProcessDataError: Tests handling of backend exceptions during processing
 * - testBackendFailureResponse: Tests handling of failure responses from the backend
 * - testGetServiceRegistrationError: Tests handling of backend exceptions during registration
 * 
 * Each test mocks the backend client's behavior and verifies that the proxy correctly
 * handles the response or error. This approach allows testing the proxy's error handling
 * and response processing without requiring a real backend service.
 */
public abstract class PipeStepProcessorProxyTestBase {

    protected abstract PipeStepProcessor getProxyProcessor();
    protected abstract MutinyPipeStepProcessorGrpc.MutinyPipeStepProcessorStub getMockedBackendClient();

    // No need for a setup method in the base class
    // Each subclass should handle its own setup

    @Test
    void testProcessData() {
        // Create test document
        PipeDoc document = PipeDoc.newBuilder()
                .setId(UUID.randomUUID().toString())
                .setTitle("Test Document")
                .setBody("This is test content for the Proxy")
                .setCustomData(Struct.newBuilder()
                        .putFields("source", Value.newBuilder().setStringValue("test").build())
                        .build())
                .build();

        // Create request with metadata
        ProcessRequest request = ProcessRequest.newBuilder()
                .setDocument(document)
                .setMetadata(ServiceMetadata.newBuilder()
                        .setPipelineName("test-pipeline")
                        .setPipeStepName("proxy-processor")
                        .setStreamId("test-stream-1")
                        .setCurrentHopNumber(1)
                        .build())
                .setConfig(ProcessConfiguration.newBuilder()
                        .putConfigParams("mode", "test")
                        .build())
                .build();

        // Mock the backend response
        ProcessResponse backendResponse = ProcessResponse.newBuilder()
                .setSuccess(true)
                .setOutputDoc(document)
                .addProcessorLogs("Backend: Document processed successfully")
                .build();

        when(getMockedBackendClient().processData(any(ProcessRequest.class)))
                .thenReturn(Uni.createFrom().item(backendResponse));

        // Process and verify
        UniAssertSubscriber<ProcessResponse> subscriber = getProxyProcessor()
                .processData(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        ProcessResponse response = subscriber.awaitItem().getItem();

        assertThat(response).isNotNull();
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.hasOutputDoc()).isTrue();
        assertThat(response.getOutputDoc().getId()).isEqualTo(document.getId());
        assertThat(response.getProcessorLogsList()).anyMatch(log -> log.contains("Backend: Document processed successfully"));
    }

    @Test
    void testTestProcessData() {
        // Create test document
        PipeDoc document = PipeDoc.newBuilder()
                .setId(UUID.randomUUID().toString())
                .setTitle("Test Document")
                .setBody("This is test content for the Proxy")
                .build();

        // Create request
        ProcessRequest request = ProcessRequest.newBuilder()
                .setDocument(document)
                .build();

        // Mock the backend response
        ProcessResponse backendResponse = ProcessResponse.newBuilder()
                .setSuccess(true)
                .setOutputDoc(document)
                .addProcessorLogs("Backend: Test processing completed")
                .build();

        when(getMockedBackendClient().testProcessData(any(ProcessRequest.class)))
                .thenReturn(Uni.createFrom().item(backendResponse));

        // Process and verify
        UniAssertSubscriber<ProcessResponse> subscriber = getProxyProcessor()
                .testProcessData(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        ProcessResponse response = subscriber.awaitItem().getItem();

        assertThat(response).isNotNull();
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.hasOutputDoc()).isTrue();
        assertThat(response.getOutputDoc().getId()).isEqualTo(document.getId());
        assertThat(response.getProcessorLogsList()).anyMatch(log -> log.contains("Backend: Test processing completed"));
    }

    @Test
    void testGetServiceRegistration() {
        // Create request
        RegistrationRequest request = RegistrationRequest.newBuilder().build();

        // Mock the backend response
        ServiceRegistrationResponse backendResponse = ServiceRegistrationResponse.newBuilder()
                .setModuleName("test-module")
                .setVersion("1.0.0")
                .setHealthCheckPassed(true)
                .setHealthCheckMessage("Module is healthy")
                .setJsonConfigSchema("{\"type\":\"object\",\"properties\":{\"test\":true}}")
                .build();

        when(getMockedBackendClient().getServiceRegistration(any(RegistrationRequest.class)))
                .thenReturn(Uni.createFrom().item(backendResponse));

        // Process and verify
        UniAssertSubscriber<ServiceRegistrationResponse> subscriber = getProxyProcessor()
                .getServiceRegistration(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        ServiceRegistrationResponse response = subscriber.awaitItem().getItem();

        assertThat(response).isNotNull();
        assertThat(response.getModuleName()).isEqualTo("test-module");
        assertThat(response.getVersion()).isEqualTo("1.0.0");
        assertThat(response.getHealthCheckPassed()).isTrue();
        assertThat(response.getMetadataMap()).containsKey("proxy_enabled");
        assertThat(response.getMetadataMap().get("proxy_enabled")).isEqualTo("true");
        assertThat(response.getMetadataMap()).containsKey("proxy_version");
    }

    @Test
    void testProcessDataError() {
        // Create test document
        PipeDoc document = PipeDoc.newBuilder()
                .setId(UUID.randomUUID().toString())
                .setTitle("Error Test Document")
                .build();

        // Create request
        ProcessRequest request = ProcessRequest.newBuilder()
                .setDocument(document)
                .build();

        // Mock the backend to throw an exception
        when(getMockedBackendClient().processData(any(ProcessRequest.class)))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("Simulated backend error")));

        // Process and verify
        UniAssertSubscriber<ProcessResponse> subscriber = getProxyProcessor()
                .processData(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        ProcessResponse response = subscriber.awaitItem().getItem();

        assertThat(response).isNotNull();
        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getProcessorLogsList()).anyMatch(log -> log.contains("Proxy error: Simulated backend error"));
    }

    @Test
    void testBackendFailureResponse() {
        // Create test document
        PipeDoc document = PipeDoc.newBuilder()
                .setId(UUID.randomUUID().toString())
                .setTitle("Failure Test Document")
                .build();

        // Create request
        ProcessRequest request = ProcessRequest.newBuilder()
                .setDocument(document)
                .build();

        // Mock the backend to return a failure response
        ProcessResponse backendResponse = ProcessResponse.newBuilder()
                .setSuccess(false)
                .addProcessorLogs("Backend: Processing failed")
                .build();

        when(getMockedBackendClient().processData(any(ProcessRequest.class)))
                .thenReturn(Uni.createFrom().item(backendResponse));

        // Process and verify
        UniAssertSubscriber<ProcessResponse> subscriber = getProxyProcessor()
                .processData(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        ProcessResponse response = subscriber.awaitItem().getItem();

        assertThat(response).isNotNull();
        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getProcessorLogsList()).anyMatch(log -> log.contains("Backend: Processing failed"));
    }

    @Test
    void testGetServiceRegistrationError() {
        // Create request
        RegistrationRequest request = RegistrationRequest.newBuilder().build();

        // Mock the backend to throw an exception
        when(getMockedBackendClient().getServiceRegistration(any(RegistrationRequest.class)))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("Simulated registration error")));

        // Process and verify
        UniAssertSubscriber<ServiceRegistrationResponse> subscriber = getProxyProcessor()
                .getServiceRegistration(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        ServiceRegistrationResponse response = subscriber.awaitItem().getItem();

        assertThat(response).isNotNull();
        assertThat(response.getModuleName()).isEqualTo("proxy-module");
        assertThat(response.getHealthCheckPassed()).isFalse();
        assertThat(response.getHealthCheckMessage()).contains("Failed to connect to backend module");
        assertThat(response.getMetadataMap()).containsKey("proxy_enabled");
        assertThat(response.getMetadataMap()).containsKey("error");
        assertThat(response.getMetadataMap().get("error")).contains("Simulated registration error");
    }
}
