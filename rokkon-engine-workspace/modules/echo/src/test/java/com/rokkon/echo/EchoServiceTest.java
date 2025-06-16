package com.rokkon.echo;

import com.rokkon.echo.grpc.EchoService;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class EchoServiceTest extends EchoServiceTestBase {

    @GrpcClient
    EchoService echoService;

    @Override
    protected EchoService getEchoService() {
        return echoService;
    }
}