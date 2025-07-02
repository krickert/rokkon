package com.rokkon.pipeline.engine;

import com.rokkon.search.engine.PipeStreamEngine;
import com.rokkon.search.model.PipeStream;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 1: Test that engine service starts up and can receive gRPC calls.
 */
@QuarkusIntegrationTest
public class EngineStartupIT {

    @GrpcClient
    PipeStreamEngine engineClient;

    @Test
    void testEngineServiceStartsAndRespondsToGrpc() {
        // Given - a simple test request
        PipeStream testRequest = PipeStream.newBuilder()
                .setStreamId("test-stream-1")
                .setCurrentPipelineName("test-pipeline")
                .build();

        // When - we call the test endpoint
        PipeStream response = engineClient.testPipeStream(testRequest)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        // Then - we get a response with test metadata added
        assertThat(response).isNotNull();
        assertThat(response.getStreamId()).isEqualTo("test-stream-1");
        assertThat(response.getContextParamsMap()).containsKey("test_processed");
        assertThat(response.getContextParamsMap().get("test_processed")).isEqualTo("true");
    }
}