package com.rokkon.testmodule;

import com.google.protobuf.Empty;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.rokkon.search.model.PipeDoc;
import com.rokkon.search.sdk.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Test processor for integration testing and as a reference implementation.
 * Includes full observability with metrics and tracing.
 */
@GrpcService
@Singleton
public class TestProcessorServiceImpl implements PipeStepProcessor {
    
    private static final Logger LOG = Logger.getLogger(TestProcessorServiceImpl.class);
    
    @ConfigProperty(name = "test.processor.name", defaultValue = "test-processor")
    String processorName;
    
    @ConfigProperty(name = "test.processor.delay.ms", defaultValue = "0")
    Long processingDelayMs;
    
    @Inject
    MeterRegistry registry;
    
    Counter processedDocuments;
    Counter failedDocuments;
    Timer processingTimer;
    
    @jakarta.annotation.PostConstruct
    void init() {
        this.processedDocuments = Counter.builder("test.processor.documents.processed")
                .description("Number of documents processed")
                .register(registry);
        
        this.failedDocuments = Counter.builder("test.processor.documents.failed")
                .description("Number of documents that failed processing")
                .register(registry);
        
        this.processingTimer = Timer.builder("test.processor.processing.time")
                .description("Time taken to process documents")
                .register(registry);
    }
    
    @Override
    public Uni<ProcessResponse> processData(ProcessRequest request) {
        return processingTimer.record(() -> processDataInternal(request));
    }
    
    private Uni<ProcessResponse> processDataInternal(ProcessRequest request) {
        LOG.infof("TestProcessor received request for document: %s", 
                  request.hasDocument() ? request.getDocument().getId() : "no-document");
        
        // Add artificial delay if configured (for testing timeouts, etc.)
        Uni<Void> delay = processingDelayMs > 0 
            ? Uni.createFrom().<Void>nullItem().onItem().delayIt().by(java.time.Duration.ofMillis(processingDelayMs))
            : Uni.createFrom().voidItem();
        
        return delay.onItem().transformToUni(v -> {
            try {
                ProcessResponse.Builder responseBuilder = ProcessResponse.newBuilder()
                        .setSuccess(true)
                        .addProcessorLogs("TestProcessor: Starting document processing");
                
                if (request.hasDocument()) {
                    // Process the document
                    PipeDoc doc = request.getDocument();
                    
                    // Log metadata if present
                    if (request.hasMetadata()) {
                        LOG.debugf("Processing document from pipeline: %s, step: %s, hop: %d",
                                request.getMetadata().getPipelineName(),
                                request.getMetadata().getPipeStepName(),
                                request.getMetadata().getCurrentHopNumber());
                    }
                    
                    // Create or update custom_data with processing metadata
                    Struct.Builder customDataBuilder = doc.hasCustomData() 
                        ? doc.getCustomData().toBuilder() 
                        : Struct.newBuilder();
                    
                    customDataBuilder
                        .putFields("processed_by", Value.newBuilder().setStringValue(processorName).build())
                        .putFields("processing_timestamp", Value.newBuilder().setStringValue(String.valueOf(System.currentTimeMillis())).build())
                        .putFields("test_module_version", Value.newBuilder().setStringValue("1.0.0").build());
                    
                    // Add config params if present
                    if (request.hasConfig() && request.getConfig().getConfigParamsCount() > 0) {
                        request.getConfig().getConfigParamsMap().forEach((key, value) -> 
                            customDataBuilder.putFields("config_" + key, Value.newBuilder().setStringValue(value).build())
                        );
                    }
                    
                    // Check mode from config
                    String mode = "test";
                    boolean requireSchema = false;
                    boolean simulateError = false;
                    
                    if (request.hasConfig() && request.getConfig().hasCustomJsonConfig()) {
                        Struct config = request.getConfig().getCustomJsonConfig();
                        if (config.containsFields("mode")) {
                            mode = config.getFieldsMap().get("mode").getStringValue();
                        }
                        if (config.containsFields("requireSchema")) {
                            requireSchema = config.getFieldsMap().get("requireSchema").getBoolValue();
                        }
                        if (config.containsFields("simulateError")) {
                            simulateError = config.getFieldsMap().get("simulateError").getBoolValue();
                        }
                    }
                    
                    // Simulate error if requested
                    if (simulateError) {
                        throw new RuntimeException("Simulated error for testing");
                    }
                    
                    // Schema validation mode
                    if (mode.equals("validate") || requireSchema) {
                        // Check if document has required fields for schema validation
                        if (!doc.hasTitle() || doc.getTitle().isEmpty()) {
                            throw new IllegalArgumentException("Schema validation failed: title is required");
                        }
                        if (!doc.hasBody() || doc.getBody().isEmpty()) {
                            throw new IllegalArgumentException("Schema validation failed: body is required");
                        }
                        responseBuilder.addProcessorLogs("TestProcessor: Schema validation passed");
                    }
                    
                    PipeDoc modifiedDoc = doc.toBuilder()
                            .setCustomData(customDataBuilder.build())
                            .build();
                    
                    responseBuilder.setOutputDoc(modifiedDoc);
                    responseBuilder.addProcessorLogs("TestProcessor: Added metadata to document");
                    responseBuilder.addProcessorLogs("TestProcessor: Document processed successfully");
                    
                    processedDocuments.increment();
                } else {
                    responseBuilder.addProcessorLogs("TestProcessor: No document provided");
                }
                
                ProcessResponse response = responseBuilder.build();
                LOG.infof("TestProcessor returning success: %s", response.getSuccess());
                
                return Uni.createFrom().item(response);
                
            } catch (Exception e) {
                LOG.errorf(e, "Error in TestProcessor");
                failedDocuments.increment();
                
                ProcessResponse errorResponse = ProcessResponse.newBuilder()
                        .setSuccess(false)
                        .addProcessorLogs("TestProcessor: Error - " + e.getMessage())
                        .setErrorDetails(Struct.newBuilder()
                                .putFields("error_type", Value.newBuilder().setStringValue(e.getClass().getSimpleName()).build())
                                .putFields("error_message", Value.newBuilder().setStringValue(e.getMessage()).build())
                                .build())
                        .build();
                
                return Uni.createFrom().item(errorResponse);
            }
        });
    }
    
    @Override
    public Uni<ServiceRegistrationData> getServiceRegistration(Empty request) {
        LOG.debugf("TestProcessor registration requested");
        
        ServiceRegistrationData registration = ServiceRegistrationData.newBuilder()
                .setModuleName(processorName)
                .setJsonConfigSchema("""
                    {
                        "type": "object",
                        "properties": {
                            "mode": {
                                "type": "string",
                                "enum": ["test", "validate", "transform"],
                                "default": "test",
                                "description": "Processing mode: test (passthrough), validate (schema check), transform (modify data)"
                            },
                            "addMetadata": {
                                "type": "boolean",
                                "default": true,
                                "description": "Whether to add processing metadata to custom_data"
                            },
                            "requireSchema": {
                                "type": "boolean",
                                "default": false,
                                "description": "Enforce schema validation regardless of mode"
                            },
                            "simulateError": {
                                "type": "boolean",
                                "default": false,
                                "description": "Simulate processing error for testing error handling"
                            }
                        },
                        "additionalProperties": false
                    }
                    """)
                .build();
        
        return Uni.createFrom().item(registration);
    }
    
    @Override
    public Uni<ProcessResponse> registrationCheck(ProcessRequest request) {
        LOG.debugf("TestProcessor registration check requested");
        
        // Create a simple test response to verify the module is functioning
        ProcessResponse.Builder responseBuilder = ProcessResponse.newBuilder()
                .setSuccess(true)
                .addProcessorLogs("TestProcessor: Registration check successful");
        
        // If a test document is provided, echo it back with modifications
        if (request.hasDocument()) {
            PipeDoc doc = request.getDocument();
            
            // Add a marker to show this went through registration check
            Struct.Builder customDataBuilder = doc.hasCustomData() 
                ? doc.getCustomData().toBuilder() 
                : Struct.newBuilder();
            
            customDataBuilder.putFields("registration_check", 
                Value.newBuilder().setStringValue("passed").build());
            customDataBuilder.putFields("module_name", 
                Value.newBuilder().setStringValue(processorName).build());
            
            PipeDoc modifiedDoc = doc.toBuilder()
                .setCustomData(customDataBuilder.build())
                .build();
            
            responseBuilder.setOutputDoc(modifiedDoc);
            responseBuilder.addProcessorLogs("TestProcessor: Test document processed in registration check");
        }
        
        return Uni.createFrom().item(responseBuilder.build());
    }
}