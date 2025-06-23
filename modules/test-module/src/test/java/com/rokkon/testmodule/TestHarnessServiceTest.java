package com.rokkon.testmodule;

import com.google.protobuf.Empty;
import com.rokkon.search.model.PipeDoc;
import com.rokkon.search.sdk.ProcessRequest;
import com.rokkon.search.sdk.ServiceMetadata;
import com.rokkon.test.*;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.helpers.test.AssertSubscriber;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class TestHarnessServiceTest {
    
    @GrpcClient
    TestHarness testHarness;
    
    @Test
    void testModuleStatus() {
        ModuleStatus status = testHarness.getModuleStatus(Empty.getDefaultInstance())
                .await().atMost(Duration.ofSeconds(5));
        
        assertThat(status).isNotNull();
        assertThat(status.getModuleName()).isEqualTo("test-processor");
        assertThat(status.getDocumentsProcessed()).isGreaterThanOrEqualTo(0);
        assertThat(status.getDocumentsFailed()).isGreaterThanOrEqualTo(0);
    }
    
    @Test
    void testSingleCommand() {
        // Create a simple check health command
        TestCommand command = TestCommand.newBuilder()
                .setCommandId(UUID.randomUUID().toString())
                .setTimestamp(com.google.protobuf.Timestamp.newBuilder()
                        .setSeconds(System.currentTimeMillis() / 1000)
                        .build())
                .setCheckHealth(CheckHealthCommand.newBuilder()
                        .setIncludeDetails(true)
                        .build())
                .build();
        
        TestResult result = testHarness.executeTest(command)
                .await().atMost(Duration.ofSeconds(5));
        
        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getEventsCount()).isGreaterThan(0);
        
        // Should have a health check event
        TestEvent healthEvent = result.getEventsList().stream()
                .filter(TestEvent::hasHealthCheck)
                .findFirst()
                .orElse(null);
        
        assertThat(healthEvent).isNotNull();
        assertThat(healthEvent.getHealthCheck().getStatus()).isEqualTo(HealthCheckEvent.HealthStatus.HEALTHY);
    }
    
    @Test
    void testDocumentProcessing() {
        // Create a test document
        PipeDoc testDoc = PipeDoc.newBuilder()
                .setId(UUID.randomUUID().toString())
                .setTitle("Test Document")
                .setBody("This is a test document for the harness")
                .build();
        
        ProcessRequest processRequest = ProcessRequest.newBuilder()
                .setDocument(testDoc)
                .setMetadata(ServiceMetadata.newBuilder()
                        .setPipelineName("test-pipeline")
                        .setPipeStepName("test-step")
                        .setStreamId(UUID.randomUUID().toString())
                        .setCurrentHopNumber(1)
                        .build())
                .build();
        
        TestCommand command = TestCommand.newBuilder()
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
        
        TestResult result = testHarness.executeTest(command)
                .await().atMost(Duration.ofSeconds(10));
        
        assertThat(result.getSuccess()).isTrue();
        
        // Should have both received and processed events
        boolean hasReceivedEvent = result.getEventsList().stream()
                .anyMatch(TestEvent::hasDocumentReceived);
        boolean hasProcessedEvent = result.getEventsList().stream()
                .anyMatch(TestEvent::hasDocumentProcessed);
        
        assertThat(hasReceivedEvent).isTrue();
        assertThat(hasProcessedEvent).isTrue();
        
        // Check the processed event
        TestEvent processedEvent = result.getEventsList().stream()
                .filter(TestEvent::hasDocumentProcessed)
                .findFirst()
                .orElse(null);
        
        assertThat(processedEvent).isNotNull();
        assertThat(processedEvent.getDocumentProcessed().getSuccess()).isTrue();
        assertThat(processedEvent.getDocumentProcessed().getDocumentId()).isEqualTo(testDoc.getId());
    }
    
    @Test
    void testStreamingCommands() {
        // Create multiple commands
        Multi<TestCommand> commands = Multi.createFrom().items(
                createHealthCheckCommand(),
                createDocumentProcessCommand(),
                createRegistrationCheckCommand()
        );
        
        AssertSubscriber<TestEvent> subscriber = testHarness.executeTestStream(commands)
                .subscribe().withSubscriber(AssertSubscriber.create(10));
        
        subscriber.awaitCompletion(Duration.ofSeconds(10));
        
        assertThat(subscriber.getItems()).isNotEmpty();
        assertThat(subscriber.getFailure()).isNull();
        
        // Should have events for each command
        long healthEvents = subscriber.getItems().stream()
                .filter(TestEvent::hasHealthCheck)
                .count();
        long processedEvents = subscriber.getItems().stream()
                .filter(TestEvent::hasDocumentProcessed)
                .count();
        long registrationEvents = subscriber.getItems().stream()
                .filter(TestEvent::hasModuleRegistered)
                .count();
        
        assertThat(healthEvents).isGreaterThanOrEqualTo(1);
        assertThat(processedEvents).isGreaterThanOrEqualTo(1);
        assertThat(registrationEvents).isGreaterThanOrEqualTo(1);
    }
    
    private TestCommand createHealthCheckCommand() {
        return TestCommand.newBuilder()
                .setCommandId(UUID.randomUUID().toString())
                .setTimestamp(com.google.protobuf.Timestamp.newBuilder()
                        .setSeconds(System.currentTimeMillis() / 1000)
                        .build())
                .setCheckHealth(CheckHealthCommand.newBuilder()
                        .setIncludeDetails(false)
                        .build())
                .build();
    }
    
    private TestCommand createDocumentProcessCommand() {
        PipeDoc testDoc = PipeDoc.newBuilder()
                .setId(UUID.randomUUID().toString())
                .setTitle("Stream Test Document")
                .setBody("Testing streaming functionality")
                .build();
        
        ProcessRequest processRequest = ProcessRequest.newBuilder()
                .setDocument(testDoc)
                .setMetadata(ServiceMetadata.newBuilder()
                        .setPipelineName("stream-test-pipeline")
                        .setPipeStepName("stream-test-step")
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
    
    private TestCommand createRegistrationCheckCommand() {
        return TestCommand.newBuilder()
                .setCommandId(UUID.randomUUID().toString())
                .setTimestamp(com.google.protobuf.Timestamp.newBuilder()
                        .setSeconds(System.currentTimeMillis() / 1000)
                        .build())
                .setVerifyRegistration(VerifyRegistrationCommand.newBuilder()
                        .setExpectedModuleName("test-processor")
                        .setCheckConsul(false)
                        .build())
                .build();
    }
}