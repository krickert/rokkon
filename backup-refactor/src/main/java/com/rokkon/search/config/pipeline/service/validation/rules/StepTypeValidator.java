package com.rokkon.search.config.pipeline.service.validation.rules;

import com.rokkon.search.config.pipeline.model.PipelineConfig;
import com.rokkon.search.config.pipeline.model.PipelineStepConfig;
import com.rokkon.search.config.pipeline.model.StepType;
import com.rokkon.search.config.pipeline.service.validation.StructuralValidationRule;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates step types and their constraints.
 */
public class StepTypeValidator implements StructuralValidationRule {
    
    @Override
    public List<String> validate(String pipelineId, PipelineConfig config) {
        List<String> errors = new ArrayList<>();
        
        if (config.pipelineSteps() == null || config.pipelineSteps().isEmpty()) {
            return errors; // Let RequiredFieldsValidator handle this
        }
        
        int initialPipelineCount = 0;
        int sinkCount = 0;
        
        for (var entry : config.pipelineSteps().entrySet()) {
            String stepId = entry.getKey();
            PipelineStepConfig step = entry.getValue();
            
            if (step == null || step.stepType() == null) {
                continue; // Let RequiredFieldsValidator handle this
            }
            
            StepType stepType = step.stepType();
            String stepPrefix = "Step '" + stepId + "': ";
            
            // Count step types
            if (stepType == StepType.INITIAL_PIPELINE) {
                initialPipelineCount++;
            } else if (stepType == StepType.SINK) {
                sinkCount++;
            }
            
            // Validate step type constraints
            validateStepTypeConstraints(stepId, step, errors);
        }
        
        // Validate pipeline structure constraints
        if (initialPipelineCount == 0) {
            errors.add("Pipeline must have at least one INITIAL_PIPELINE step");
        }
        
        if (initialPipelineCount > 1) {
            errors.add("Pipeline can have at most one INITIAL_PIPELINE step, found " + initialPipelineCount);
        }
        
        if (sinkCount == 0) {
            errors.add("Pipeline must have at least one SINK step");
        }
        
        return errors;
    }
    
    private void validateStepTypeConstraints(String stepId, PipelineStepConfig step, List<String> errors) {
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
                break;
                
            case PIPELINE:
                // Regular pipeline steps can have both inputs and outputs
                // No specific constraints for now
                break;
        }
    }
    
    @Override
    public int getPriority() {
        return 30; // Run after basic validation
    }
}