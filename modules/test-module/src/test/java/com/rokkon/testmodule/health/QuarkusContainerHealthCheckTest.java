package com.rokkon.testmodule.health;

import com.rokkon.test.containers.TestModuleContainerResource;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Quarkus test that verifies gRPC health check works when the module runs in a container.
 * This uses the Quarkus test resource pattern to manage the container lifecycle properly.
 * 
 * This test demonstrates:
 * 1. How to use @QuarkusTestResource to start containers
 * 2. How to inject container configuration into tests
 * 3. How to test gRPC services running in containers
 */
@QuarkusTest
@QuarkusTestResource(value = TestModuleContainerResource.class, restrictToAnnotatedClass = true)
class QuarkusContainerHealthCheckTest {
    
    @ConfigProperty(name = "test.module.container.grpc.port")
    int containerGrpcPort;
    
    @ConfigProperty(name = "test.module.container.http.port")
    int containerHttpPort;
    
    @ConfigProperty(name = "test.module.container.internal.grpc.port")
    int internalGrpcPort;
    
    @ConfigProperty(name = "test.module.container.name")
    String containerName;
    
    private ManagedChannel channel;
    private HealthGrpc.HealthBlockingStub healthService;
    
    @BeforeEach
    void setup() {
        System.out.println("Connecting to container gRPC port: " + containerGrpcPort);
        
        channel = ManagedChannelBuilder
                .forAddress("localhost", containerGrpcPort)
                .usePlaintext()
                .build();
        healthService = HealthGrpc.newBlockingStub(channel);
    }
    
    @AfterEach
    void cleanup() {
        if (channel != null && !channel.isShutdown()) {
            channel.shutdown();
        }
    }
    
    @Test
    void testContainerGrpcHealthCheck() {
        HealthCheckRequest request = HealthCheckRequest.newBuilder().build();
        HealthCheckResponse response = healthService.check(request);
        
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.ServingStatus.SERVING);
        
        System.out.println("Container health check successful: " + response.getStatus());
    }
    
    @Test
    void testContainerServiceSpecificHealthCheck() {
        HealthCheckRequest request = HealthCheckRequest.newBuilder()
            .setService("com.rokkon.pipeline.proto.PipeStepProcessor")
            .build();
            
        HealthCheckResponse response = healthService.check(request);
        
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.ServingStatus.UNKNOWN);
    }
    
    @Test
    void testContainerHttpHealthEndpoint() {
        // Test HTTP health endpoint using RestAssured
        io.restassured.RestAssured.given()
            .baseUri("http://localhost:" + containerHttpPort)
            .when()
            .get("/q/health")
            .then()
            .statusCode(200)
            .body("status", org.hamcrest.CoreMatchers.is("UP"));
    }
    
    @Test
    void demonstrateConsulRegistrationFromContainer() {
        System.out.println("\n=== Container Registration Info for Consul ===");
        System.out.println("Container Name: " + containerName);
        System.out.println("When registering this container with Consul:");
        System.out.println("  Use internal port: " + internalGrpcPort);
        System.out.println("  Health check: GRPC " + containerName + ":" + internalGrpcPort);
        System.out.println("\nFor external testing (like we're doing now):");
        System.out.println("  Use mapped port: " + containerGrpcPort);
        System.out.println("===========================================\n");
        
        // Verify we can still connect
        assertThat(healthService).isNotNull();
        assertThat(containerGrpcPort).isNotEqualTo(internalGrpcPort);
    }
}