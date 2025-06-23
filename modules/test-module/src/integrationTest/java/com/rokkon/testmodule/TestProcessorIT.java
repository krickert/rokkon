package com.rokkon.testmodule;

import com.rokkon.search.sdk.PipeStepProcessor;
import com.rokkon.search.sdk.PipeStepProcessorClient;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/**
 * Integration test for TestProcessor.
 */
@QuarkusIntegrationTest
public class TestProcessorIT extends TestProcessorTestBase {

    private ManagedChannel channel;
    private PipeStepProcessor pipeStepProcessor;

    @BeforeEach
    void setup() {
        int port = 49095; // Standardized port for test-module
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
    protected PipeStepProcessor getTestProcessor() {
        return pipeStepProcessor;
    }
}