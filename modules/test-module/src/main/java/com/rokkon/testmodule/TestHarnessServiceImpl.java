package com.rokkon.testmodule;

import com.google.protobuf.Empty;
import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.google.protobuf.Value;
import com.rokkon.search.sdk.PipeStepProcessor;
import com.rokkon.search.sdk.ProcessRequest;
import com.rokkon.search.sdk.ProcessResponse;
import com.rokkon.test.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.quarkus.arc.All;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Test harness implementation for integration testing.
 * Provides bidirectional streaming for test orchestration and observation.
 */
@GrpcService
@Singleton
public class TestHarnessServiceImpl implements TestHarness {
    
    private static final Logger LOG = Logger.getLogger(TestHarnessServiceImpl.class);
    
    @Inject
    @io.quarkus.grpc.GrpcService
    TestProcessorServiceImpl testProcessor;
    
    @Inject
    MeterRegistry meterRegistry;
    
    @ConfigProperty(name = "test.processor.name", defaultValue = "test-processor")
    String processorName;
    
    // Track module state
    private final AtomicBoolean isRegistered = new AtomicBoolean(false);
    private final AtomicLong documentsProcessed = new AtomicLong(0);
    private final AtomicLong documentsFailed = new AtomicLong(0);
    private final Map<String, String> currentConfig = new ConcurrentHashMap<>();
    private volatile Instant lastActivity = Instant.now();
    
    @Override
    public Multi<TestEvent> executeTestStream(Multi<TestCommand> commands) {
        // Following the Quarkus guide pattern for bidirectional streaming
        return commands
                .onItem().transformToMultiAndConcatenate(command -> {
                    LOG.infof("TestHarness received command: %s", command.getCommandCase());
                    
                    // Process each command and return a Multi of events
                    return processCommandToMulti(command);
                })
                .onFailure().recoverWithMulti(throwable -> {
                    LOG.errorf(throwable, "Error in test stream");
                    return Multi.createFrom().item(createErrorEvent(null, throwable));
                })
                .onCompletion().invoke(() -> LOG.info("Test stream completed"));
    }
    
    @Override
    public Uni<TestResult> executeTest(TestCommand command) {
        LOG.infof("TestHarness executing single test command: %s", command.getCommandCase());
        
        // Process the command and collect all events
        return processCommandToMulti(command)
                .collect().asList()
                .map(events -> {
                    TestResult.Builder resultBuilder = TestResult.newBuilder()
                            .setSuccess(true);
                    events.forEach(resultBuilder::addEvents);
                    return resultBuilder.build();
                })
                .onFailure().recoverWithItem(throwable -> {
                    LOG.errorf(throwable, "Test execution failed");
                    return TestResult.newBuilder()
                            .setSuccess(false)
                            .addMessages("Error: " + throwable.getMessage())
                            .addEvents(createErrorEvent(command.getCommandId(), throwable))
                            .build();
                });
    }
    
    @Override
    public Uni<ModuleStatus> getModuleStatus(Empty request) {
        return Uni.createFrom().item(() -> {
            Struct.Builder configStruct = Struct.newBuilder();
            currentConfig.forEach((key, value) -> 
                configStruct.putFields(key, Value.newBuilder().setStringValue(value).build())
            );
            
            return ModuleStatus.newBuilder()
                    .setIsRegistered(isRegistered.get())
                    .setModuleName(processorName)
                    .setDocumentsProcessed(documentsProcessed.get())
                    .setDocumentsFailed(documentsFailed.get())
                    .setLastActivity(Timestamp.newBuilder()
                            .setSeconds(lastActivity.getEpochSecond())
                            .setNanos(lastActivity.getNano())
                            .build())
                    .putAllCurrentConfig(currentConfig)
                    .build();
        });
    }
    
    private Multi<TestEvent> processCommandToMulti(TestCommand command) {
        lastActivity = Instant.now();
        
        return switch (command.getCommandCase()) {
            case PROCESS_DOCUMENT -> processDocumentCommand(command.getCommandId(), command.getProcessDocument());
            case VERIFY_REGISTRATION -> verifyRegistrationCommand(command.getCommandId(), command.getVerifyRegistration());
            case CHECK_HEALTH -> checkHealthCommand(command.getCommandId(), command.getCheckHealth());
            case CONFIGURE_MODULE -> configureModuleCommand(command.getCommandId(), command.getConfigureModule());
            case SIMULATE_SCENARIO -> simulateScenarioCommand(command.getCommandId(), command.getSimulateScenario());
            case WAIT_FOR_EVENT -> waitForEventCommand(command.getCommandId(), command.getWaitForEvent());
            default -> {
                LOG.warnf("Unknown command type: %s", command.getCommandCase());
                yield Multi.createFrom().item(createErrorEvent(command.getCommandId(), 
                    new IllegalArgumentException("Unknown command type: " + command.getCommandCase())));
            }
        };
    }
    
    private Multi<TestEvent> processDocumentCommand(String commandId, ProcessDocumentCommand cmd) {
        ProcessRequest request = cmd.getRequest();
        
        return Multi.createFrom().emitter(emitter -> {
            // Emit document received event
            if (request.hasDocument()) {
                DocumentReceivedEvent.Builder receivedEvent = DocumentReceivedEvent.newBuilder()
                        .setDocumentId(request.getDocument().getId());
                
                if (request.hasMetadata()) {
                    receivedEvent
                            .setPipelineName(request.getMetadata().getPipelineName())
                            .setStepName(request.getMetadata().getPipeStepName())
                            .setHopNumber(request.getMetadata().getCurrentHopNumber());
                }
                
                emitter.emit(TestEvent.newBuilder()
                        .setEventId(UUID.randomUUID().toString())
                        .setCommandId(commandId)
                        .setTimestamp(nowTimestamp())
                        .setDocumentReceived(receivedEvent.build())
                        .build());
            }
            
            // Process the document
            long startTime = System.currentTimeMillis();
            testProcessor.processData(request)
                    .subscribe().with(
                            response -> {
                                long processingTime = System.currentTimeMillis() - startTime;
                                
                                if (response.getSuccess()) {
                                    documentsProcessed.incrementAndGet();
                                } else {
                                    documentsFailed.incrementAndGet();
                                }
                                
                                // Build processing metadata
                                Struct.Builder metadata = Struct.newBuilder();
                                metadata.putFields("processor_logs_count", 
                                        Value.newBuilder().setNumberValue(response.getProcessorLogsCount()).build());
                                if (response.hasErrorDetails()) {
                                    metadata.putFields("error_details", 
                                            Value.newBuilder().setStructValue(response.getErrorDetails()).build());
                                }
                                
                                // Emit document processed event
                                DocumentProcessedEvent processedEvent = DocumentProcessedEvent.newBuilder()
                                        .setDocumentId(request.hasDocument() ? request.getDocument().getId() : "no-document")
                                        .setSuccess(response.getSuccess())
                                        .setErrorMessage(response.getSuccess() ? "" : String.join("; ", response.getProcessorLogsList()))
                                        .setProcessingMetadata(metadata.build())
                                        .setProcessingTimeMs(processingTime)
                                        .build();
                                
                                emitter.emit(TestEvent.newBuilder()
                                        .setEventId(UUID.randomUUID().toString())
                                        .setCommandId(commandId)
                                        .setTimestamp(nowTimestamp())
                                        .setDocumentProcessed(processedEvent)
                                        .build());
                                
                                emitter.complete();
                            },
                            failure -> {
                                documentsFailed.incrementAndGet();
                                emitter.emit(createErrorEvent(commandId, failure));
                                emitter.complete();
                            }
                    );
        });
    }
    
    private Multi<TestEvent> verifyRegistrationCommand(String commandId, VerifyRegistrationCommand cmd) {
        return Multi.createFrom().item(() -> {
            // Check if module believes it's registered
            boolean registered = isRegistered.get();
            String actualName = processorName;
            
            boolean success = true;
            String errorMessage = "";
            
            if (!cmd.getExpectedModuleName().isEmpty() && !cmd.getExpectedModuleName().equals(actualName)) {
                success = false;
                errorMessage = String.format("Module name mismatch. Expected: %s, Actual: %s", 
                        cmd.getExpectedModuleName(), actualName);
            }
            
            if (cmd.getCheckConsul()) {
                // TODO: Actually check Consul registration when we have access
                LOG.info("Consul check requested but not implemented yet");
            }
            
            ModuleRegisteredEvent registeredEvent = ModuleRegisteredEvent.newBuilder()
                    .setModuleName(actualName)
                    .setConsulServiceId(registered ? "test-module-" + UUID.randomUUID() : "")
                    .setSuccess(success && registered)
                    .setErrorMessage(errorMessage)
                    .build();
            
            return TestEvent.newBuilder()
                    .setEventId(UUID.randomUUID().toString())
                    .setCommandId(commandId)
                    .setTimestamp(nowTimestamp())
                    .setModuleRegistered(registeredEvent)
                    .build();
        });
    }
    
    private Multi<TestEvent> checkHealthCommand(String commandId, CheckHealthCommand cmd) {
        return Multi.createFrom().item(() -> {
            // Determine health status
            HealthCheckEvent.HealthStatus status = HealthCheckEvent.HealthStatus.HEALTHY;
            Struct.Builder details = Struct.newBuilder();
            
            // Add basic health metrics
            details.putFields("documents_processed", 
                    Value.newBuilder().setNumberValue(documentsProcessed.get()).build());
            details.putFields("documents_failed", 
                    Value.newBuilder().setNumberValue(documentsFailed.get()).build());
            details.putFields("uptime_seconds", 
                    Value.newBuilder().setNumberValue(
                            java.time.Duration.between(Instant.EPOCH, lastActivity).getSeconds()).build());
            
            if (cmd.getIncludeDetails()) {
                // Add detailed metrics
                details.putFields("is_registered", 
                        Value.newBuilder().setBoolValue(isRegistered.get()).build());
                
                // Check if we're having issues
                long total = documentsProcessed.get() + documentsFailed.get();
                if (total > 0) {
                    double failureRate = (double) documentsFailed.get() / total;
                    details.putFields("failure_rate", 
                            Value.newBuilder().setNumberValue(failureRate).build());
                    
                    if (failureRate > 0.5) {
                        status = HealthCheckEvent.HealthStatus.UNHEALTHY;
                    } else if (failureRate > 0.1) {
                        status = HealthCheckEvent.HealthStatus.DEGRADED;
                    }
                }
            }
            
            HealthCheckEvent healthEvent = HealthCheckEvent.newBuilder()
                    .setStatus(status)
                    .setDetails(details.build())
                    .build();
            
            return TestEvent.newBuilder()
                    .setEventId(UUID.randomUUID().toString())
                    .setCommandId(commandId)
                    .setTimestamp(nowTimestamp())
                    .setHealthCheck(healthEvent)
                    .build();
        });
    }
    
    private Multi<TestEvent> configureModuleCommand(String commandId, ConfigureModuleCommand cmd) {
        return Multi.createFrom().item(() -> {
            try {
                if (cmd.getResetToDefaults()) {
                    currentConfig.clear();
                    LOG.info("Module configuration reset to defaults");
                }
                
                if (cmd.hasConfig()) {
                    cmd.getConfig().getFieldsMap().forEach((key, value) -> {
                        String strValue = switch (value.getKindCase()) {
                            case STRING_VALUE -> value.getStringValue();
                            case NUMBER_VALUE -> String.valueOf(value.getNumberValue());
                            case BOOL_VALUE -> String.valueOf(value.getBoolValue());
                            default -> value.toString();
                        };
                        currentConfig.put(key, strValue);
                    });
                    LOG.infof("Module configuration updated: %s", currentConfig);
                }
                
                // Return success event
                return TestEvent.newBuilder()
                        .setEventId(UUID.randomUUID().toString())
                        .setCommandId(commandId)
                        .setTimestamp(nowTimestamp())
                        .setGeneric(GenericEvent.newBuilder()
                                .setEventType("configuration_updated")
                                .setData(Struct.newBuilder()
                                        .putFields("success", Value.newBuilder().setBoolValue(true).build())
                                        .build())
                                .build())
                        .build();
                
            } catch (Exception e) {
                return createErrorEvent(commandId, e);
            }
        });
    }
    
    private Multi<TestEvent> simulateScenarioCommand(String commandId, SimulateScenarioCommand cmd) {
        // For now, just acknowledge the scenario
        LOG.infof("Simulating scenario: %s for %d ms", cmd.getScenario(), cmd.getDurationMs());
        
        GenericEvent scenarioEvent = GenericEvent.newBuilder()
                .setEventType("scenario_started")
                .setData(Struct.newBuilder()
                        .putFields("scenario", Value.newBuilder().setStringValue(cmd.getScenario().name()).build())
                        .putFields("duration_ms", Value.newBuilder().setNumberValue(cmd.getDurationMs()).build())
                        .build())
                .build();
        
        return Multi.createFrom().item(TestEvent.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setCommandId(commandId)
                .setTimestamp(nowTimestamp())
                .setGeneric(scenarioEvent)
                .build());
        
        // TODO: Implement actual scenario simulation
    }
    
    private Multi<TestEvent> waitForEventCommand(String commandId, WaitForEventCommand cmd) {
        // This is more complex - we'd need to set up event watchers
        LOG.infof("Wait for event command received: %s", cmd.getEventTypesList());
        
        // For now, just acknowledge
        GenericEvent ackEvent = GenericEvent.newBuilder()
                .setEventType("wait_acknowledged")
                .setData(Struct.newBuilder()
                        .putFields("event_types", Value.newBuilder()
                                .setListValue(com.google.protobuf.ListValue.newBuilder()
                                        .addAllValues(cmd.getEventTypesList().stream()
                                                .map(type -> Value.newBuilder().setStringValue(type).build())
                                                .toList())
                                        .build())
                                .build())
                        .build())
                .build();
        
        return Multi.createFrom().item(TestEvent.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setCommandId(commandId)
                .setTimestamp(nowTimestamp())
                .setGeneric(ackEvent)
                .build());
    }
    
    private TestEvent createErrorEvent(String commandId, Throwable throwable) {
        return TestEvent.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setCommandId(commandId != null ? commandId : "")
                .setTimestamp(nowTimestamp())
                .setError(ErrorEvent.newBuilder()
                        .setErrorType(throwable.getClass().getSimpleName())
                        .setErrorMessage(throwable.getMessage() != null ? throwable.getMessage() : "")
                        .setStackTrace(getStackTraceString(throwable))
                        .build())
                .build();
    }
    
    private Timestamp nowTimestamp() {
        Instant now = Instant.now();
        return Timestamp.newBuilder()
                .setSeconds(now.getEpochSecond())
                .setNanos(now.getNano())
                .build();
    }
    
    private String getStackTraceString(Throwable throwable) {
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }
    
    // Public method to mark module as registered (called by registration logic)
    public void markAsRegistered(boolean registered) {
        this.isRegistered.set(registered);
        LOG.infof("Module registration status updated: %s", registered);
    }
}