package com.rokkon.pipeline.chunker;

import com.rokkon.search.sdk.PipeStepProcessor;
import com.rokkon.search.sdk.PipeStepProcessorClient;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/**
 * Integration test for creating documents with two different chunking configurations.
 * Extends the base test class and provides the implementation for getting the chunker service.
 */
@QuarkusIntegrationTest
public class DoubleChunkConfigurationIT extends DoubleChunkConfigurationTestBase {

    private static final String OUTPUT_DIR = "target/test-data/double-chunk-output-it";
    
    private ManagedChannel channel;
    private PipeStepProcessor chunkerService;

    @BeforeEach
    void setup() {
        // For integration tests, the gRPC server runs on the configured port (9090 by default)
        int port = 9090;
        
        channel = ManagedChannelBuilder
                .forAddress("localhost", port)
                .usePlaintext()
                .build();
        
        chunkerService = new PipeStepProcessorClient("pipeStepProcessor", channel, (name, stub) -> stub);
    }

    @AfterEach
    void cleanup() {
        if (channel != null) {
            channel.shutdown();
        }
    }

    @Override
    protected PipeStepProcessor getChunkerService() {
        return chunkerService;
    }
    
    @Override
    protected String getOutputDir() {
        return OUTPUT_DIR;
    }
}