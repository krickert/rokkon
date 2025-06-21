package com.rokkon.pipeline.registration;

import com.rokkon.search.grpc.ModuleRegistration;
import com.rokkon.search.grpc.ModuleRegistrationClient;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/**
 * Integration test for ModuleRegistration service using @QuarkusIntegrationTest.
 * Uses external gRPC client connecting to the running service.
 */
@QuarkusIntegrationTest
public class ModuleRegistrationServiceIT extends ModuleRegistrationTestBase {

    private ManagedChannel channel;
    private ModuleRegistration moduleRegistrationService;

    @BeforeEach
    void setup() {
        // Integration tests use the actual service port
        int port = 9090; // Default gRPC port from application.yml
        channel = ManagedChannelBuilder
                .forAddress("localhost", port)
                .usePlaintext()
                .build();
        moduleRegistrationService = new ModuleRegistrationClient("moduleRegistration", channel, 
                (serviceName, interceptors) -> interceptors);
    }

    @AfterEach
    void cleanup() {
        if (channel != null) {
            channel.shutdown();
        }
    }

    @Override
    protected ModuleRegistration getModuleRegistrationService() {
        return moduleRegistrationService;
    }
}