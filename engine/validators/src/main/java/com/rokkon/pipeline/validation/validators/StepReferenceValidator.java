package com.rokkon.pipeline.validation.validators;

import com.rokkon.pipeline.config.model.PipelineConfig;
import com.rokkon.pipeline.config.model.PipelineStepConfig;
import com.rokkon.pipeline.config.model.TransportType;
import com.rokkon.pipeline.validation.PipelineConfigValidator;
import com.rokkon.pipeline.validation.DefaultValidationResult;
import com.rokkon.pipeline.validation.DELET_ME_I_SHOULD_USE_INTERFACE_OR_MOCK_OR_DEFAULT_ValidationResult;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.*;

/**
 * Validates that all step references point to existing steps within the pipeline.
 * Checks for duplicate step names and validates internal gRPC references.
 */
@ApplicationScoped
public class StepReferenceValidator implements PipelineConfigValidator {
    
    @Override
    public DELET_ME_I_SHOULD_USE_INTERFACE_OR_MOCK_OR_DEFAULT_ValidationResult validate(PipelineConfig config) {
        if (config == null || config.pipelineSteps() == null || config.pipelineSteps().isEmpty()) {
            return new DefaultValidationResult(true, List.of(), List.of());
        }
        
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Collect all valid step IDs
        Set<String> validStepIds = new HashSet<>(config.pipelineSteps().keySet());
        
        // Check for duplicate step names
        Set<String> stepNames = new HashSet<>();
        for (var entry : config.pipelineSteps().entrySet()) {
            String stepId = entry.getKey();
            PipelineStepConfig step = entry.getValue();
            
            if (step != null && step.stepName() != null && !step.stepName().isBlank()) {
                if (!stepNames.add(step.stepName())) {
                    errors.add("Duplicate step name found: " + step.stepName());
                }
            }
        }
        
        // Validate all references
        for (var entry : config.pipelineSteps().entrySet()) {
            String stepId = entry.getKey();
            PipelineStepConfig step = entry.getValue();
            
            if (step != null && step.outputs() != null) {
                for (var outputEntry : step.outputs().entrySet()) {
                    String outputKey = outputEntry.getKey();
                    var output = outputEntry.getValue();
                    
                    if (output != null && 
                        output.transportType() == TransportType.GRPC && 
                        output.grpcTransport() != null && 
                        output.grpcTransport().serviceName() != null &&
                        !output.grpcTransport().serviceName().isBlank()) {
                        
                        String targetService = output.grpcTransport().serviceName();
                        
                        // Check if this looks like an internal reference (no dots, suggesting it's not a FQDN)
                        if (!targetService.contains(".") && !validStepIds.contains(targetService)) {
                            errors.add("Step '" + stepId + "' output '" + outputKey + 
                                     "' references non-existent target step '" + targetService + "'");
                        }
                    }
                }
            }
        }
        
        return new DefaultValidationResult(errors.isEmpty(), errors, warnings);
    }
    
    @Override
    public int getPriority() {
        return 400; // Run after structural validation
    }
    
    @Override
    public String getValidatorName() {
        return "StepReferenceValidator";
    }
}