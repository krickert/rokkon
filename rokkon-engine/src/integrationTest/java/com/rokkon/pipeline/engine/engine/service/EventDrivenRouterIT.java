package com.rokkon.pipeline.engine.service;

import com.rokkon.pipeline.config.model.GrpcTransportConfig;
import com.rokkon.pipeline.config.model.KafkaTransportConfig;
import com.rokkon.pipeline.config.model.PipelineStepConfig;
import com.rokkon.pipeline.config.model.StepType;
import com.rokkon.pipeline.config.model.TransportType;
import com.rokkon.search.model.PipeDoc;
import com.rokkon.search.model.PipeStream;
import com.rokkon.search.sdk.ProcessRequest;
import com.rokkon.search.sdk.ProcessResponse;
import com.rokkon.search.sdk.ServiceMetadata;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import io.vertx.core.eventbus.EventBus;
import jakarta.enterprise.event.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test for the EventDrivenRouter that verifies it can correctly route
 * requests between modules using different transport handlers.
 */
@ExtendWith(MockitoExtension.class)
public class EventDrivenRouterTest {

    @Mock
    private GrpcTransportHandler grpcHandler;

    @Mock
    private KafkaTransportHandler kafkaHandler;

    @Mock
    private EventBus eventBus;

    @Mock
    private Event<EventDrivenRouterImpl.RoutingEvent> routingEvent;

    private EventDrivenRouterImpl router;

    @BeforeEach
    void setUp() {
        router = new EventDrivenRouterImpl();
        router.eventBus = eventBus;
        router.routingEvent = routingEvent;
        router.grpcHandler = grpcHandler;
        router.kafkaHandler = kafkaHandler;
        router.init(); // Register the handlers
    }

    @Test
    void testRouteRequestUsingGrpcTransport() {
        // Given
        String serviceName = "test-service";
        PipelineStepConfig stepConfig = createGrpcStepConfig(serviceName);
        ProcessRequest request = createTestRequest();
        ProcessResponse expectedResponse = createSuccessResponse();

        // Configure mock
        when(grpcHandler.canHandle(eq(stepConfig))).thenReturn(true);
        when(grpcHandler.routeRequest(eq(request), eq(stepConfig)))
                .thenReturn(Uni.createFrom().item(expectedResponse));

        // When
        ProcessResponse response = router.routeRequest(request, stepConfig)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getSuccess()).isTrue();

        // Verify the gRPC handler was used
        verify(grpcHandler).routeRequest(eq(request), eq(stepConfig));
    }

    @Test
    void testRouteStreamToMultipleDestinations() {
        // Given
        PipelineStepConfig stepConfig = createStepConfigWithMultipleOutputs();
        PipeStream stream = createTestStream();

        // Configure mocks
        when(grpcHandler.routeStream(eq(stream), eq("grpc-step"), eq(stepConfig)))
                .thenReturn(Uni.createFrom().voidItem());

        when(kafkaHandler.routeStream(eq(stream), eq("kafka-step"), eq(stepConfig)))
                .thenReturn(Uni.createFrom().voidItem());

        // When
        List<EventDrivenRouter.RoutingResult> results = router.routeStream(stream, stepConfig)
                .collect().asList()
                .await().indefinitely();

        // Then
        assertThat(results).hasSize(2);

        // Verify both handlers were called
        verify(grpcHandler).routeStream(eq(stream), eq("grpc-step"), eq(stepConfig));
        verify(kafkaHandler).routeStream(eq(stream), eq("kafka-step"), eq(stepConfig));

        // Verify all results were successful
        assertThat(results).allMatch(result -> result.success());
    }

    @Test
    void testRouteStreamWithPartialFailure() {
        // Given
        PipelineStepConfig stepConfig = createStepConfigWithMultipleOutputs();
        PipeStream stream = createTestStream();
        RuntimeException kafkaError = new RuntimeException("Kafka routing failed");

        // Configure mocks - gRPC succeeds, Kafka fails
        when(grpcHandler.routeStream(eq(stream), eq("grpc-step"), eq(stepConfig)))
                .thenReturn(Uni.createFrom().voidItem());

        when(kafkaHandler.routeStream(eq(stream), eq("kafka-step"), eq(stepConfig)))
                .thenReturn(Uni.createFrom().failure(kafkaError));

        // When
        List<EventDrivenRouter.RoutingResult> results = router.routeStream(stream, stepConfig)
                .collect().asList()
                .await().indefinitely();

        // Then
        assertThat(results).hasSize(2);

        // Verify both handlers were called
        verify(grpcHandler).routeStream(eq(stream), eq("grpc-step"), eq(stepConfig));
        verify(kafkaHandler).routeStream(eq(stream), eq("kafka-step"), eq(stepConfig));

        // Verify one success and one failure
        assertThat(results).filteredOn(result -> result.success()).hasSize(1);
        assertThat(results).filteredOn(result -> !result.success()).hasSize(1);

        // Verify the failure details
        EventDrivenRouter.RoutingResult failureResult = results.stream()
                .filter(result -> !result.success())
                .findFirst()
                .orElseThrow();

        assertThat(failureResult.targetStepName()).isEqualTo("kafka-step");
        assertThat(failureResult.transportType()).isEqualTo(TransportType.KAFKA);
        assertThat(failureResult.error()).isEqualTo(kafkaError);
    }

    @Test
    void testRouteRequestWithNoHandlerAvailable() {
        // Given
        PipelineStepConfig stepConfig = createGrpcStepConfig("test-service");
        ProcessRequest request = createTestRequest();

        // Configure mock to indicate it can't handle the request
        when(grpcHandler.canHandle(eq(stepConfig))).thenReturn(false);

        // When/Then
        router.routeRequest(request, stepConfig)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitFailure()
                .assertFailedWith(IllegalStateException.class, "No handler available for step: test-step");
    }

    private PipelineStepConfig createGrpcStepConfig(String serviceName) {
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

    private PipelineStepConfig createStepConfigWithMultipleOutputs() {
        return new PipelineStepConfig(
                "multi-output-step",
                StepType.PIPELINE,
                "Step with multiple outputs",
                null,
                null,
                Collections.emptyList(),
                Map.of(
                        "grpc-output", new PipelineStepConfig.OutputTarget(
                                "grpc-step",
                                TransportType.GRPC,
                                new GrpcTransportConfig("grpc-service", Map.of("timeout", "5000")),
                                null
                        ),
                        "kafka-output", new PipelineStepConfig.OutputTarget(
                                "kafka-step",
                                TransportType.KAFKA,
                                null,
                                new KafkaTransportConfig(
                                        "test-topic",
                                        "id",
                                        "none",
                                        16384,
                                        100,
                                        Map.of("acks", "all")
                                )
                        )
                ),
                0,
                0L,
                0L,
                0.0,
                null,
                new PipelineStepConfig.ProcessorInfo("test-service", null)
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

    private PipeStream createTestStream() {
        return PipeStream.newBuilder()
                .setStreamId("test-stream-" + UUID.randomUUID())
                .setCurrentPipelineName("test-pipeline")
                // Add the current step name as a context parameter since there's no direct setter
                .putContextParams("current_step", "multi-output-step")
                .build();
    }

    private ProcessResponse createSuccessResponse() {
        return ProcessResponse.newBuilder()
                .setSuccess(true)
                .addProcessorLogs("Document processed successfully")
                .build();
    }
}
