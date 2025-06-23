package com.rokkon.testmodule.health;

import io.grpc.health.v1.HealthGrpc;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Standalone Docker container test for gRPC health check.
 * This test is NOT a QuarkusTest - it runs independently and starts its own container.
 * 
 * This simulates exactly how the module registration service would connect to a
 * module running in Docker/Kubernetes.
 */
@Testcontainers
@Disabled("Docker image not built yet")
class StandaloneGrpcHealthCheckDockerTest {
    
    private static final int INTERNAL_GRPC_PORT = 9090;
    private static final int INTERNAL_HTTP_PORT = 8080;
    
    @Container
    private static final GenericContainer<?> testModuleContainer = new GenericContainer<>("rokkon/test-module:1.0.0-SNAPSHOT")
            .withExposedPorts(INTERNAL_GRPC_PORT, INTERNAL_HTTP_PORT)
            .waitingFor(Wait.forHttp("/q/health").forPort(INTERNAL_HTTP_PORT).forStatusCode(200))
            .withLogConsumer(outputFrame -> System.out.print("[docker] " + outputFrame.getUtf8String()));
    
    private static ManagedChannel channel;
    private static HealthGrpc.HealthBlockingStub healthService;
    private static Integer externalGrpcPort;
    private static Integer externalHttpPort;
    
    @BeforeAll
    static void setupContainer() {
        // Get the external mapped ports
        externalGrpcPort = testModuleContainer.getMappedPort(INTERNAL_GRPC_PORT);
        externalHttpPort = testModuleContainer.getMappedPort(INTERNAL_HTTP_PORT);
        
        System.out.println("\n=== Docker Container Started ===");
        System.out.println("Container ID: " + testModuleContainer.getContainerId());
        System.out.println("Container Name: " + testModuleContainer.getContainerName());
        System.out.println("Port Mapping:");
        System.out.println("  gRPC: " + INTERNAL_GRPC_PORT + " -> " + externalGrpcPort);
        System.out.println("  HTTP: " + INTERNAL_HTTP_PORT + " -> " + externalHttpPort);
        System.out.println("================================\n");
        
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
    void testDockerContainerGrpcHealthCheck() {
        System.out.println("Testing gRPC health check on external port " + externalGrpcPort);
        
        HealthCheckRequest request = HealthCheckRequest.newBuilder().build();
        HealthCheckResponse response = healthService.check(request);
        
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.ServingStatus.SERVING);
        
        System.out.println("✓ Health check response: " + response.getStatus());
    }
    
    @Test
    void testDockerContainerServiceSpecificHealthCheck() {
        System.out.println("Testing service-specific health check");
        
        HealthCheckRequest request = HealthCheckRequest.newBuilder()
            .setService("com.rokkon.pipeline.proto.PipeStepProcessor")
            .build();
            
        HealthCheckResponse response = healthService.check(request);
        
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.ServingStatus.UNKNOWN);
        
        System.out.println("✓ Service-specific health check: " + response.getStatus());
    }
    
    @Test
    void demonstrateConsulRegistrationPattern() {
        System.out.println("\n=== How to Register with Consul ===");
        System.out.println("When this container is running in Docker/K8s:");
        System.out.println("1. Module exposes port: " + INTERNAL_GRPC_PORT);
        System.out.println("2. Consul should register:");
        System.out.println("   {");
        System.out.println("     \"Name\": \"test-module\",");
        System.out.println("     \"Address\": \"<container-hostname>\",");
        System.out.println("     \"Port\": " + INTERNAL_GRPC_PORT + ",");
        System.out.println("     \"Check\": {");
        System.out.println("       \"GRPC\": \"<container-hostname>:" + INTERNAL_GRPC_PORT + "\",");
        System.out.println("       \"Interval\": \"10s\"");
        System.out.println("     }");
        System.out.println("   }");
        System.out.println("\n3. For testing from outside, we use:");
        System.out.println("   - Host: localhost");
        System.out.println("   - Port: " + externalGrpcPort + " (mapped)");
        System.out.println("=====================================\n");
        
        // Verify the pattern works
        assertThat(healthService).isNotNull();
        assertThat(externalGrpcPort).isNotEqualTo(INTERNAL_GRPC_PORT);
    }
    
    @Test
    void testHttpHealthEndpointAlsoWorks() {
        System.out.println("Testing HTTP health endpoint on port " + externalHttpPort);
        
        // Use simple HTTP client to test
        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create("http://localhost:" + externalHttpPort + "/q/health"))
                    .GET()
                    .build();
            
            java.net.http.HttpResponse<String> response = client.send(request, 
                    java.net.http.HttpResponse.BodyHandlers.ofString());
            
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("UP");
            
            System.out.println("✓ HTTP health check successful");
            System.out.println("  Response: " + response.body());
        } catch (Exception e) {
            throw new RuntimeException("HTTP health check failed", e);
        }
    }
}