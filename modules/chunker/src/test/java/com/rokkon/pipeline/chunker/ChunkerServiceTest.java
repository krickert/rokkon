package com.rokkon.pipeline.chunker;

import com.rokkon.search.sdk.PipeStepProcessor;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class ChunkerServiceTest extends ChunkerServiceTestBase {

    @GrpcClient
    PipeStepProcessor pipeStepProcessor;

    @Override
    protected PipeStepProcessor getChunkerService() {
        return pipeStepProcessor;
    }
}