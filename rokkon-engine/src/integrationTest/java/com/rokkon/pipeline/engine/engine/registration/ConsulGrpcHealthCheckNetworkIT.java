package com.rokkon.pipeline.engine.registration;

import org.junit.jupiter.api.Test;
import org.testcontainers.consul.ConsulContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import com.rokkon.search.sdk.MutinyPipeStepProcessorGrpc;
import com.rokkon.search.sdk.PipeStepProcessor;
import com.rokkon.search.sdk.ServiceRegistrationResponse;
import com.rokkon.search.sdk.RegistrationRequest;
import com.rokkon.search.sdk.ProcessRequest;
import com.rokkon.search.sdk.ProcessResponse;
import com.rokkon.search.model.PipeDoc;
import com.google.protobuf.Empty;
import io.smallrye.mutiny.Uni;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test that demonstrates Consul can perform gRPC health checks on containers
 * sharing a Docker network. This is the definitive test that proves we can make
 * gRPC calls from a Consul registration.
 */
public class ConsulGrpcHealthCheckNetworkTest {

    @Test
    void testConsulCanPerformGrpcHealthChecksOnSharedNetwork() throws Exception {
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
                        .withExposedPorts(9090, 8090)
                        .withNetwork(network)
                        .withNetworkAliases("test-module")
                        .withEnv("QUARKUS_HTTP_PORT", "8090")
                        .withEnv("QUARKUS_GRPC_SERVER_PORT", "9090")
                        .withLogConsumer(outputFrame -> System.out.print("[test-module] " + outputFrame.getUtf8String()))
                        .waitingFor(Wait.forHttp("/q/health").forPort(8090))) {

                    testModule.start();
                    System.out.println("Test module started on network with alias 'test-module'");

                    // Step 1: Verify we can make a gRPC call to the module via external port
                    System.out.println("\n=== Step 1: Testing direct gRPC connectivity ===");
                    ManagedChannel externalChannel = ManagedChannelBuilder
                        .forAddress("localhost", testModule.getMappedPort(9090))
                        .usePlaintext()
                        .build();

                    try {
                        var grpcClient = MutinyPipeStepProcessorGrpc.newMutinyStub(externalChannel);
                        ServiceRegistrationResponse registrationData = grpcClient.getServiceRegistration(RegistrationRequest.getDefaultInstance())
                            .await().atMost(java.time.Duration.ofSeconds(5));

                        assertEquals("test-processor", registrationData.getModuleName());
                        System.out.println("✓ Direct gRPC call successful: " + registrationData.getModuleName());
                    } finally {
                        externalChannel.shutdown();
                    }

                    // Step 2: Register the module in Consul with internal network addresses
                    System.out.println("\n=== Step 2: Registering module in Consul ===");
                    String serviceJson = """
                        {
                            "ID": "test-module-health-check-test",
                            "Name": "test-module-health",
                            "Tags": ["grpc", "test"],
                            "Address": "test-module",
                            "Port": 9090,
                            "Check": {
                                "Name": "gRPC Health Check",
                                "GRPC": "test-module:9090",
                                "GRPCUseTLS": false,
                                "Interval": "5s",
                                "Timeout": "2s"
                            }
                        }
                        """;

                    HttpClient httpClient = HttpClient.newHttpClient();
                    HttpRequest registerRequest = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + consul.getMappedPort(8500) + "/v1/agent/service/register"))
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString(serviceJson))
                        .build();

                    HttpResponse<String> registerResponse = httpClient.send(registerRequest, HttpResponse.BodyHandlers.ofString());
                    assertEquals(200, registerResponse.statusCode(), "Service registration should succeed");
                    System.out.println("✓ Service registered in Consul");

                    // Step 3: Wait for Consul to perform health checks
                    System.out.println("\n=== Step 3: Waiting for Consul health checks ===");
                    Thread.sleep(10000); // Wait for at least 2 health check intervals

                    // Step 4: Verify the service is healthy
                    System.out.println("\n=== Step 4: Verifying service health in Consul ===");
                    HttpRequest healthRequest = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + consul.getMappedPort(8500) + "/v1/health/service/test-module-health?passing=true"))
                        .GET()
                        .build();

                    HttpResponse<String> healthResponse = httpClient.send(healthRequest, HttpResponse.BodyHandlers.ofString());
                    assertEquals(200, healthResponse.statusCode());

                    String body = healthResponse.body();
                    System.out.println("Health check response: " + body);

                    // Verify the service is marked as healthy
                    assertTrue(body.contains("test-module-health-check-test"), "Service should be in health response");
                    assertTrue(body.contains("\"Status\": \"passing\""), "Service should be passing health checks");
                    assertTrue(body.contains("\"Type\": \"grpc\""), "Should be using gRPC health check");
                    assertTrue(body.contains("\"Output\": \"gRPC check test-module:9090: success\""), "gRPC health check should succeed");

                    System.out.println("\n✓ SUCCESS: Consul successfully performed gRPC health checks using network aliases!");
                    System.out.println("This proves:");
                    System.out.println("  1. Containers can communicate via Docker network aliases");
                    System.out.println("  2. Consul can perform gRPC health checks on network-internal addresses");
                    System.out.println("  3. The module registration flow works with container networking");

                    // Step 5: Demonstrate we can make a gRPC call through the registered service
                    System.out.println("\n=== Step 5: Making gRPC call to verify service functionality ===");
                    PipeDoc testDoc = PipeDoc.newBuilder()
                        .setId("test-doc-" + System.currentTimeMillis())
                        .setTitle("Network Test Document")
                        .setBody("Test document for network validation")
                        .build();

                    ProcessRequest processRequest = ProcessRequest.newBuilder()
                        .setDocument(testDoc)
                        .build();

                    // Use external port for this test (in production, would use service discovery)
                    ManagedChannel serviceChannel = ManagedChannelBuilder
                        .forAddress("localhost", testModule.getMappedPort(9090))
                        .usePlaintext()
                        .build();

                    try {
                        var serviceClient = MutinyPipeStepProcessorGrpc.newMutinyStub(serviceChannel);
                        ProcessResponse processResponse = serviceClient.processData(processRequest)
                            .await().atMost(java.time.Duration.ofSeconds(5));

                        assertTrue(processResponse.getSuccess(), "Processing should succeed");
                        assertTrue(processResponse.getProcessorLogsList().stream()
                            .anyMatch(log -> log.contains("Document processed successfully")), 
                            "Should have success log");
                        System.out.println("✓ Successfully processed document through registered service");
                    } finally {
                        serviceChannel.shutdown();
                    }
                }
            }
        }
    }
}
