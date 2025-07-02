package com.rokkon.test;

import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.google.protobuf.Value;
import com.rokkon.search.model.PipeDoc;
import com.rokkon.search.sdk.ProcessRequest;
import com.rokkon.search.sdk.ServiceMetadata;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Client for interacting with the TestHarness service during integration tests.
 * Provides a fluent API for test orchestration and verification.
 */
public class TestModuleHarnessClient implements AutoCloseable {
    
    private static final Logger LOG = Logger.getLogger(TestModuleHarnessClient.class);
    
    private final ManagedChannel channel;
    private final TestHarnessGrpc.TestHarnessStub asyncStub;
    private final TestHarnessGrpc.TestHarnessBlockingStub blockingStub;
    private final Map<String, TestEvent> receivedEvents = new ConcurrentHashMap<>();
    private StreamObserver<TestCommand> commandStream;
    private final AtomicBoolean streamActive = new AtomicBoolean(false);
    
    public TestModuleHarnessClient(String host, int port) {
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        this.asyncStub = TestHarnessGrpc.newStub(channel);
        this.blockingStub = TestHarnessGrpc.newBlockingStub(channel);
    }
    
    /**
     * Start the bidirectional streaming connection.
     * Must be called before sending commands via the stream.
     */
    public TestModuleHarnessClient startStream() {
        if (streamActive.get()) {
            throw new IllegalStateException("Stream already active");
        }
        
        StreamObserver<TestEvent> responseObserver = new StreamObserver<TestEvent>() {
            @Override
            public void onNext(TestEvent event) {
                LOG.debugf("Received event: %s for command: %s", 
                         event.getEventCase(), event.getCommandId());
                receivedEvents.put(event.getEventId(), event);
                
                // Log specific event types
                switch (event.getEventCase()) {
                    case DOCUMENT_PROCESSED -> {
                        DocumentProcessedEvent processed = event.getDocumentProcessed();
                        LOG.infof("Document %s processed: success=%s, time=%dms",
                                processed.getDocumentId(), 
                                processed.getSuccess(),
                                processed.getProcessingTimeMs());
                    }
                    case ERROR -> {
                        ErrorEvent error = event.getError();
                        LOG.errorf("Error event: %s - %s", 
                                 error.getErrorType(), error.getErrorMessage());
                    }
                    case MODULE_REGISTERED -> {
                        ModuleRegisteredEvent registered = event.getModuleRegistered();
                        LOG.infof("Module registered: %s (success=%s)",
                                registered.getModuleName(), registered.getSuccess());
                    }
                }
            }
            
            @Override
            public void onError(Throwable t) {
                LOG.errorf(t, "Stream error");
                streamActive.set(false);
            }
            
            @Override
            public void onCompleted() {
                LOG.info("Stream completed");
                streamActive.set(false);
            }
        };
        
        commandStream = asyncStub.executeTestStream(responseObserver);
        streamActive.set(true);
        return this;
    }
    
    /**
     * Send a document for processing and return a Uni that completes when processed.
     */
    public Uni<DocumentProcessedEvent> processDocument(PipeDoc document, String pipelineName, String stepName) {
        String commandId = UUID.randomUUID().toString();
        
        ProcessRequest request = ProcessRequest.newBuilder()
                .setDocument(document)
                .setMetadata(ServiceMetadata.newBuilder()
                        .setPipelineName(pipelineName)
                        .setPipeStepName(stepName)
                        .setStreamId(UUID.randomUUID().toString())
                        .setCurrentHopNumber(1)
                        .build())
                .build();
        
        TestCommand command = TestCommand.newBuilder()
                .setCommandId(commandId)
                .setTimestamp(nowTimestamp())
                .setProcessDocument(ProcessDocumentCommand.newBuilder()
                        .setRequest(request)
                        .setExpectSuccess(true)
                        .setTimeoutMs(5000)
                        .build())
                .build();
        
        if (streamActive.get()) {
            commandStream.onNext(command);
            
            // Wait for the processed event
            return waitForEvent(commandId, TestEvent.EventCase.DOCUMENT_PROCESSED, Duration.ofSeconds(10))
                    .map(TestEvent::getDocumentProcessed);
        } else {
            // Use single-shot API
            TestResult result = blockingStub.executeTest(command);
            return Uni.createFrom().item(() -> {
                for (TestEvent event : result.getEventsList()) {
                    if (event.hasDocumentProcessed()) {
                        return event.getDocumentProcessed();
                    }
                }
                throw new IllegalStateException("No document processed event in result");
            });
        }
    }
    
    /**
     * Verify module registration.
     */
    public Uni<ModuleRegisteredEvent> verifyRegistration(String expectedModuleName, boolean checkConsul) {
        String commandId = UUID.randomUUID().toString();
        
        TestCommand command = TestCommand.newBuilder()
                .setCommandId(commandId)
                .setTimestamp(nowTimestamp())
                .setVerifyRegistration(VerifyRegistrationCommand.newBuilder()
                        .setExpectedModuleName(expectedModuleName)
                        .setCheckConsul(checkConsul)
                        .build())
                .build();
        
        if (streamActive.get()) {
            commandStream.onNext(command);
            return waitForEvent(commandId, TestEvent.EventCase.MODULE_REGISTERED, Duration.ofSeconds(5))
                    .map(TestEvent::getModuleRegistered);
        } else {
            TestResult result = blockingStub.executeTest(command);
            return Uni.createFrom().item(() -> {
                for (TestEvent event : result.getEventsList()) {
                    if (event.hasModuleRegistered()) {
                        return event.getModuleRegistered();
                    }
                }
                throw new IllegalStateException("No module registered event in result");
            });
        }
    }
    
    /**
     * Check module health.
     */
    public Uni<HealthCheckEvent> checkHealth(boolean includeDetails) {
        String commandId = UUID.randomUUID().toString();
        
        TestCommand command = TestCommand.newBuilder()
                .setCommandId(commandId)
                .setTimestamp(nowTimestamp())
                .setCheckHealth(CheckHealthCommand.newBuilder()
                        .setIncludeDetails(includeDetails)
                        .build())
                .build();
        
        if (streamActive.get()) {
            commandStream.onNext(command);
            return waitForEvent(commandId, TestEvent.EventCase.HEALTH_CHECK, Duration.ofSeconds(5))
                    .map(TestEvent::getHealthCheck);
        } else {
            TestResult result = blockingStub.executeTest(command);
            return Uni.createFrom().item(() -> {
                for (TestEvent event : result.getEventsList()) {
                    if (event.hasHealthCheck()) {
                        return event.getHealthCheck();
                    }
                }
                throw new IllegalStateException("No health check event in result");
            });
        }
    }
    
    /**
     * Get current module status.
     */
    public ModuleStatus getModuleStatus() {
        return blockingStub.getModuleStatus(com.google.protobuf.Empty.getDefaultInstance());
    }
    
    /**
     * Configure the test module behavior.
     */
    public Uni<Void> configureModule(Map<String, Object> config) {
        String commandId = UUID.randomUUID().toString();
        
        Struct.Builder configStruct = Struct.newBuilder();
        config.forEach((key, value) -> {
            if (value instanceof String) {
                configStruct.putFields(key, Value.newBuilder().setStringValue((String) value).build());
            } else if (value instanceof Boolean) {
                configStruct.putFields(key, Value.newBuilder().setBoolValue((Boolean) value).build());
            } else if (value instanceof Number) {
                configStruct.putFields(key, Value.newBuilder().setNumberValue(((Number) value).doubleValue()).build());
            }
        });
        
        TestCommand command = TestCommand.newBuilder()
                .setCommandId(commandId)
                .setTimestamp(nowTimestamp())
                .setConfigureModule(ConfigureModuleCommand.newBuilder()
                        .setConfig(configStruct.build())
                        .build())
                .build();
        
        if (streamActive.get()) {
            commandStream.onNext(command);
            return waitForEvent(commandId, TestEvent.EventCase.GENERIC, Duration.ofSeconds(5))
                    .replaceWithVoid();
        } else {
            blockingStub.executeTest(command);
            return Uni.createFrom().voidItem();
        }
    }
    
    /**
     * Wait for a specific event type for a given command.
     */
    private Uni<TestEvent> waitForEvent(String commandId, TestEvent.EventCase eventType, Duration timeout) {
        return Uni.createFrom().emitter(emitter -> {
            long startTime = System.currentTimeMillis();
            long timeoutMs = timeout.toMillis();
            
            // Poll for the event
            Multi.createFrom().ticks().every(Duration.ofMillis(100))
                    .onItem().invoke(() -> {
                        // Check all received events
                        for (TestEvent event : receivedEvents.values()) {
                            if (event.getCommandId().equals(commandId) && 
                                event.getEventCase() == eventType) {
                                emitter.complete(event);
                                return;
                            }
                        }
                        
                        // Check timeout
                        if (System.currentTimeMillis() - startTime > timeoutMs) {
                            emitter.fail(new RuntimeException(
                                "Timeout waiting for " + eventType + " event for command " + commandId));
                        }
                    })
                    .subscribe().with(
                            tick -> {}, 
                            emitter::fail
                    );
        });
    }
    
    /**
     * Create a test document with basic fields.
     */
    public static PipeDoc createTestDocument(String id, String title, String body) {
        return PipeDoc.newBuilder()
                .setId(id)
                .setTitle(title)
                .setBody(body)
                .setCustomData(Struct.newBuilder()
                        .putFields("test_source", Value.newBuilder().setStringValue("harness-client").build())
                        .putFields("created_at", Value.newBuilder().setStringValue(Instant.now().toString()).build())
                        .build())
                .build();
    }
    
    /**
     * Stop the streaming connection.
     */
    public void stopStream() {
        if (streamActive.get() && commandStream != null) {
            commandStream.onCompleted();
            streamActive.set(false);
        }
    }
    
    @Override
    public void close() {
        stopStream();
        channel.shutdown();
        try {
            if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                channel.shutdownNow();
            }
        } catch (InterruptedException e) {
            channel.shutdownNow();
        }
    }
    
    private static Timestamp nowTimestamp() {
        Instant now = Instant.now();
        return Timestamp.newBuilder()
                .setSeconds(now.getEpochSecond())
                .setNanos(now.getNano())
                .build();
    }
    
    /**
     * Builder for creating a test harness client with fluent configuration.
     */
    public static class Builder {
        private String host = "localhost";
        private int port = 9090;
        
        public Builder withHost(String host) {
            this.host = host;
            return this;
        }
        
        public Builder withPort(int port) {
            this.port = port;
            return this;
        }
        
        public TestModuleHarnessClient build() {
            return new TestModuleHarnessClient(host, port);
        }
    }
    
    public static Builder builder() {
        return new Builder();
    }
}