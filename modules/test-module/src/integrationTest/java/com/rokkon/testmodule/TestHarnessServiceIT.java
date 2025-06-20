package com.rokkon.testmodule;

import com.google.protobuf.Empty;
import com.rokkon.search.model.PipeDoc;
import com.rokkon.search.sdk.ProcessRequest;
import com.rokkon.search.sdk.ServiceMetadata;
import com.rokkon.test.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusIntegrationTest
class TestHarnessServiceIT {
    
    private ManagedChannel channel;
    private TestHarnessClient testHarnessClient;
    
    @BeforeEach
    void setup() {
        // In integration tests, the service runs on the configured port
        int port = 49095; // Standardized port for test-module
        channel = ManagedChannelBuilder
                .forAddress("localhost", port)
                .usePlaintext()
                .build();
        testHarnessClient = new TestHarnessClient("TestHarness", channel, (name, stub) -> stub);
    }
    
    @AfterEach
    void cleanup() {
        if (channel != null) {
            channel.shutdown();
            try {
                channel.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                channel.shutdownNow();
            }
        }
    }
    
    @Test
    void testModuleStatusInProdMode() {
        ModuleStatus status = testHarnessClient.getModuleStatus(Empty.getDefaultInstance())
                .await().atMost(Duration.ofSeconds(5));
        
        assertThat(status).isNotNull();
        assertThat(status.getModuleName()).isEqualTo("test-processor");
        // In prod mode, module should be functioning
        assertThat(status.hasLastActivity()).isTrue();
    }
    
    @Test
    void testStreamingWithMultipleClients() throws InterruptedException {
        // Simulate multiple clients connecting with concurrent streams
        CountDownLatch latch = new CountDownLatch(2);
        List<TestEvent> stream1Events = new ArrayList<>();
        List<TestEvent> stream2Events = new ArrayList<>();
        
        // Stream 1
        Multi<TestCommand> commands1 = Multi.createFrom().items(
                createDocumentProcessCommand("stream1-doc"),
                createHealthCheckCommand()
        );
        
        testHarnessClient.executeTestStream(commands1)
                .subscribe().with(
                        stream1Events::add,
                        error -> latch.countDown(),
                        latch::countDown
                );
        
        // Stream 2 - Using the same client but different stream
        Multi<TestCommand> commands2 = Multi.createFrom().items(
                createDocumentProcessCommand("stream2-doc"),
                createHealthCheckCommand()
        );
        
        testHarnessClient.executeTestStream(commands2)
                .subscribe().with(
                        stream2Events::add,
                        error -> latch.countDown(),
                        latch::countDown
                );
        
        // Wait for both streams to complete
        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        
        // Both streams should have received events
        assertThat(stream1Events).isNotEmpty();
        assertThat(stream2Events).isNotEmpty();
        
        // Each stream should have events for their own documents
        boolean stream1HasOwnDoc = stream1Events.stream()
                .filter(TestEvent::hasDocumentProcessed)
                .anyMatch(e -> e.getDocumentProcessed().getDocumentId().equals("stream1-doc"));
        
        boolean stream2HasOwnDoc = stream2Events.stream()
                .filter(TestEvent::hasDocumentProcessed)
                .anyMatch(e -> e.getDocumentProcessed().getDocumentId().equals("stream2-doc"));
        
        assertThat(stream1HasOwnDoc).isTrue();
        assertThat(stream2HasOwnDoc).isTrue();
    }
    
    @Test
    void testErrorHandling() {
        // Create a command that will cause an error
        TestCommand errorCommand = TestCommand.newBuilder()
                .setCommandId(UUID.randomUUID().toString())
                .setTimestamp(com.google.protobuf.Timestamp.newBuilder()
                        .setSeconds(System.currentTimeMillis() / 1000)
                        .build())
                .setProcessDocument(ProcessDocumentCommand.newBuilder()
                        .setRequest(ProcessRequest.newBuilder()
                                // No document - might cause issues
                                .setMetadata(ServiceMetadata.newBuilder()
                                        .setPipelineName("error-test")
                                        .setPipeStepName("error-step")
                                        .setStreamId(UUID.randomUUID().toString())
                                        .setCurrentHopNumber(1)
                                        .build())
                                .build())
                        .setExpectSuccess(false)
                        .setTimeoutMs(5000)
                        .build())
                .build();
        
        TestResult result = testHarnessClient.executeTest(errorCommand)
                .await().atMost(Duration.ofSeconds(5));
        
        assertThat(result).isNotNull();
        // Even with no document, the harness should handle it gracefully
        assertThat(result.getSuccess()).isTrue();
    }
    
    @Test
    void testModuleConfiguration() {
        // Configure the module
        TestCommand configCommand = TestCommand.newBuilder()
                .setCommandId(UUID.randomUUID().toString())
                .setTimestamp(com.google.protobuf.Timestamp.newBuilder()
                        .setSeconds(System.currentTimeMillis() / 1000)
                        .build())
                .setConfigureModule(ConfigureModuleCommand.newBuilder()
                        .setConfig(com.google.protobuf.Struct.newBuilder()
                                .putFields("test_mode", com.google.protobuf.Value.newBuilder()
                                        .setStringValue("integration-test")
                                        .build())
                                .putFields("delay_ms", com.google.protobuf.Value.newBuilder()
                                        .setNumberValue(100)
                                        .build())
                                .build())
                        .setResetToDefaults(false)
                        .build())
                .build();
        
        TestResult result = testHarnessClient.executeTest(configCommand)
                .await().atMost(Duration.ofSeconds(5));
        
        assertThat(result.getSuccess()).isTrue();
        
        // Check that configuration was applied
        ModuleStatus status = testHarnessClient.getModuleStatus(Empty.getDefaultInstance())
                .await().atMost(Duration.ofSeconds(5));
        
        assertThat(status.getCurrentConfigMap()).containsKey("test_mode");
        assertThat(status.getCurrentConfigMap().get("test_mode")).isEqualTo("integration-test");
    }
    
    private TestCommand createHealthCheckCommand() {
        return TestCommand.newBuilder()
                .setCommandId(UUID.randomUUID().toString())
                .setTimestamp(com.google.protobuf.Timestamp.newBuilder()
                        .setSeconds(System.currentTimeMillis() / 1000)
                        .build())
                .setCheckHealth(CheckHealthCommand.newBuilder()
                        .setIncludeDetails(true)
                        .build())
                .build();
    }
    
    private TestCommand createDocumentProcessCommand(String docId) {
        PipeDoc testDoc = PipeDoc.newBuilder()
                .setId(docId)
                .setTitle("Integration Test Document")
                .setBody("Testing in production mode")
                .build();
        
        ProcessRequest processRequest = ProcessRequest.newBuilder()
                .setDocument(testDoc)
                .setMetadata(ServiceMetadata.newBuilder()
                        .setPipelineName("integration-test-pipeline")
                        .setPipeStepName("integration-test-step")
                        .setStreamId(UUID.randomUUID().toString())
                        .setCurrentHopNumber(1)
                        .build())
                .build();
        
        return TestCommand.newBuilder()
                .setCommandId(UUID.randomUUID().toString())
                .setTimestamp(com.google.protobuf.Timestamp.newBuilder()
                        .setSeconds(System.currentTimeMillis() / 1000)
                        .build())
                .setProcessDocument(ProcessDocumentCommand.newBuilder()
                        .setRequest(processRequest)
                        .setExpectSuccess(true)
                        .setTimeoutMs(5000)
                        .build())
                .build();
    }
}