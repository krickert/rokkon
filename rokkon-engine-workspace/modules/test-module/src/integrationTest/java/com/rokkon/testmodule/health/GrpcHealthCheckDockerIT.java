package com.rokkon.testmodule.health;

import io.grpc.health.v1.HealthGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * True container test that starts a Docker container and tests gRPC health check.
 * This test runs the test-module in a Docker container and verifies:
 * 1. The container starts successfully
 * 2. gRPC health check is accessible from outside the container
 * 3. Port mapping works correctly (internal 9090 -> external random)
 * 
 * This simulates how Consul would connect to the module in production.
 */
@QuarkusIntegrationTest
@Testcontainers
public class GrpcHealthCheckDockerIT {
    
    private static final int INTERNAL_GRPC_PORT = 9090;
    private static final int INTERNAL_HTTP_PORT = 8080;
    
    @Container
    private static final GenericContainer<?> testModuleContainer = new GenericContainer<>("rokkon/test-module:1.0.0-SNAPSHOT")
            .withExposedPorts(INTERNAL_GRPC_PORT, INTERNAL_HTTP_PORT)
            .waitingFor(Wait.forLogMessage(".*Started gRPC server.*", 1))
            .withLogConsumer(outputFrame -> System.out.print("[docker-test-module] " + outputFrame.getUtf8String()));
    
    private static ManagedChannel channel;
    private static HealthGrpc.HealthBlockingStub healthService;
    private static Integer externalGrpcPort;
    private static Integer externalHttpPort;
    
    @BeforeAll
    static void setupContainer() {
        // Get the external mapped ports
        externalGrpcPort = testModuleContainer.getMappedPort(INTERNAL_GRPC_PORT);
        externalHttpPort = testModuleContainer.getMappedPort(INTERNAL_HTTP_PORT);
        
        System.out.println("Docker container started with port mapping:");
        System.out.println("  gRPC: " + INTERNAL_GRPC_PORT + " (internal) -> " + externalGrpcPort + " (external)");
        System.out.println("  HTTP: " + INTERNAL_HTTP_PORT + " (internal) -> " + externalHttpPort + " (external)");
        System.out.println("Container ID: " + testModuleContainer.getContainerId());
        System.out.println("Container Network: " + testModuleContainer.getNetworkMode());
        
        // Create channel to external port
        channel = ManagedChannelBuilder
                .forAddress("localhost", externalGrpcPort)
                .usePlaintext()
                .build();
        healthService = HealthGrpc.newBlockingStub(channel);
    }
    
    @AfterAll
    static void cleanupContainer() {
        if (channel != null && !channel.isShutdown()) {
            channel.shutdown();
        }
    }
    
    @Test
    void testContainerGrpcHealthCheckRespondsOk() {
        // Test default health check (empty service name)
        HealthCheckRequest request = HealthCheckRequest.newBuilder().build();
        
        HealthCheckResponse response = healthService.check(request);
        
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.ServingStatus.SERVING);
        
        System.out.println("Container health check successful: " + response.getStatus());
    }
    
    @Test
    void testContainerGrpcHealthCheckForSpecificService() {
        // Test health check for specific gRPC service
        HealthCheckRequest request = HealthCheckRequest.newBuilder()
            .setService("com.rokkon.pipeline.proto.PipeStepProcessor")
            .build();
            
        HealthCheckResponse response = healthService.check(request);
        
        assertThat(response).isNotNull();
        // Quarkus returns UNKNOWN for specific service queries
        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.ServingStatus.UNKNOWN);
        
        System.out.println("Container service-specific health check: " + response.getStatus());
    }
    
    @Test
    void testContainerHttpHealthEndpoint() {
        // Also test HTTP health endpoint to verify container is fully functional
        io.restassured.RestAssured.given()
            .baseUri("http://localhost:" + externalHttpPort)
            .when()
            .get("/q/health")
            .then()
            .statusCode(200)
            .body("status", org.hamcrest.CoreMatchers.is("UP"));
            
        System.out.println("Container HTTP health check successful on port " + externalHttpPort);
    }
    
    @Test
    void testContainerInternalPortConfiguration() {
        // This test documents the key finding for Consul registration
        System.out.println("\n=== Key Finding for Consul Registration ===");
        System.out.println("When registering with Consul, use:");
        System.out.println("  - Host: <container_name> or <container_ip>");
        System.out.println("  - Port: " + INTERNAL_GRPC_PORT + " (internal container port)");
        System.out.println("  - Health Check: GRPC <host>:" + INTERNAL_GRPC_PORT);
        System.out.println("\nCurrent container is accessible externally at:");
        System.out.println("  - Host: localhost");
        System.out.println("  - Port: " + externalGrpcPort + " (mapped external port)");
        System.out.println("==========================================\n");
        
        // Verify we can still connect
        assertThat(healthService).isNotNull();
        assertThat(externalGrpcPort).isNotEqualTo(INTERNAL_GRPC_PORT);
    }
}