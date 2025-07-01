package com.rokkon.pipeline.engine.test;

import com.rokkon.search.sdk.PipeStepProcessor;
import com.rokkon.search.sdk.PipeStepProcessorClient;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

@QuarkusIntegrationTest
public class DummyProcessorIT extends DummyProcessorTestBase {

    private ManagedChannel channel;
    private PipeStepProcessor pipeStepProcessor;

    @BeforeEach
    void setup() {
        // For integration tests, we connect to the real running server
        int port = 9000; // Default gRPC port from application.yml
        channel = ManagedChannelBuilder
                .forAddress("localhost", port)
                .usePlaintext()
                .build();
        pipeStepProcessor = new PipeStepProcessorClient("pipeStepProcessor", channel, (name, stub) -> stub);
    }

    @AfterEach
    void cleanup() {
        if (channel != null) {
            channel.shutdown();
        }
    }

    @Override
    protected PipeStepProcessor getDummyProcessor() {
        return pipeStepProcessor;
    }
}