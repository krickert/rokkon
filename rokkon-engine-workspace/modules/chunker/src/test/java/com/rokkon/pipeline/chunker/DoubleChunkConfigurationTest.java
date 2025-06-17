package com.rokkon.pipeline.chunker;

import com.rokkon.search.sdk.PipeStepProcessor;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * Unit test for creating documents with two different chunking configurations.
 * Extends the base test class and provides the implementation for getting the chunker service.
 */
@QuarkusTest
public class DoubleChunkConfigurationTest extends DoubleChunkConfigurationTestBase {

    private static final String OUTPUT_DIR = "target/test-data/double-chunk-output";

    @Inject
    @GrpcClient
    PipeStepProcessor chunkerService;

    @Override
    protected PipeStepProcessor getChunkerService() {
        return chunkerService;
    }
    
    @Override
    protected String getOutputDir() {
        return OUTPUT_DIR;
    }
}