package com.rokkon.pipeline.validation.validators;

import com.rokkon.pipeline.config.model.KafkaInputDefinition;
import com.rokkon.pipeline.validation.DELET_ME_I_SHOULD_USE_INTERFACE_OR_MOCK_OR_DEFAULT_ValidationResult;
import jakarta.enterprise.context.ApplicationScoped;
import com.rokkon.pipeline.config.model.PipelineConfig;
import com.rokkon.pipeline.config.model.PipelineStepConfig;
import com.rokkon.pipeline.config.model.TransportType;
import com.rokkon.pipeline.validation.PipelineConfigValidator;
import com.rokkon.pipeline.validation.DefaultValidationResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates Kafka topic naming conventions according to Kafka's requirements:
 * - Topic names can contain letters, numbers, dots, underscores, and hyphens
 * - Topic names cannot exceed 249 characters
 * - Topic names cannot be "." or ".."
 * 
 * Also validates against our design decisions:
 * - Topics should follow pattern: {pipeline-name}.{step-name}.input/output
 * - DLQ topics should follow pattern: {topic}.dlq
 */
@ApplicationScoped
public class KafkaTopicNamingValidator implements PipelineConfigValidator {
    
    private static final String VALID_TOPIC_REGEX = "^[a-zA-Z0-9._-]+$";
    private static final int MAX_TOPIC_LENGTH = 249;
    
    @Override
    public DELET_ME_I_SHOULD_USE_INTERFACE_OR_MOCK_OR_DEFAULT_ValidationResult validate(PipelineConfig config) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        if (config.pipelineSteps() == null) {
            return DELET_ME_I_SHOULD_USE_INTERFACE_OR_MOCK_OR_DEFAULT_ValidationResult.success();
        }
        
        for (var entry : config.pipelineSteps().entrySet()) {
            String stepId = entry.getKey();
            PipelineStepConfig step = entry.getValue();
            
            if (step == null) continue;
            
            // Validate Kafka outputs
            if (step.outputs() != null) {
                for (var outputEntry : step.outputs().entrySet()) {
                    String outputName = outputEntry.getKey();
                    var output = outputEntry.getValue();
                    if (output != null && output.transportType() == TransportType.KAFKA && 
                        output.kafkaTransport() != null && output.kafkaTransport().topic() != null) {
                        
                        String topic = output.kafkaTransport().topic();
                        validateTopicName(stepId, outputName, topic, errors, warnings);
                        
                        // Check for DLQ topic pattern
                        String dlqTopic = output.kafkaTransport().getDlqTopic();
                        if (!dlqTopic.equals(topic + ".dlq")) {
                            warnings.add(String.format(
                                "Step '%s' output '%s': DLQ topic '%s' doesn't follow recommended pattern '%s.dlq'",
                                stepId, outputName, dlqTopic, topic
                            ));
                        }
                    }
                }
            }
            
            // Validate Kafka inputs
            if (step.kafkaInputs() != null) {
                for (KafkaInputDefinition input : step.kafkaInputs()) {
                    if (input.listenTopics() != null) {
                        for (String topic : input.listenTopics()) {
                            if (topic != null) {
                                validateTopicName(stepId, "input", topic, errors, warnings);
                            }
                        }
                    }
                }
            }
        }
        
        if (!errors.isEmpty()) {
            return DefaultValidationResult.failure(errors, warnings);
        } else if (!warnings.isEmpty()) {
            return DELET_ME_I_SHOULD_USE_INTERFACE_OR_MOCK_OR_DEFAULT_ValidationResult.successWithWarnings(warnings);
        } else {
            return DELET_ME_I_SHOULD_USE_INTERFACE_OR_MOCK_OR_DEFAULT_ValidationResult.success();
        }
    }
    
    private void validateTopicName(String stepId, String context, String topicName, 
                                   List<String> errors, List<String> warnings) {
        if (topicName == null || topicName.isBlank()) {
            errors.add(String.format("Step '%s' %s: Topic name cannot be empty", stepId, context));
            return;
        }
        
        // Kafka requirements
        if (!topicName.matches(VALID_TOPIC_REGEX)) {
            errors.add(String.format(
                "Step '%s' %s: Topic name '%s' can only contain letters, numbers, dots, underscores, and hyphens",
                stepId, context, topicName
            ));
        }
        
        if (topicName.length() > MAX_TOPIC_LENGTH) {
            errors.add(String.format(
                "Step '%s' %s: Topic name '%s' cannot exceed %d characters",
                stepId, context, topicName, MAX_TOPIC_LENGTH
            ));
        }
        
        if (topicName.equals(".") || topicName.equals("..")) {
            errors.add(String.format(
                "Step '%s' %s: Topic name cannot be '.' or '..'",
                stepId, context
            ));
        }
        
        // Our naming convention recommendations
        if (!topicName.contains(".")) {
            warnings.add(String.format(
                "Step '%s' %s: Topic name '%s' should follow pattern '{pipeline-name}.{step-name}.{input/output}'",
                stepId, context, topicName
            ));
        }
    }
    
    @Override
    public int getPriority() {
        return 50; // Higher priority for basic naming validation
    }
    
    @Override
    public String getValidatorName() {
        return "KafkaTopicNamingValidator";
    }
}