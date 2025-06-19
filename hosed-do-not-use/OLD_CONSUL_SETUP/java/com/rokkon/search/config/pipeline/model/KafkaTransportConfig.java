package com.rokkon.search.config.pipeline.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Collections;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Kafka transport configuration")
public record KafkaTransportConfig(
        @JsonProperty("topic") String topic, // Target topic to publish to
        @JsonProperty("kafkaProducerProperties") Map<String, String> kafkaProducerProperties
        // Specific properties for the producer for THIS output
) {
    @JsonCreator
    public KafkaTransportConfig(
            @JsonProperty("topic") String topic,
            @JsonProperty("kafkaProducerProperties") Map<String, String> kafkaProducerProperties
    ) {
        this.topic = topic; // Can be null if not a Kafka output, validation handled by OutputTarget
        this.kafkaProducerProperties = (kafkaProducerProperties == null) ? Collections.emptyMap() : Map.copyOf(kafkaProducerProperties);
    }
}