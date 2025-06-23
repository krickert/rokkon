package com.rokkon.pipeline.engine.registration;

import org.junit.jupiter.api.Test;
import org.testcontainers.consul.ConsulContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple test to verify containers can communicate on a shared network
 */
public class SimpleSharedNetworkTest {
    
    @Test
    void testContainersCanCommunicateOnSharedNetwork() throws Exception {
        // Create a shared network
        try (Network network = Network.newNetwork()) {
            System.out.println("Created network: " + network.getId());
            
            // Start Consul on the network
            try (ConsulContainer consul = new ConsulContainer("hashicorp/consul:1.18")
                    .withNetwork(network)
                    .withNetworkAliases("consul")) {
                
                consul.start();
                System.out.println("Consul started on network with alias 'consul'");
                
                // Start test module on the same network
                try (GenericContainer<?> testModule = new GenericContainer<>("rokkon/test-module:1.0.0-SNAPSHOT")
                        .withExposedPorts(9090, 8090)  // Use correct default port 8090
                        .withNetwork(network)
                        .withNetworkAliases("test-module")
                        .withEnv("QUARKUS_HTTP_PORT", "8090")
                        .withEnv("QUARKUS_GRPC_SERVER_PORT", "9090")
                        .withLogConsumer(outputFrame -> System.out.print("[test-module] " + outputFrame.getUtf8String()))
                        .waitingFor(Wait.forHttp("/q/health").forPort(8090))) {
                    
                    testModule.start();
                    System.out.println("Test module started on network with alias 'test-module'");
                    
                    // Register the module with Consul using internal network names
                    String serviceJson = """
                        {
                            "ID": "test-module-network-test",
                            "Name": "test-module",
                            "Tags": ["grpc", "test"],
                            "Address": "test-module",
                            "Port": 9090,
                            "Check": {
                                "Name": "Module Health Check",
                                "GRPC": "test-module:9090",
                                "GRPCUseTLS": false,
                                "Interval": "5s",
                                "Timeout": "2s"
                            }
                        }
                        """;
                    
                    // Register via Consul's external port (for our test client)
                    HttpClient httpClient = HttpClient.newHttpClient();
                    HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + consul.getMappedPort(8500) + "/v1/agent/service/register"))
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString(serviceJson))
                        .build();
                    
                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    assertEquals(200, response.statusCode(), "Service registration should succeed");
                    System.out.println("Service registered in Consul");
                    
                    // Wait a bit for Consul to perform health checks
                    Thread.sleep(10000); // 10 seconds to allow health checks
                    
                    // Check if the service is healthy
                    HttpRequest healthRequest = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + consul.getMappedPort(8500) + "/v1/health/service/test-module?passing=true"))
                        .GET()
                        .build();
                    
                    HttpResponse<String> healthResponse = httpClient.send(healthRequest, HttpResponse.BodyHandlers.ofString());
                    assertEquals(200, healthResponse.statusCode());
                    
                    String body = healthResponse.body();
                    System.out.println("Health check response: " + body);
                    
                    // Verify the service is marked as healthy
                    assertTrue(body.contains("test-module-network-test"), "Service should be in health response");
                    assertFalse(body.equals("[]"), "Should have at least one healthy service");
                    
                    // Also verify we can reach the service via external port
                    ManagedChannel channel = ManagedChannelBuilder
                        .forAddress("localhost", testModule.getMappedPort(9090))
                        .usePlaintext()
                        .build();
                    
                    try {
                        HealthGrpc.HealthBlockingStub healthStub = HealthGrpc.newBlockingStub(channel);
                        HealthCheckResponse grpcResponse = healthStub.check(HealthCheckRequest.newBuilder().build());
                        assertEquals(HealthCheckResponse.ServingStatus.SERVING, grpcResponse.getStatus());
                        System.out.println("Direct gRPC health check passed");
                    } finally {
                        channel.shutdown();
                    }
                }
            }
        }
    }
}