package com.rokkon.echo;

import com.rokkon.search.sdk.PipeStepProcessor;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class EchoServiceTest extends EchoServiceTestBase {

    @GrpcClient
    PipeStepProcessor pipeStepProcessor;

    @Override
    protected PipeStepProcessor getEchoService() {
        return pipeStepProcessor;
    }
}