package com.rokkon.pipeline.config.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Kafka input definition")
public record KafkaInputDefinition(
        @JsonProperty("listenTopics") @NotEmpty List<String> listenTopics,
        @JsonProperty("consumerGroupId") String consumerGroupId, // Now truly optional in config
        @JsonProperty("kafkaConsumerProperties") Map<String, String> kafkaConsumerProperties
) {
    public KafkaInputDefinition {
        if (listenTopics == null || listenTopics.isEmpty()) {
            throw new IllegalArgumentException("listenTopics cannot be null or empty");
        }
        // Validate that no topic is null or blank
        for (String topic : listenTopics) {
            if (topic == null || topic.isBlank()) {
                throw new IllegalArgumentException("listenTopics cannot contain null or blank topics");
            }
        }
        
        listenTopics = List.copyOf(listenTopics); // Make immutable
        kafkaConsumerProperties = (kafkaConsumerProperties == null) ? Collections.emptyMap() : Map.copyOf(kafkaConsumerProperties);
        // No validation for consumerGroupId being null/blank here, as it's optional.
        // The engine will handle defaulting if it's null.
    }
}