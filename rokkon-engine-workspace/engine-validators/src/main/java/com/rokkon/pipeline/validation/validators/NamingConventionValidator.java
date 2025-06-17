package com.rokkon.pipeline.validation.validators;

import com.rokkon.pipeline.config.model.PipelineConfig;
import com.rokkon.pipeline.config.model.PipelineStepConfig;
import com.rokkon.pipeline.config.model.TransportType;
import com.rokkon.pipeline.config.model.KafkaTransportConfig;
import com.rokkon.pipeline.validation.PipelineConfigValidator;
import com.rokkon.pipeline.validation.ValidationResult;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@ApplicationScoped
public class NamingConventionValidator implements PipelineConfigValidator {
    
    private static final Pattern PIPELINE_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9-]*[a-zA-Z0-9]$");
    private static final Pattern STEP_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9-]*[a-zA-Z0-9]$");
    private static final Pattern EXPECTED_TOPIC_PATTERN = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9-]*[a-zA-Z0-9]\\.[a-zA-Z0-9][a-zA-Z0-9-]*[a-zA-Z0-9]\\.input$");
    private static final Pattern EXPECTED_DLQ_PATTERN = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9-]*[a-zA-Z0-9]\\.[a-zA-Z0-9][a-zA-Z0-9-]*[a-zA-Z0-9]\\.input\\.dlq$");
    private static final Pattern EXPECTED_CONSUMER_GROUP_PATTERN = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9-]*[a-zA-Z0-9]\\.consumer-group$");
    
    // Kafka topic name constraints from requirements
    private static final Pattern TOPIC_CHAR_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]+$");
    private static final int MAX_TOPIC_LENGTH = 249;
    
    @Override
    public ValidationResult validate(PipelineConfig config) {
        if (config == null) {
            return ValidationResult.success();
        }
        
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Validate pipeline name format
        if (config.name() != null) {
            validatePipelineName(config.name(), errors, warnings);
        }
        
        // Validate step names and topic naming conventions
        if (config.pipelineSteps() != null) {
            for (PipelineStepConfig step : config.pipelineSteps().values()) {
                validateStep(config.name(), step, errors, warnings);
            }
        }
        
        if (!errors.isEmpty()) {
            return ValidationResult.failure(errors, warnings);
        } else if (!warnings.isEmpty()) {
            return ValidationResult.successWithWarnings(warnings);
        } else {
            return ValidationResult.success();
        }
    }
    
    private void validatePipelineName(String pipelineName, List<String> errors, List<String> warnings) {
        if (pipelineName.contains(".")) {
            errors.add("Pipeline name '" + pipelineName + "' cannot contain dots - dots are reserved as delimiters in topic naming convention");
        }
        
        if (!PIPELINE_NAME_PATTERN.matcher(pipelineName).matches()) {
            errors.add("Pipeline name '" + pipelineName + "' must contain only alphanumeric characters and hyphens, starting and ending with alphanumeric");
        }
        
        if (pipelineName.length() > 50) {
            warnings.add("Pipeline name '" + pipelineName + "' is longer than 50 characters, which may cause topic name length issues");
        }
    }
    
    private void validateStep(String pipelineName, PipelineStepConfig step, List<String> errors, List<String> warnings) {
        if (step.stepName() != null) {
            validateStepName(step.stepName(), errors, warnings);
        }
        
        // Validate Kafka transport naming conventions for outputs
        if (step.outputs() != null) {
            step.outputs().forEach((outputName, transport) -> {
                if (transport.transportType() == TransportType.KAFKA && transport.kafkaTransport() != null) {
                    validateKafkaTransportNaming(pipelineName, step.stepName(), transport.kafkaTransport(), errors, warnings);
                }
            });
        }
        
        // Validate Kafka input consumer groups
        if (step.kafkaInputs() != null) {
            for (var kafkaInput : step.kafkaInputs()) {
                if (kafkaInput.consumerGroupId() != null) {
                    validateConsumerGroupNaming(pipelineName, kafkaInput.consumerGroupId(), errors, warnings);
                }
            }
        }
    }
    
    private void validateStepName(String stepName, List<String> errors, List<String> warnings) {
        if (stepName.contains(".")) {
            errors.add("Step name '" + stepName + "' cannot contain dots - dots are reserved as delimiters in topic naming convention");
        }
        
        if (!STEP_NAME_PATTERN.matcher(stepName).matches()) {
            errors.add("Step name '" + stepName + "' must contain only alphanumeric characters and hyphens, starting and ending with alphanumeric");
        }
        
        if (stepName.length() > 50) {
            warnings.add("Step name '" + stepName + "' is longer than 50 characters, which may cause topic name length issues");
        }
    }
    
    private void validateKafkaTransportNaming(String pipelineName, String stepName, KafkaTransportConfig kafka, List<String> errors, List<String> warnings) {
        if (kafka.topic() != null) {
            validateTopicNaming(pipelineName, stepName, kafka.topic(), errors, warnings);
            validateTopicConstraints(kafka.topic(), errors, warnings);
            
            // Validate derived DLQ topic
            String dlqTopic = kafka.getDlqTopic();
            if (dlqTopic != null) {
                validateDlqTopicNaming(pipelineName, stepName, dlqTopic, errors, warnings);
                validateTopicConstraints(dlqTopic, errors, warnings);
            }
        }
        
        // Validate partition key field (should be pipedocId)
        if (kafka.partitionKeyField() != null && !kafka.partitionKeyField().equals("pipedocId")) {
            warnings.add("Partition key field '" + kafka.partitionKeyField() + "' is not the recommended 'pipedocId'. " +
                        "Using 'pipedocId' ensures CRUD operations don't clash out-of-order.");
        }
    }
    
    private void validateTopicNaming(String pipelineName, String stepName, String topic, List<String> errors, List<String> warnings) {
        String expectedTopic = pipelineName + "." + stepName + ".input";
        
        if (!topic.equals(expectedTopic)) {
            if (EXPECTED_TOPIC_PATTERN.matcher(topic).matches()) {
                warnings.add("Topic '" + topic + "' doesn't follow the expected naming convention. Expected: '" + expectedTopic + "'");
            } else {
                errors.add("Topic '" + topic + "' doesn't follow the required naming pattern '{pipeline-name}.{step-name}.input'");
            }
        }
    }
    
    private void validateDlqTopicNaming(String pipelineName, String stepName, String dlqTopic, List<String> errors, List<String> warnings) {
        String expectedDlqTopic = pipelineName + "." + stepName + ".input.dlq";
        
        if (!dlqTopic.equals(expectedDlqTopic)) {
            if (EXPECTED_DLQ_PATTERN.matcher(dlqTopic).matches()) {
                warnings.add("DLQ topic '" + dlqTopic + "' doesn't follow the expected naming convention. Expected: '" + expectedDlqTopic + "'");
            } else {
                errors.add("DLQ topic '" + dlqTopic + "' doesn't follow the required naming pattern '{pipeline-name}.{step-name}.input.dlq'");
            }
        }
    }
    
    private void validateConsumerGroupNaming(String pipelineName, String consumerGroup, List<String> errors, List<String> warnings) {
        String expectedConsumerGroup = pipelineName + ".consumer-group";
        
        if (!consumerGroup.equals(expectedConsumerGroup)) {
            if (EXPECTED_CONSUMER_GROUP_PATTERN.matcher(consumerGroup).matches()) {
                warnings.add("Consumer group '" + consumerGroup + "' doesn't follow the expected naming convention. Expected: '" + expectedConsumerGroup + "'");
            } else {
                errors.add("Consumer group '" + consumerGroup + "' doesn't follow the required naming pattern '{pipeline-name}.consumer-group'");
            }
        }
    }
    
    private void validateTopicConstraints(String topic, List<String> errors, List<String> warnings) {
        // Validate character pattern
        if (!TOPIC_CHAR_PATTERN.matcher(topic).matches()) {
            errors.add("Topic '" + topic + "' contains invalid characters. Only alphanumeric, dots, underscores, and hyphens are allowed.");
        }
        
        // Validate length
        if (topic.length() > MAX_TOPIC_LENGTH) {
            errors.add("Topic '" + topic + "' exceeds maximum length of " + MAX_TOPIC_LENGTH + " characters.");
        }
        
        // Validate not just dots
        if (topic.equals(".") || topic.equals("..")) {
            errors.add("Topic name '" + topic + "' cannot be just dots.");
        }
    }
    
    @Override
    public String getValidatorName() {
        return "NamingConventionValidator";
    }
    
    @Override
    public int getPriority() {
        return 200;
    }
}