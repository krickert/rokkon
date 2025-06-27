package com.rokkon.pipeline.validation.validators;

import com.rokkon.pipeline.config.model.PipelineClusterConfig;
import com.rokkon.pipeline.validation.PipelineClusterConfigValidator;
import com.rokkon.pipeline.validation.PipelineClusterConfigValidatable;
import com.rokkon.pipeline.validation.ValidationResult;
import com.rokkon.pipeline.validation.ValidationResultFactory;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates that there are no circular dependencies between pipelines.
 * TODO: Implement full inter-pipeline loop detection logic.
 */
@ApplicationScoped
public class InterPipelineLoopValidator implements PipelineClusterConfigValidator {
    
    @Override
    public ValidationResult validate(PipelineClusterConfigValidatable validatable) {
        PipelineClusterConfig clusterConfig = (PipelineClusterConfig) validatable;
        if (clusterConfig == null || 
            clusterConfig.pipelineGraphConfig() == null || 
            clusterConfig.pipelineGraphConfig().pipelines() == null) {
            return ValidationResultFactory.success();
        }
        
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // TODO: Implement inter-pipeline loop detection
        // This would check for circular dependencies between pipelines
        // through shared Kafka topics or gRPC service calls
        // For now, just add a warning that this validation is not yet implemented
        warnings.add("Inter-pipeline loop detection is not yet implemented");
        
        return errors.isEmpty() ? ValidationResultFactory.successWithWarnings(warnings) : ValidationResultFactory.failure(errors, warnings);
    }
    
    @Override
    public int getPriority() {
        return 100;
    }
    
    @Override
    public String getValidatorName() {
        return "InterPipelineLoopValidator";
    }
}