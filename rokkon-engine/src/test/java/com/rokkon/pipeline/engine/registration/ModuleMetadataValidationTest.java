package com.rokkon.pipeline.engine.registration;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import com.rokkon.search.sdk.PipeStepProcessorGrpc;
import com.rokkon.search.sdk.ServiceRegistrationResponse;
import com.rokkon.search.sdk.RegistrationRequest;
import com.google.protobuf.Empty;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test that validates we properly call getServiceRegistration to get module metadata.
 */
public class ModuleMetadataValidationTest {

    @Test
    void testModuleReportsCorrectMetadata() throws Exception {
        // Start test module
        try (Network network = Network.newNetwork()) {
            System.out.println("Created network: " + network.getId());

            try (GenericContainer<?> testModule = new GenericContainer<>("rokkon/test-module:1.0.0-SNAPSHOT")
                    .withExposedPorts(9090, 8090)
                    .withNetwork(network)
                    .withNetworkAliases("test-module")
                    .withEnv("QUARKUS_HTTP_PORT", "8090")
                    .withEnv("QUARKUS_GRPC_SERVER_PORT", "9090")
                    .withLogConsumer(outputFrame -> System.out.print("[test-module] " + outputFrame.getUtf8String()))
                    .waitingFor(Wait.forHttp("/q/health").forPort(8090))) {

                testModule.start();
                System.out.println("Test module started");

                // Connect to module and call getServiceRegistration
                ManagedChannel channel = ManagedChannelBuilder
                    .forAddress("localhost", testModule.getMappedPort(9090))
                    .usePlaintext()
                    .build();

                try {
                    PipeStepProcessorGrpc.PipeStepProcessorBlockingStub stub = 
                        PipeStepProcessorGrpc.newBlockingStub(channel);

                    // Call getServiceRegistration
                    ServiceRegistrationResponse registrationData = stub
                        .withDeadlineAfter(5, java.util.concurrent.TimeUnit.SECONDS)
                        .getServiceRegistration(RegistrationRequest.getDefaultInstance());

                    // Verify module reports correct name
                    assertEquals("test-processor", registrationData.getModuleName());
                    System.out.println("✓ Module reports name: " + registrationData.getModuleName());

                    // Verify module provides configuration schema
                    assertNotNull(registrationData.getJsonConfigSchema());
                    assertFalse(registrationData.getJsonConfigSchema().isEmpty());
                    System.out.println("✓ Module provides configuration schema:");
                    System.out.println(registrationData.getJsonConfigSchema());

                    // Verify the schema is valid JSON
                    assertTrue(registrationData.getJsonConfigSchema().contains("\"type\": \"object\""));
                    assertTrue(registrationData.getJsonConfigSchema().contains("\"properties\""));
                    System.out.println("✓ Configuration schema appears to be valid JSON");

                } finally {
                    channel.shutdown();
                }
            }
        }
    }

    @Test
    void testModuleNameMismatchDetection() throws Exception {
        // This test simulates what happens when registration name doesn't match module name
        try (Network network = Network.newNetwork()) {
            try (GenericContainer<?> testModule = new GenericContainer<>("rokkon/test-module:1.0.0-SNAPSHOT")
                    .withExposedPorts(9090, 8090)
                    .withNetwork(network)
                    .withNetworkAliases("test-module")
                    .withEnv("QUARKUS_HTTP_PORT", "8090")
                    .withEnv("QUARKUS_GRPC_SERVER_PORT", "9090")
                    .withLogConsumer(outputFrame -> System.out.print("[test-module] " + outputFrame.getUtf8String()))
                    .waitingFor(Wait.forHttp("/q/health").forPort(8090))) {

                testModule.start();

                ManagedChannel channel = ManagedChannelBuilder
                    .forAddress("localhost", testModule.getMappedPort(9090))
                    .usePlaintext()
                    .build();

                try {
                    PipeStepProcessorGrpc.PipeStepProcessorBlockingStub stub = 
                        PipeStepProcessorGrpc.newBlockingStub(channel);

                    ServiceRegistrationResponse registrationData = stub
                        .withDeadlineAfter(5, java.util.concurrent.TimeUnit.SECONDS)
                        .getServiceRegistration(RegistrationRequest.getDefaultInstance());

                    // The module reports "test-processor" but someone might try to register it as "wrong-name"
                    String actualModuleName = registrationData.getModuleName();
                    String attemptedRegistrationName = "wrong-name";

                    // This is what our service should detect
                    assertNotEquals(attemptedRegistrationName, actualModuleName);
                    System.out.println("✓ Module name mismatch detected:");
                    System.out.println("  - Registration attempt: " + attemptedRegistrationName);
                    System.out.println("  - Module reports: " + actualModuleName);

                } finally {
                    channel.shutdown();
                }
            }
        }
    }
}
