package com.rokkon.search.config.pipeline.service.validation.rules;

import com.rokkon.search.config.pipeline.model.PipelineConfig;
import com.rokkon.search.config.pipeline.model.PipelineStepConfig;
import com.rokkon.search.config.pipeline.service.validation.StructuralValidationRule;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates that all required fields are present and non-empty.
 * Migrated from the old validation system but adapted for new Record models.
 */
public class RequiredFieldsValidator implements StructuralValidationRule {
    
    @Override
    public List<String> validate(String pipelineId, PipelineConfig config) {
        List<String> errors = new ArrayList<>();
        
        // Validate pipeline name
        if (config.name() == null || config.name().isBlank()) {
            errors.add("Pipeline name is required");
        }
        
        // Validate pipeline steps
        if (config.pipelineSteps() == null || config.pipelineSteps().isEmpty()) {
            errors.add("Pipeline must have at least one step");
        } else {
            for (var entry : config.pipelineSteps().entrySet()) {
                String stepId = entry.getKey();
                PipelineStepConfig step = entry.getValue();
                
                validateStep(stepId, step, errors);
            }
        }
        
        return errors;
    }
    
    private void validateStep(String stepId, PipelineStepConfig step, List<String> errors) {
        String stepPrefix = "Step '" + stepId + "': ";
        
        if (step == null) {
            errors.add(stepPrefix + "Step configuration is null");
            return;
        }
        
        // Validate step name
        if (step.stepName() == null || step.stepName().isBlank()) {
            errors.add(stepPrefix + "Step name is required");
        }
        
        // Validate step type
        if (step.stepType() == null) {
            errors.add(stepPrefix + "Step type is required");
        }
        
        // Validate processor info
        if (step.processorInfo() == null) {
            errors.add(stepPrefix + "Processor info is required");
        } else {
            // Either gRPC service name or internal processor bean name should be provided
            boolean hasGrpcService = step.processorInfo().grpcServiceName() != null && 
                                   !step.processorInfo().grpcServiceName().isBlank();
            boolean hasInternalBean = step.processorInfo().internalProcessorBeanName() != null && 
                                    !step.processorInfo().internalProcessorBeanName().isBlank();
            
            if (!hasGrpcService && !hasInternalBean) {
                errors.add(stepPrefix + "Processor info must specify either grpcServiceName or internalProcessorBeanName");
            }
        }
        
        // Validate outputs format
        if (step.outputs() != null) {
            for (var outputEntry : step.outputs().entrySet()) {
                String outputKey = outputEntry.getKey();
                var output = outputEntry.getValue();
                
                if (output == null) {
                    errors.add(stepPrefix + "Output '" + outputKey + "' configuration is null");
                    continue;
                }
                
                if (output.transportType() == null) {
                    errors.add(stepPrefix + "Output '" + outputKey + "' transport type is required");
                }
            }
        }
        
        // Validate retry configuration
        if (step.maxRetries() != null && step.maxRetries() < 0) {
            errors.add(stepPrefix + "Max retries cannot be negative");
        }
        
        if (step.retryBackoffMs() != null && step.retryBackoffMs() < 0) {
            errors.add(stepPrefix + "Retry backoff cannot be negative");
        }
        
        if (step.stepTimeoutMs() != null && step.stepTimeoutMs() <= 0) {
            errors.add(stepPrefix + "Step timeout must be positive");
        }
        
        if (step.retryBackoffMultiplier() != null && step.retryBackoffMultiplier() <= 0) {
            errors.add(stepPrefix + "Retry backoff multiplier must be positive");
        }
    }
    
    @Override
    public int getPriority() {
        return 10; // High priority - run early
    }
}