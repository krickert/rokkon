package com.rokkon.pipeline.config.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Pipeline step configuration")
public record PipelineStepConfig(
        @JsonProperty("stepName") @NotBlank String stepName,
        @JsonProperty("stepType") @NotNull StepType stepType,
        @JsonProperty("description") String description,
        @JsonProperty("customConfigSchemaId") String customConfigSchemaId,
        @JsonProperty("customConfig") @Valid JsonConfigOptions customConfig,
        @JsonProperty("kafkaInputs") @Valid List<KafkaInputDefinition> kafkaInputs,
        @JsonProperty("outputs") @Valid Map<String, OutputTarget> outputs,
        @JsonProperty("maxRetries") Integer maxRetries,
        @JsonProperty("retryBackoffMs") Long retryBackoffMs,
        @JsonProperty("maxRetryBackoffMs") Long maxRetryBackoffMs,
        @JsonProperty("retryBackoffMultiplier") Double retryBackoffMultiplier,
        @JsonProperty("stepTimeoutMs") Long stepTimeoutMs,
        @JsonProperty("processorInfo") @NotNull(message = "Processor information (processorInfo) must be provided.") @Valid ProcessorInfo processorInfo
) {

    @JsonCreator
    public PipelineStepConfig(
            @JsonProperty("stepName") String stepName,
            @JsonProperty("stepType") StepType stepType,
            @JsonProperty("description") String description,
            @JsonProperty("customConfigSchemaId") String customConfigSchemaId,
            @JsonProperty("customConfig") JsonConfigOptions customConfig,
            @JsonProperty("kafkaInputs") List<KafkaInputDefinition> kafkaInputs,
            @JsonProperty("outputs") Map<String, OutputTarget> outputs,
            @JsonProperty("maxRetries") Integer maxRetries,
            @JsonProperty("retryBackoffMs") Long retryBackoffMs,
            @JsonProperty("maxRetryBackoffMs") Long maxRetryBackoffMs,
            @JsonProperty("retryBackoffMultiplier") Double retryBackoffMultiplier,
            @JsonProperty("stepTimeoutMs") Long stepTimeoutMs,
            @JsonProperty("processorInfo") ProcessorInfo processorInfo
    ) {
        this.stepName = Objects.requireNonNull(stepName, "stepName cannot be null");
        if (stepName.isBlank()) throw new IllegalArgumentException("stepName cannot be blank");

        this.stepType = Objects.requireNonNull(stepType, "stepType cannot be null");
        this.description = description;
        this.customConfigSchemaId = customConfigSchemaId;
        this.customConfig = customConfig; // Nullable, or provide default if desired

        this.kafkaInputs = (kafkaInputs == null) ? Collections.emptyList() : List.copyOf(kafkaInputs);
        this.outputs = (outputs == null) ? Collections.emptyMap() : Map.copyOf(outputs);
        this.maxRetries = (maxRetries == null || maxRetries < 0) ? 0 : maxRetries;
        this.retryBackoffMs = (retryBackoffMs == null || retryBackoffMs < 0) ? 1000L : retryBackoffMs;
        this.maxRetryBackoffMs = (maxRetryBackoffMs == null || maxRetryBackoffMs < 0) ? 30000L : maxRetryBackoffMs;
        this.retryBackoffMultiplier = (retryBackoffMultiplier == null || retryBackoffMultiplier <= 0) ? 2.0 : retryBackoffMultiplier;
        this.stepTimeoutMs = (stepTimeoutMs == null || stepTimeoutMs < 0) ? null : stepTimeoutMs;
        this.processorInfo = Objects.requireNonNull(processorInfo, "processorInfo cannot be null");

        // ProcessorInfo validation
        if (this.processorInfo.grpcServiceName() != null && !this.processorInfo.grpcServiceName().isBlank() &&
                this.processorInfo.internalProcessorBeanName() != null && !this.processorInfo.internalProcessorBeanName().isBlank()) {
            throw new IllegalArgumentException("ProcessorInfo cannot have both grpcServiceName and internalProcessorBeanName set.");
        }
        if ((this.processorInfo.grpcServiceName() == null || this.processorInfo.grpcServiceName().isBlank()) &&
                (this.processorInfo.internalProcessorBeanName() == null || this.processorInfo.internalProcessorBeanName().isBlank())) {
            throw new IllegalArgumentException("ProcessorInfo must have either grpcServiceName or internalProcessorBeanName set.");
        }
    }

    // Convenience constructors
    public PipelineStepConfig(
            String stepName,
            StepType stepType,
            String description,
            String customConfigSchemaId,
            JsonConfigOptions customConfig,
            Map<String, OutputTarget> outputs,
            Integer maxRetries,
            Long retryBackoffMs,
            Long maxRetryBackoffMs,
            Double retryBackoffMultiplier,
            Long stepTimeoutMs,
            ProcessorInfo processorInfo
    ) {
        this(
                stepName,
                stepType,
                description,
                customConfigSchemaId,
                customConfig,
                Collections.emptyList(), // Default for kafkaInputs
                outputs,
                maxRetries,
                retryBackoffMs,
                maxRetryBackoffMs,
                retryBackoffMultiplier,
                stepTimeoutMs,
                processorInfo
        );
    }

    public PipelineStepConfig(
            String stepName,
            StepType stepType,
            ProcessorInfo processorInfo,
            JsonConfigOptions customConfig,
            String customConfigSchemaId
    ) {
        this(
                stepName,
                stepType,
                "Test Description for " + stepName, // default description
                customConfigSchemaId,
                customConfig,
                Collections.emptyList(), // default kafkaInputs
                Collections.emptyMap(),  // default outputs
                0,       // default maxRetries
                1000L,   // default retryBackoffMs
                30000L,  // default maxRetryBackoffMs
                2.0,     // default retryBackoffMultiplier
                null,    // default stepTimeoutMs
                processorInfo
        );
    }

    public PipelineStepConfig(
            String stepName,
            StepType stepType,
            ProcessorInfo processorInfo,
            JsonConfigOptions customConfig
    ) {
        this(stepName, stepType, processorInfo, customConfig, null);
    }

    public PipelineStepConfig(
            String stepName,
            StepType stepType,
            ProcessorInfo processorInfo
    ) {
        this(
                stepName,
                stepType,
                processorInfo,
                new JsonConfigOptions(JsonNodeFactory.instance.objectNode(), Collections.emptyMap()),
                null
        );
    }

    // Inner Records
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "JSON configuration options")
    public record JsonConfigOptions(
            @JsonProperty("jsonConfig") JsonNode jsonConfig,
            @JsonProperty("configParams") Map<String, String> configParams
    ) {
        @JsonCreator
        public JsonConfigOptions(
                @JsonProperty("jsonConfig") JsonNode jsonConfig,
                @JsonProperty("configParams") Map<String, String> configParams
        ) {
            this.jsonConfig = jsonConfig; // Can be null if not provided in JSON
            this.configParams = (configParams == null) ? Collections.emptyMap() : Map.copyOf(configParams);
        }

        // Convenience constructors
        public JsonConfigOptions(JsonNode jsonNode) {
            this(jsonNode, Collections.emptyMap());
        }

        public JsonConfigOptions(Map<String, String> configParams) {
            this(null, configParams);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Output target configuration")
    public record OutputTarget(
            @JsonProperty("targetStepName") @NotBlank String targetStepName,
            @JsonProperty("transportType") @NotNull TransportType transportType,
            @JsonProperty("grpcTransport") @Valid GrpcTransportConfig grpcTransport,
            @JsonProperty("kafkaTransport") @Valid KafkaTransportConfig kafkaTransport
    ) {
        @JsonCreator
        public OutputTarget(
                @JsonProperty("targetStepName") String targetStepName,
                @JsonProperty("transportType") TransportType transportType,
                @JsonProperty("grpcTransport") GrpcTransportConfig grpcTransport,
                @JsonProperty("kafkaTransport") KafkaTransportConfig kafkaTransport
        ) {
            this.targetStepName = Objects.requireNonNull(targetStepName, "targetStepName cannot be null");
            if (this.targetStepName.isBlank()) throw new IllegalArgumentException("targetStepName cannot be blank");

            this.transportType = (transportType == null) ? TransportType.GRPC : transportType;
            this.grpcTransport = grpcTransport;
            this.kafkaTransport = kafkaTransport;

            if (this.transportType == TransportType.KAFKA && this.kafkaTransport == null) {
                throw new IllegalArgumentException("OutputTarget: KafkaTransportConfig must be provided when transportType is KAFKA for targetStepName '" + targetStepName + "'.");
            }
            if (this.transportType != TransportType.KAFKA && this.kafkaTransport != null) {
                throw new IllegalArgumentException("OutputTarget: KafkaTransportConfig should only be provided when transportType is KAFKA (found type: " + this.transportType + ") for targetStepName '" + targetStepName + "'.");
            }
            if (this.transportType == TransportType.GRPC && this.grpcTransport == null) {
                throw new IllegalArgumentException("OutputTarget: GrpcTransportConfig must be provided when transportType is GRPC for targetStepName '" + targetStepName + "'.");
            }
            if (this.transportType != TransportType.GRPC && this.grpcTransport != null) {
                throw new IllegalArgumentException("OutputTarget: GrpcTransportConfig should only be provided when transportType is GRPC (found type: " + this.transportType + ") for targetStepName '" + targetStepName + "'.");
            }
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Processor information")
    public record ProcessorInfo(
            @JsonProperty("grpcServiceName") String grpcServiceName,
            @JsonProperty("internalProcessorBeanName") String internalProcessorBeanName
    ) {
        @JsonCreator
        public ProcessorInfo(
                @JsonProperty("grpcServiceName") String grpcServiceName,
                @JsonProperty("internalProcessorBeanName") String internalProcessorBeanName
        ) {
            boolean grpcSet = grpcServiceName != null && !grpcServiceName.isBlank();
            boolean beanSet = internalProcessorBeanName != null && !internalProcessorBeanName.isBlank();

            if (grpcSet && beanSet) {
                throw new IllegalArgumentException("ProcessorInfo cannot have both grpcServiceName and internalProcessorBeanName set.");
            }
            if (!grpcSet && !beanSet) {
                throw new IllegalArgumentException("ProcessorInfo must have either grpcServiceName or internalProcessorBeanName set.");
            }
            this.grpcServiceName = grpcServiceName;
            this.internalProcessorBeanName = internalProcessorBeanName;
        }
    }
}