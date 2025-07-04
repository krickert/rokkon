package com.rokkon.testmodule.health;

import io.grpc.health.v1.HealthGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/**
 * Unit test for gRPC health check using Quarkus dev mode.
 * This verifies health check works during development.
 * Uses standard blocking stub to demonstrate protocol compatibility.
 */
@QuarkusTest
class GrpcHealthCheckTest extends GrpcHealthCheckTestBase {
    
    @ConfigProperty(name = "quarkus.http.test-port", defaultValue = "8081")
    int httpPort;
    
    private ManagedChannel channel;
    private HealthGrpc.HealthBlockingStub healthService;
    
    @BeforeEach
    void setup() {
        // Connect to Quarkus test mode HTTP port (unified server)
        channel = ManagedChannelBuilder
                .forAddress("localhost", httpPort)
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