package com.rokkon.pipeline.validation.validators;

import com.rokkon.pipeline.config.model.*;
import com.rokkon.pipeline.validation.PipelineConfigValidator;
import com.rokkon.pipeline.validation.ValidationResult;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates output routing configuration in pipeline steps.
 * Ensures that output routes are properly configured and referenced steps exist.
 */
@ApplicationScoped
public class OutputRoutingValidator implements PipelineConfigValidator {

    @Override
    public ValidationResult validate(PipelineConfig config) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (config == null || config.pipelineSteps() == null) {
            errors.add("Pipeline configuration or steps cannot be null");
            return new ValidationResult(false, errors, warnings);
        }

        Set<String> stepIds = config.pipelineSteps().keySet();

        for (var entry : config.pipelineSteps().entrySet()) {
            String stepId = entry.getKey();
            PipelineStepConfig step = entry.getValue();
            validateStepOutputs(stepId, step, stepIds, errors, warnings);
        }

        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }

    private void validateStepOutputs(String stepId, PipelineStepConfig step, 
                                   Set<String> allStepIds, List<String> errors, List<String> warnings) {
        if (step == null || step.outputs() == null) {
            return;
        }

        String prefix = String.format("Step '%s'", stepId);

        // Check if step has outputs defined
        if (step.outputs().isEmpty() && step.stepType() != StepType.SINK) {
            warnings.add(String.format("%s: No outputs defined for non-SINK step", prefix));
        }

        // SINK steps should not have outputs
        if (step.stepType() == StepType.SINK && !step.outputs().isEmpty()) {
            errors.add(String.format("%s: SINK steps should not have outputs", prefix));
        }

        // Validate each output
        for (var outputEntry : step.outputs().entrySet()) {
            String outputName = outputEntry.getKey();
            PipelineStepConfig.OutputTarget output = outputEntry.getValue();
            
            validateOutput(prefix, outputName, output, allStepIds, errors, warnings);
        }

        // Check for duplicate output names
        validateDuplicateOutputs(prefix, step, errors);

        // Warn if only one output but it's not named "default"
        if (step.outputs().size() == 1 && !step.outputs().containsKey("default")) {
            warnings.add(String.format("%s: Single output should be named 'default' for clarity", prefix));
        }
    }

    private void validateOutput(String stepPrefix, String outputName, 
                               PipelineStepConfig.OutputTarget output,
                               Set<String> allStepIds, List<String> errors, List<String> warnings) {
        if (output == null) {
            errors.add(String.format("%s output '%s': Output target cannot be null", stepPrefix, outputName));
            return;
        }

        String outputPrefix = String.format("%s output '%s'", stepPrefix, outputName);

        // Validate target step exists (if specified)
        if (output.targetStepName() != null && !output.targetStepName().isEmpty()) {
            if (!allStepIds.contains(output.targetStepName())) {
                errors.add(String.format("%s: Target step '%s' does not exist in pipeline", 
                          outputPrefix, output.targetStepName()));
            }
        }

        // Validate transport type
        if (output.transportType() == null) {
            errors.add(String.format("%s: Transport type must be specified", outputPrefix));
            return;
        }

        // Validate transport configuration matches transport type
        switch (output.transportType()) {
            case KAFKA:
                if (output.kafkaTransport() == null) {
                    errors.add(String.format("%s: Kafka transport config required for KAFKA transport type", 
                              outputPrefix));
                } else {
                    validateKafkaTransport(outputPrefix, output.kafkaTransport(), errors, warnings);
                }
                if (output.grpcTransport() != null) {
                    warnings.add(String.format("%s: gRPC config specified but transport type is KAFKA", 
                                outputPrefix));
                }
                break;
                
            case GRPC:
                if (output.grpcTransport() == null) {
                    errors.add(String.format("%s: gRPC transport config required for GRPC transport type", 
                              outputPrefix));
                } else {
                    validateGrpcTransport(outputPrefix, output.grpcTransport(), errors, warnings);
                }
                if (output.kafkaTransport() != null) {
                    warnings.add(String.format("%s: Kafka config specified but transport type is GRPC", 
                                outputPrefix));
                }
                break;
        }
    }

    private void validateKafkaTransport(String prefix, KafkaTransportConfig config, 
                                      List<String> errors, List<String> warnings) {
        if (config.topic() == null || config.topic().isEmpty()) {
            errors.add(String.format("%s: Kafka topic must be specified", prefix));
        }

        // Validate batch settings
        if (config.batchSize() != null && config.batchSize() <= 0) {
            errors.add(String.format("%s: Batch size must be positive (was %d)", prefix, config.batchSize()));
        }

        if (config.lingerMs() != null && config.lingerMs() < 0) {
            errors.add(String.format("%s: Linger ms cannot be negative (was %d)", prefix, config.lingerMs()));
        }
    }

    private void validateGrpcTransport(String prefix, GrpcTransportConfig config, 
                                     List<String> errors, List<String> warnings) {
        if (config.serviceName() == null || config.serviceName().isEmpty()) {
            errors.add(String.format("%s: gRPC service name must be specified", prefix));
        }

        // GrpcTransportConfig only has serviceName and grpcClientProperties
        // Validate timeout if specified in properties
        if (config.grpcClientProperties() != null && config.grpcClientProperties().containsKey("timeout")) {
            String timeoutStr = config.grpcClientProperties().get("timeout");
            try {
                long timeout = Long.parseLong(timeoutStr);
                if (timeout <= 0) {
                    errors.add(String.format("%s: Timeout must be positive (was %s ms)", prefix, timeoutStr));
                }
            } catch (NumberFormatException e) {
                errors.add(String.format("%s: Timeout must be a valid number (was '%s')", prefix, timeoutStr));
            }
        }
    }

    private void validateDuplicateOutputs(String prefix, PipelineStepConfig step, List<String> errors) {
        // Check for case-insensitive duplicates
        Set<String> lowerCaseNames = new HashSet<>();
        for (String outputName : step.outputs().keySet()) {
            if (!lowerCaseNames.add(outputName.toLowerCase())) {
                errors.add(String.format("%s: Duplicate output name '%s' (case-insensitive)", 
                          prefix, outputName));
            }
        }
    }

    @Override
    public int getPriority() {
        return 80;
    }

    @Override
    public String getValidatorName() {
        return "OutputRoutingValidator";
    }
}