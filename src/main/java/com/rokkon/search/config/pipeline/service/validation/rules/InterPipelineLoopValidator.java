package com.rokkon.search.config.pipeline.service.validation.rules;

import com.rokkon.search.config.pipeline.model.PipelineClusterConfig;
import com.rokkon.search.config.pipeline.service.validation.ClusterStructuralValidationRule;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates that there are no circular dependencies between pipelines.
 * TODO: Implement full inter-pipeline loop detection logic.
 */
public class InterPipelineLoopValidator implements ClusterStructuralValidationRule {
    
    @Override
    public List<String> validate(PipelineClusterConfig clusterConfig) {
        List<String> errors = new ArrayList<>();
        
        if (clusterConfig.pipelineGraphConfig() == null || 
            clusterConfig.pipelineGraphConfig().pipelines() == null) {
            return errors; // Nothing to validate
        }
        
        // TODO: Implement inter-pipeline loop detection
        // This would check for circular dependencies between pipelines
        // through shared Kafka topics or gRPC service calls
        
        return errors;
    }
    
    @Override
    public int getPriority() {
        return 100;
    }
}