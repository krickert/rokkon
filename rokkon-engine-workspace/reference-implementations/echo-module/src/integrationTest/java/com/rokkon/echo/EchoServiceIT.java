package com.rokkon.echo;

import com.rokkon.echo.grpc.EchoService;
import com.rokkon.echo.grpc.EchoServiceClient;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

@QuarkusIntegrationTest
public class EchoServiceIT extends EchoServiceTestBase {

    private ManagedChannel channel;
    private EchoService echoService;

    @BeforeEach
    void setup() {
        // For integration tests, the gRPC server runs on the configured port (9090 by default)
        // The application.yml has it set to 9090
        int port = 9090;
        
        channel = ManagedChannelBuilder
                .forAddress("localhost", port)
                .usePlaintext()
                .build();
        
        echoService = new EchoServiceClient("echoService", channel, (name, stub) -> stub);
    }

    @AfterEach
    void cleanup() {
        if (channel != null) {
            channel.shutdown();
        }
    }

    @Override
    protected EchoService getEchoService() {
        return echoService;
    }
}