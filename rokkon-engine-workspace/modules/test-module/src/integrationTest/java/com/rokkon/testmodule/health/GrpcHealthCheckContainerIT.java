package com.rokkon.testmodule.health;

import io.grpc.health.v1.HealthGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/**
 * Container integration test for gRPC health check.
 * When running as @QuarkusIntegrationTest, the service is already running,
 * so we just connect to it directly on port 9090.
 * 
 * This verifies:
 * 1. The service runs on port 9090 (what Consul will use)
 * 2. Health check works in production mode
 * 3. Standard gRPC clients can connect
 */
@QuarkusIntegrationTest
public class GrpcHealthCheckContainerIT extends GrpcHealthCheckTestBase {
    
    private static final int GRPC_PORT = 9090;
    
    private ManagedChannel channel;
    private HealthGrpc.HealthBlockingStub healthService;
    
    @BeforeEach
    void setup() {
        // In integration test mode, the service is already running on port 9090
        System.out.println("Connecting to gRPC service on port: " + GRPC_PORT);
        System.out.println("This is the port that Consul will use for health checks");
        
        // Connect directly to the service
        channel = ManagedChannelBuilder
                .forAddress("localhost", GRPC_PORT)
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