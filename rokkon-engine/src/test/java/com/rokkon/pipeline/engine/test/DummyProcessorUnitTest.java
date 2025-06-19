package com.rokkon.pipeline.engine.test;

import com.rokkon.search.sdk.PipeStepProcessor;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class DummyProcessorUnitTest extends DummyProcessorTestBase {

    @GrpcClient
    PipeStepProcessor pipeStepProcessor;

    @Override
    protected PipeStepProcessor getDummyProcessor() {
        return pipeStepProcessor;
    }
}