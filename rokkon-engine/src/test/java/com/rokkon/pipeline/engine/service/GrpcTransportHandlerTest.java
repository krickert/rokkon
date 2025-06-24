package com.rokkon.pipeline.engine.service;

import com.rokkon.pipeline.config.model.GrpcTransportConfig;
import com.rokkon.pipeline.config.model.PipelineStepConfig;
import com.rokkon.pipeline.config.model.StepType;
import com.rokkon.pipeline.config.model.TransportType;
import com.rokkon.search.model.PipeDoc;
import com.rokkon.search.sdk.PipeStepProcessor;
import com.rokkon.search.sdk.ProcessRequest;
import com.rokkon.search.sdk.ProcessResponse;
import com.rokkon.search.sdk.ServiceMetadata;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import io.smallrye.stork.api.ServiceInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test for the GrpcTransportHandler class that verifies it can correctly route
 * requests between modules using gRPC.
 */
@ExtendWith(MockitoExtension.class)
public class GrpcTransportHandlerTest {

    @Mock
    private ServiceDiscovery serviceDiscovery;

    @Mock
    private GrpcClientFactory grpcClientFactory;

    @Mock
    private PipeStepProcessor mockProcessor;

    @Mock
    private ServiceInstance mockServiceInstance;

    private GrpcTransportHandler transportHandler;

    @BeforeEach
    void setUp() {
        transportHandler = new GrpcTransportHandler();
        transportHandler.serviceDiscovery = serviceDiscovery;
        transportHandler.grpcClientFactory = grpcClientFactory;
    }

    @Test
    void testCanHandleWithValidConfig() {
        // Given
        PipelineStepConfig stepConfig = createStepConfig("test-service");

        // When/Then
        assertThat(transportHandler.canHandle(stepConfig)).isTrue();
    }

    @Test
    void testCanHandleWithInvalidConfig() {
        // Given
        // Create a step config with a processor info that has an internalProcessorBeanName instead of a grpcServiceName
        PipelineStepConfig stepConfig = new PipelineStepConfig(
                "test-step",
                StepType.PIPELINE,
                "Test step",
                null,
                null,
                Collections.emptyList(),
                Collections.emptyMap(),
                0,
                0L,
                0L,
                0.0,
                null,
                new PipelineStepConfig.ProcessorInfo(null, "internalProcessor") // Using internalProcessorBeanName instead of grpcServiceName
        );

        // When/Then
        assertThat(transportHandler.canHandle(stepConfig)).isFalse();
    }

    @Test
    void testRouteRequestSuccessfully() {
        // Given
        String serviceName = "test-service";
        PipelineStepConfig stepConfig = createStepConfig(serviceName);

        ProcessRequest request = createTestRequest();
        ProcessResponse expectedResponse = createSuccessResponse();

        // Configure mocks
        when(serviceDiscovery.discoverService(eq(serviceName)))
                .thenReturn(Uni.createFrom().item(mockServiceInstance));

        when(mockServiceInstance.getHost()).thenReturn("test-host");
        when(mockServiceInstance.getPort()).thenReturn(9090);

        when(grpcClientFactory.getClient(eq("test-host"), eq(9090)))
                .thenReturn(mockProcessor);

        when(mockProcessor.processData(any(ProcessRequest.class)))
                .thenReturn(Uni.createFrom().item(expectedResponse));

        // When
        ProcessResponse response = transportHandler.routeRequest(request, stepConfig)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getSuccess()).isTrue();

        // Verify the correct service was discovered
        verify(serviceDiscovery).discoverService(eq(serviceName));

        // Verify the client was created with the correct host and port
        verify(grpcClientFactory).getClient(eq("test-host"), eq(9090));

        // Verify the request was sent to the processor
        verify(mockProcessor).processData(any(ProcessRequest.class));
    }

    @Test
    void testRouteRequestWithServiceDiscoveryFailure() {
        // Given
        String serviceName = "test-service";
        PipelineStepConfig stepConfig = createStepConfig(serviceName);
        ProcessRequest request = createTestRequest();

        // Configure service discovery to fail
        RuntimeException discoveryError = new RuntimeException("Service discovery failed");
        when(serviceDiscovery.discoverService(eq(serviceName)))
                .thenReturn(Uni.createFrom().failure(discoveryError));

        // When/Then
        transportHandler.routeRequest(request, stepConfig)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitFailure()
                .assertFailedWith(RuntimeException.class, "Service discovery failed");
    }

    @Test
    void testRouteRequestWithProcessingFailure() {
        // Given
        String serviceName = "test-service";
        PipelineStepConfig stepConfig = createStepConfig(serviceName);
        ProcessRequest request = createTestRequest();

        // Configure mocks for successful service discovery but failed processing
        when(serviceDiscovery.discoverService(eq(serviceName)))
                .thenReturn(Uni.createFrom().item(mockServiceInstance));

        when(mockServiceInstance.getHost()).thenReturn("test-host");
        when(mockServiceInstance.getPort()).thenReturn(9090);

        when(grpcClientFactory.getClient(eq("test-host"), eq(9090)))
                .thenReturn(mockProcessor);

        RuntimeException processingError = new RuntimeException("Processing failed");
        when(mockProcessor.processData(any(ProcessRequest.class)))
                .thenReturn(Uni.createFrom().failure(processingError));

        // When/Then
        transportHandler.routeRequest(request, stepConfig)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitFailure()
                .assertFailedWith(RuntimeException.class, "Processing failed");
    }

    private PipelineStepConfig createStepConfig(String serviceName) {
        return new PipelineStepConfig(
                "test-step",
                StepType.PIPELINE,
                "Test step",
                null,
                null,
                Collections.emptyList(),
                Map.of("default", new PipelineStepConfig.OutputTarget(
                        "next-step",
                        TransportType.GRPC,
                        new GrpcTransportConfig(serviceName, Map.of("timeout", "5000")),
                        null
                )),
                0,
                0L,
                0L,
                0.0,
                null,
                new PipelineStepConfig.ProcessorInfo(serviceName, null)
        );
    }

    private ProcessRequest createTestRequest() {
        PipeDoc testDoc = PipeDoc.newBuilder()
                .setId("test-doc-" + UUID.randomUUID())
                .setTitle("Test Document")
                .setBody("This is a test document")
                .build();

        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setPipelineName("test-pipeline")
                .setPipeStepName("test-step")
                .setStreamId(UUID.randomUUID().toString())
                .setCurrentHopNumber(1)
                .build();

        return ProcessRequest.newBuilder()
                .setDocument(testDoc)
                .setMetadata(metadata)
                .build();
    }

    private ProcessResponse createSuccessResponse() {
        return ProcessResponse.newBuilder()
                .setSuccess(true)
                .addProcessorLogs("Document processed successfully")
                .build();
    }
}
