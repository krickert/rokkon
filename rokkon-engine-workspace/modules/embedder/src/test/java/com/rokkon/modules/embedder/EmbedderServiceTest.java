package com.rokkon.modules.embedder;

import com.rokkon.search.sdk.PipeStepProcessor;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class EmbedderServiceTest extends EmbedderServiceTestBase {

    @GrpcClient
    PipeStepProcessor pipeStepProcessor;

    @Override
    protected PipeStepProcessor getEmbedderService() {
        return pipeStepProcessor;
    }
}