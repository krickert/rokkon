package com.rokkon.modules.embedder;

import com.rokkon.search.sdk.PipeStepProcessor;
import com.rokkon.search.sdk.PipeStepProcessorClient;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

@QuarkusIntegrationTest
public class EmbedderServiceIT extends EmbedderServiceTestBase {

    private ManagedChannel channel;
    private PipeStepProcessor pipeStepProcessor;

    @BeforeEach
    void setup() {
        // For integration tests, the gRPC server runs on the configured port (49091 by default)
        int port = 49091;
        
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
    protected PipeStepProcessor getEmbedderService() {
        return pipeStepProcessor;
    }
}