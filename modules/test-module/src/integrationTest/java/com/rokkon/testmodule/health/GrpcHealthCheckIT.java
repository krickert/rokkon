package com.rokkon.testmodule.health;

import io.grpc.health.v1.HealthGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/**
 * Integration test for gRPC health check using packaged JAR.
 * This verifies health check works in production mode.
 * Uses standard blocking stub to demonstrate protocol compatibility.
 */
@QuarkusIntegrationTest
public class GrpcHealthCheckIT extends GrpcHealthCheckTestBase {
    
    private ManagedChannel channel;
    private HealthGrpc.HealthBlockingStub healthService;
    
    @BeforeEach
    void setup() {
        // Connect to the JAR's gRPC port
        int port = 49095;
        channel = ManagedChannelBuilder
                .forAddress("localhost", port)
                .usePlaintext()
                .build();
        healthService = HealthGrpc.newBlockingStub(channel);
    }
    
    @AfterEach
    void cleanup() {
        if (channel != null) {
            channel.shutdown();
        }
    }
    
    @Override
    protected HealthGrpc.HealthBlockingStub getHealthService() {
        return healthService;
    }
}