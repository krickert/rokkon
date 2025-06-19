package com.rokkon.pipeline.validation.validators;

import com.rokkon.pipeline.config.model.PipelineConfig;
import com.rokkon.pipeline.config.model.PipelineStepConfig;
import com.rokkon.pipeline.config.model.StepType;
import com.rokkon.pipeline.validation.PipelineConfigValidator;
import com.rokkon.pipeline.validation.ValidationResult;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates step types and their constraints.
 * Ensures proper pipeline structure with correct step types.
 */
@ApplicationScoped
public class StepTypeValidator implements PipelineConfigValidator {
    
    @Override
    public ValidationResult validate(PipelineConfig config) {
        if (config == null || config.pipelineSteps() == null || config.pipelineSteps().isEmpty()) {
            return new ValidationResult(true, List.of(), List.of());
        }
        
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        int initialPipelineCount = 0;
        int sinkCount = 0;
        
        for (var entry : config.pipelineSteps().entrySet()) {
            String stepId = entry.getKey();
            PipelineStepConfig step = entry.getValue();
            
            if (step == null || step.stepType() == null) {
                continue; // Let RequiredFieldsValidator handle this
            }
            
            StepType stepType = step.stepType();
            
            // Count step types
            if (stepType == StepType.INITIAL_PIPELINE) {
                initialPipelineCount++;
            } else if (stepType == StepType.SINK) {
                sinkCount++;
            }
            
            // Validate step type constraints
            validateStepTypeConstraints(stepId, step, errors, warnings);
        }
        
        // Only validate pipeline structure constraints if there are steps
        // Empty pipelines are valid initial states
        if (config.pipelineSteps() != null && !config.pipelineSteps().isEmpty()) {
            if (initialPipelineCount == 0) {
                errors.add("Pipeline must have at least one INITIAL_PIPELINE step");
            }
            
            if (initialPipelineCount > 1) {
                errors.add("Pipeline can have at most one INITIAL_PIPELINE step, found " + initialPipelineCount);
            }
            
            if (sinkCount == 0) {
                errors.add("Pipeline must have at least one SINK step");
            }
        }
        
        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }
    
    private void validateStepTypeConstraints(String stepId, PipelineStepConfig step, 
                                            List<String> errors, List<String> warnings) {
        String stepPrefix = "Step '" + stepId + "': ";
        StepType stepType = step.stepType();
        
        switch (stepType) {
            case INITIAL_PIPELINE:
                // Initial pipeline steps should not have Kafka inputs (they start the pipeline)
                if (step.kafkaInputs() != null && !step.kafkaInputs().isEmpty()) {
                    errors.add(stepPrefix + "INITIAL_PIPELINE steps should not have Kafka inputs");
                }
                
                // Initial pipeline steps must have outputs
                if (step.outputs() == null || step.outputs().isEmpty()) {
                    errors.add(stepPrefix + "INITIAL_PIPELINE steps must have at least one output");
                }
                break;
                
            case SINK:
                // Sink steps should not have outputs (they end the pipeline)
                if (step.outputs() != null && !step.outputs().isEmpty()) {
                    errors.add(stepPrefix + "SINK steps should not have outputs");
                }
                
                // Sink steps should have inputs
                if (step.kafkaInputs() == null || step.kafkaInputs().isEmpty()) {
                    warnings.add(stepPrefix + "SINK steps typically have inputs to process");
                }
                break;
                
            case PIPELINE:
                // Regular pipeline steps should have both inputs and outputs
                if (step.kafkaInputs() == null || step.kafkaInputs().isEmpty()) {
                    warnings.add(stepPrefix + "PIPELINE steps typically have inputs");
                }
                
                if (step.outputs() == null || step.outputs().isEmpty()) {
                    warnings.add(stepPrefix + "PIPELINE steps typically have outputs");
                }
                break;
        }
    }
    
    @Override
    public int getPriority() {
        return 300; // Run after basic validation
    }
    
    @Override
    public String getValidatorName() {
        return "StepTypeValidator";
    }
}