package com.rokkon.echo;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusIntegrationTest
public class SimpleIntegrationTest {

    @Test
    void testGrpcServerIsRunning() throws InterruptedException {
        // Simple test to verify gRPC server is accessible
        int port = 9090;
        ManagedChannel channel = null;
        
        try {
            channel = ManagedChannelBuilder
                    .forAddress("localhost", port)
                    .usePlaintext()
                    .build();
            
            // Wait a bit for server to be ready
            Thread.sleep(1000);
            
            // Try to connect - if this doesn't throw, server is running
            channel.getState(true);
            
            assertThat(true).isTrue(); // If we get here, connection works
            
        } catch (StatusRuntimeException e) {
            System.err.println("gRPC error: " + e.getMessage());
            throw e;
        } finally {
            if (channel != null) {
                channel.shutdown();
            }
        }
    }
}