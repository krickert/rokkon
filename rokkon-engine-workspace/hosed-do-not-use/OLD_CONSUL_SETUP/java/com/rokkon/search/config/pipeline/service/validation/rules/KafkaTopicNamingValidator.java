package com.rokkon.search.config.pipeline.service.validation.rules;

import com.rokkon.search.config.pipeline.model.PipelineConfig;
import com.rokkon.search.config.pipeline.model.PipelineStepConfig;
import com.rokkon.search.config.pipeline.model.TransportType;
import com.rokkon.search.config.pipeline.service.validation.StructuralValidationRule;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates Kafka topic naming conventions.
 */
public class KafkaTopicNamingValidator implements StructuralValidationRule {
    
    @Override
    public List<String> validate(String pipelineId, PipelineConfig config) {
        List<String> errors = new ArrayList<>();
        
        if (config.pipelineSteps() == null) {
            return errors;
        }
        
        for (var entry : config.pipelineSteps().entrySet()) {
            String stepId = entry.getKey();
            PipelineStepConfig step = entry.getValue();
            
            if (step == null) continue;
            
            // Validate Kafka outputs
            if (step.outputs() != null) {
                for (var outputEntry : step.outputs().entrySet()) {
                    var output = outputEntry.getValue();
                    if (output != null && output.transportType() == TransportType.KAFKA && 
                        output.kafkaTransport() != null && output.kafkaTransport().topic() != null) {
                        
                        String topic = output.kafkaTransport().topic();
                        validateTopicName(stepId, topic, errors);
                    }
                }
            }
            
            // Validate Kafka inputs
            if (step.kafkaInputs() != null) {
                for (var input : step.kafkaInputs()) {
                    if (input.listenTopics() != null) {
                        for (String topic : input.listenTopics()) {
                            if (topic != null) {
                                validateTopicName(stepId, topic, errors);
                            }
                        }
                    }
                }
            }
        }
        
        return errors;
    }
    
    private void validateTopicName(String stepId, String topicName, List<String> errors) {
        if (topicName == null || topicName.isBlank()) {
            errors.add("Step '" + stepId + "': Topic name cannot be empty");
            return;
        }
        
        if (!topicName.matches("^[a-zA-Z0-9._-]+$")) {
            errors.add("Step '" + stepId + "': Topic name '" + topicName + 
                      "' can only contain letters, numbers, dots, underscores, and hyphens");
        }
        
        if (topicName.length() > 249) {
            errors.add("Step '" + stepId + "': Topic name '" + topicName + 
                      "' cannot exceed 249 characters");
        }
        
        if (topicName.equals(".") || topicName.equals("..")) {
            errors.add("Step '" + stepId + "': Topic name cannot be '.' or '..'");
        }
    }
    
    @Override
    public int getPriority() {
        return 50;
    }
}