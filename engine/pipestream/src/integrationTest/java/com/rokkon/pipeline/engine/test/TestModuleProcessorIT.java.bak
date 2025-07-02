package com.rokkon.pipeline.engine.test;

import com.rokkon.search.sdk.PipeStepProcessor;
import com.rokkon.search.sdk.PipeStepProcessorClient;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration test using the real test-module Docker container.
 * This replaces DummyProcessorIT with the actual test-module.
 */
@QuarkusIntegrationTest
@Testcontainers
public class TestModuleProcessorIT extends TestModuleProcessorTestBase {

    private static final int MODULE_GRPC_PORT = 49093;
    
    @Container
    static GenericContainer<?> testModuleContainer = new GenericContainer<>("rokkon/test-module:1.0.0-SNAPSHOT")
            .withExposedPorts(MODULE_GRPC_PORT)
            .waitingFor(Wait.forLogMessage(".*Started gRPC server.*", 1))
            .withEnv("QUARKUS_LOG_LEVEL", "INFO");
    
    private static ManagedChannel channel;
    private static PipeStepProcessor pipeStepProcessor;
    
    @BeforeAll
    static void setupAll() {
        // Connect to the test module container
        String host = testModuleContainer.getHost();
        Integer port = testModuleContainer.getMappedPort(MODULE_GRPC_PORT);
        
        channel = ManagedChannelBuilder
                .forAddress(host, port)
                .usePlaintext()
                .build();
        
        pipeStepProcessor = new PipeStepProcessorClient("pipeStepProcessor", channel,
                (serviceName, interceptors) -> interceptors);
    }
    
    @AfterAll
    static void cleanupAll() {
        if (channel != null && !channel.isShutdown()) {
            channel.shutdown();
        }
    }
    
    @Override
    protected PipeStepProcessor getProcessor() {
        return pipeStepProcessor;
    }
}