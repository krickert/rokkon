package com.rokkon.pipeline.chunker;

import com.rokkon.search.sdk.PipeStepProcessor;
import com.rokkon.search.sdk.PipeStepProcessorClient;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/**
 * Integration test for ChunkerService running in prod mode.
 * Uses external gRPC client to connect to the running service.
 */
@QuarkusIntegrationTest
public class ChunkerServiceIT extends ChunkerServiceTestBase {

    private ManagedChannel channel;
    private PipeStepProcessor pipeStepProcessor;

    @BeforeEach
    void setup() {
        // In integration tests, the service runs on the actual configured port
        int port = 9090;
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
    protected PipeStepProcessor getChunkerService() {
        return pipeStepProcessor;
    }
}