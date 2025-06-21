package com.rokkon.pipeline.engine.service;

import com.rokkon.pipeline.config.model.*;
import com.rokkon.pipeline.consul.service.PipelineConfigService;
import com.rokkon.pipeline.engine.util.JsonProtoConverter;
import com.rokkon.search.engine.ProcessResponse;
import com.rokkon.search.engine.ProcessStatus;
import com.rokkon.search.model.*;
import com.rokkon.search.sdk.ProcessConfiguration;
import com.rokkon.search.sdk.ProcessRequest;
import com.rokkon.search.sdk.ServiceMetadata;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core service that executes pipelines by routing documents through configured steps.
 * This is the main orchestration service that:
 * - Reads pipeline configurations from Consul
 * - Discovers services via Consul
 * - Routes documents between steps using gRPC (and eventually Kafka)
 * - Tracks execution state
 * - Handles errors and retries
 */
@ApplicationScoped
public class PipelineExecutorService {

    private static final Logger LOG = LoggerFactory.getLogger(PipelineExecutorService.class);

    @ConfigProperty(name = "rokkon.cluster.name", defaultValue = "default-cluster")
    String clusterName;

    @Inject
    PipelineConfigService pipelineConfigService;

    @Inject
    EventDrivenRouter router;

    // Track active executions for monitoring
    private final Map<String, PipeStreamExecutionContext> activeExecutions = new ConcurrentHashMap<>();

    /**
     * Main entry point for pipeline execution.
     * Creates a PipeStream and executes it through the configured pipeline.
     */
    public Uni<ProcessResponse> executePipeline(String pipelineName, PipeDoc document, ActionType actionType) {
        LOG.info("Starting pipeline execution: pipeline={}, documentId={}, action={}", 
                pipelineName, document.getId(), actionType);

        String streamId = UUID.randomUUID().toString();
        
        // Create initial PipeStream
        PipeStream.Builder streamBuilder = PipeStream.newBuilder()
                .setStreamId(streamId)
                .setDocument(document)
                .setCurrentPipelineName(pipelineName)
                .setCurrentHopNumber(0)
                .setActionType(actionType);

        // Add correlation ID for tracing
        streamBuilder.putContextParams("correlation_id", streamId);
        streamBuilder.putContextParams("start_time", Instant.now().toString());

        PipeStream initialStream = streamBuilder.build();

        // Load pipeline configuration using configured cluster name
        return pipelineConfigService.getPipeline(clusterName, pipelineName)
            .onItem().transform(opt -> opt.orElse(null))
            .onItem().ifNull().failWith(() -> 
                new IllegalArgumentException("Pipeline not found: " + pipelineName))
            .flatMap(config -> {
                    // Find the initial step
                    String initialStepName = findInitialStep(config);
                    if (initialStepName == null) {
                        return Uni.createFrom().failure(
                            new IllegalStateException("No initial step found in pipeline: " + pipelineName));
                    }

                    // Create execution context
                    PipeStreamExecutionContext context = new PipeStreamExecutionContext(
                        streamId, pipelineName, config, initialStream);
                    activeExecutions.put(streamId, context);

                    // Start execution
                    return executeStream(context, initialStepName)
                            .onTermination().invoke(() -> activeExecutions.remove(streamId));
                });
    }

    /**
     * Execute a stream through the pipeline starting from the specified step.
     */
    private Uni<ProcessResponse> executeStream(PipeStreamExecutionContext context, String nextStepName) {
        LOG.debug("Executing stream {} at step {}", context.streamId, nextStepName);

        PipelineStepConfig stepConfig = context.pipelineConfig.pipelineSteps().get(nextStepName);
        if (stepConfig == null) {
            return Uni.createFrom().failure(
                new IllegalStateException("Step not found in pipeline: " + nextStepName));
        }

        // Update stream with target step
        PipeStream updatedStream = context.currentStream.toBuilder()
                .setTargetStepName(nextStepName)
                .setCurrentHopNumber(context.currentStream.getCurrentHopNumber() + 1)
                .build();
        context.currentStream = updatedStream;

        // Execute based on step type
        if (stepConfig.stepType() == null) {
            return Uni.createFrom().failure(
                new IllegalStateException("Step type not set for: " + nextStepName));
        }
        
        return switch (stepConfig.stepType()) {
            case INITIAL_PIPELINE -> executeInitialStep(context, stepConfig);
            case PIPELINE -> executePipelineStep(context, stepConfig);
            case SINK -> executeSinkStep(context, stepConfig);
        };
    }

    /**
     * Execute an INITIAL_PIPELINE step (entry point).
     */
    private Uni<ProcessResponse> executeInitialStep(PipeStreamExecutionContext context, 
                                                   PipelineStepConfig stepConfig) {
        LOG.debug("Executing INITIAL_PIPELINE step: {}", stepConfig.stepName());
        
        // Initial steps don't call modules, they just route to the next step
        String nextStep = determineNextStep(stepConfig, context.currentStream);
        if (nextStep != null) {
            return executeStream(context, nextStep);
        }
        
        // No next step means pipeline complete
        return createSuccessResponse(context);
    }

    /**
     * Execute a PIPELINE step (calls a module).
     */
    private Uni<ProcessResponse> executePipelineStep(PipeStreamExecutionContext context, 
                                                        PipelineStepConfig stepConfig) {
        LOG.debug("Executing PIPELINE step: {}", stepConfig.stepName());

        PipelineStepConfig.ProcessorInfo processorInfo = stepConfig.processorInfo();
        if (processorInfo == null || processorInfo.grpcServiceName() == null || processorInfo.grpcServiceName().isBlank()) {
            return Uni.createFrom().failure(
                new IllegalStateException("No gRPC service configured for step: " + stepConfig.stepName()));
        }
        String moduleName = processorInfo.grpcServiceName();

        // Record step start
        long startTime = System.currentTimeMillis();

        // Build ProcessRequest for the module
        ProcessRequest request = buildProcessRequest(context, stepConfig);

        // Route to module using appropriate transport
        return router.routeRequest(request, stepConfig)
                .flatMap(processResponse -> {
                    // Record execution history
                    long endTime = System.currentTimeMillis();
                    StepExecutionRecord record = createExecutionRecord(
                        context, stepConfig, startTime, endTime, processResponse);
                    
                    context.currentStream = context.currentStream.toBuilder()
                            .addHistory(record)
                            .build();

                    // Update document if modified
                    if (processResponse.hasOutputDoc()) {
                        context.currentStream = context.currentStream.toBuilder()
                                .setDocument(processResponse.getOutputDoc())
                                .build();
                    }

                    // Determine next step
                    String nextStep = determineNextStep(stepConfig, context.currentStream);
                    if (nextStep != null) {
                        return executeStream(context, nextStep);
                    }
                    
                    return createSuccessResponse(context);
                })
                .onFailure().retry()
                    .withBackOff(Duration.ofSeconds(1), Duration.ofSeconds(10))
                    .atMost(3)
                .onFailure().recoverWithUni(error -> {
                    // Record error and potentially route to error handling
                    LOG.error("Failed to execute step {} for stream {}", 
                            stepConfig.stepName(), context.streamId, error);
                    
                    ErrorData errorData = createErrorData(stepConfig, error);
                    context.currentStream = context.currentStream.toBuilder()
                            .setStreamErrorData(errorData)
                            .build();
                    
                    return createErrorResponse(context, error);
                });
    }

    /**
     * Execute a SINK step (terminal step).
     */
    private Uni<ProcessResponse> executeSinkStep(PipeStreamExecutionContext context, 
                                                PipelineStepConfig stepConfig) {
        LOG.debug("Executing SINK step: {}", stepConfig.stepName());
        
        // For now, sinks work like pipeline steps but don't route to next steps
        return executePipelineStep(context, stepConfig);
    }

    /**
     * Build ProcessRequest for module invocation.
     */
    private ProcessRequest buildProcessRequest(PipeStreamExecutionContext context, 
                                             PipelineStepConfig stepConfig) {
        // Build service metadata
        ServiceMetadata.Builder metadataBuilder = ServiceMetadata.newBuilder()
                .setPipelineName(context.pipelineName)
                .setPipeStepName(stepConfig.stepName())
                .setStreamId(context.streamId)
                .setCurrentHopNumber(context.currentStream.getCurrentHopNumber());

        // Add execution history
        metadataBuilder.addAllHistory(context.currentStream.getHistoryList());

        // Add context params
        metadataBuilder.putAllContextParams(context.currentStream.getContextParamsMap());

        // Add any existing stream error
        if (context.currentStream.hasStreamErrorData()) {
            metadataBuilder.setStreamErrorData(context.currentStream.getStreamErrorData());
        }

        // Build process configuration
        ProcessConfiguration.Builder configBuilder = ProcessConfiguration.newBuilder();
        
        // Add custom JSON config if present
        if (stepConfig.customConfig() != null && stepConfig.customConfig().jsonConfig() != null) {
            configBuilder.setCustomJsonConfig(
                JsonProtoConverter.jsonNodeToStruct(stepConfig.customConfig().jsonConfig())
            );
        }

        // Add config params
        Map<String, String> configParams = new HashMap<>();
        if (stepConfig.customConfig() != null && stepConfig.customConfig().configParams() != null) {
            configParams.putAll(stepConfig.customConfig().configParams());
        }
        if (!configParams.isEmpty()) {
            configBuilder.putAllConfigParams(configParams);
        }

        // Build the request
        return ProcessRequest.newBuilder()
                .setDocument(context.currentStream.getDocument())
                .setConfig(configBuilder.build())
                .setMetadata(metadataBuilder.build())
                .build();
    }

    /**
     * Determine the next step based on routing configuration.
     * Uses the event-driven router to handle all transports.
     */
    private String determineNextStep(PipelineStepConfig currentStep, PipeStream stream) {
        if (currentStep.outputs() == null || currentStep.outputs().isEmpty()) {
            return null;
        }

        // Route to all configured outputs using the event-driven router
        router.routeStream(stream, currentStep)
            .subscribe().with(
                result -> {
                    if (result.success()) {
                        LOG.debug("Successfully routed to {} via {}", 
                            result.targetStepName(), result.transportType());
                    } else {
                        LOG.error("Failed to route to {} via {}: {}", 
                            result.targetStepName(), result.transportType(), result.message());
                    }
                },
                error -> LOG.error("Error in routing stream", error)
            );

        // Find the next synchronous (gRPC) step to continue pipeline execution
        return currentStep.outputs().values().stream()
            .filter(output -> output.transportType() == TransportType.GRPC)
            .map(PipelineStepConfig.OutputTarget::targetStepName)
            .findFirst()
            .orElse(null);
    }

    /**
     * Find the initial step in the pipeline.
     */
    private String findInitialStep(PipelineConfig config) {
        if (config.pipelineSteps() == null) {
            return null;
        }

        // Look for INITIAL_PIPELINE type
        for (Map.Entry<String, PipelineStepConfig> entry : config.pipelineSteps().entrySet()) {
            if (entry.getValue().stepType() == StepType.INITIAL_PIPELINE) {
                return entry.getKey();
            }
        }

        // If no INITIAL_PIPELINE, just get the first step
        return config.pipelineSteps().keySet().stream().findFirst().orElse(null);
    }

    /**
     * Create execution record for history.
     */
    private StepExecutionRecord createExecutionRecord(PipeStreamExecutionContext context,
                                                     PipelineStepConfig stepConfig,
                                                     long startTime, long endTime,
                                                     com.rokkon.search.sdk.ProcessResponse processResponse) {
        StepExecutionRecord.Builder recordBuilder = StepExecutionRecord.newBuilder()
                .setHopNumber(context.currentStream.getCurrentHopNumber())
                .setStepName(stepConfig.stepName())
                .setStartTime(com.google.protobuf.Timestamp.newBuilder()
                        .setSeconds(startTime / 1000)
                        .build())
                .setEndTime(com.google.protobuf.Timestamp.newBuilder()
                        .setSeconds(endTime / 1000)
                        .build())
                .setStatus(processResponse.getSuccess() ? "SUCCESS" : "FAILURE");

        // Add processor logs
        if (processResponse.getProcessorLogsCount() > 0) {
            recordBuilder.addAllProcessorLogs(processResponse.getProcessorLogsList());
        }

        // Add error info if failed
        if (!processResponse.getSuccess() && processResponse.hasErrorDetails()) {
            // Convert error details to ErrorData
            ErrorData errorData = ErrorData.newBuilder()
                    .setErrorMessage("Module processing failed")
                    .setOriginatingStepName(stepConfig.stepName())
                    .setTimestamp(recordBuilder.getEndTime())
                    .build();
            recordBuilder.setErrorInfo(errorData);
        }

        return recordBuilder.build();
    }

    /**
     * Create error data from exception.
     */
    private ErrorData createErrorData(PipelineStepConfig stepConfig, Throwable error) {
        return ErrorData.newBuilder()
                .setErrorMessage(error.getMessage() != null ? error.getMessage() : "Unknown error")
                .setErrorCode(error.getClass().getSimpleName())
                .setTechnicalDetails(error.toString())
                .setOriginatingStepName(stepConfig.stepName())
                .setTimestamp(com.google.protobuf.Timestamp.newBuilder()
                        .setSeconds(System.currentTimeMillis() / 1000)
                        .build())
                .build();
    }

    /**
     * Create success response.
     */
    private Uni<ProcessResponse> createSuccessResponse(PipeStreamExecutionContext context) {
        return Uni.createFrom().item(ProcessResponse.newBuilder()
                .setStreamId(context.streamId)
                .setStatus(ProcessStatus.ACCEPTED)
                .setMessage("Pipeline execution completed successfully")
                .setRequestId(context.streamId)
                .setTimestamp(System.currentTimeMillis())
                .build());
    }

    /**
     * Create error response.
     */
    private Uni<ProcessResponse> createErrorResponse(PipeStreamExecutionContext context, Throwable error) {
        return Uni.createFrom().item(ProcessResponse.newBuilder()
                .setStreamId(context.streamId)
                .setStatus(ProcessStatus.ERROR)
                .setMessage("Pipeline execution failed: " + error.getMessage())
                .setRequestId(context.streamId)
                .setTimestamp(System.currentTimeMillis())
                .build());
    }

    /**
     * Execution context for tracking state during pipeline execution.
     */
    private static class PipeStreamExecutionContext {
        final String streamId;
        final String pipelineName;
        final PipelineConfig pipelineConfig;
        PipeStream currentStream;

        PipeStreamExecutionContext(String streamId, String pipelineName, 
                                  PipelineConfig pipelineConfig, PipeStream initialStream) {
            this.streamId = streamId;
            this.pipelineName = pipelineName;
            this.pipelineConfig = pipelineConfig;
            this.currentStream = initialStream;
        }
    }
}