package com.rokkon.pipeline.config.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Base test class for KafkaInputDefinition serialization/deserialization.
 * Tests Kafka input configuration for pipeline steps.
 */
public abstract class KafkaInputDefinitionTestBase {

    protected abstract ObjectMapper getObjectMapper();

    @Test
    public void testValidConfiguration() {
        KafkaInputDefinition config = new KafkaInputDefinition(
                List.of("topic1", "topic2", "topic3"),
                "my-consumer-group",
                Map.of("auto.offset.reset", "earliest", "max.poll.records", "500")
        );
        
        assertThat(config.listenTopics()).containsExactly("topic1", "topic2", "topic3");
        assertThat(config.consumerGroupId()).isEqualTo("my-consumer-group");
        assertThat(config.kafkaConsumerProperties())
            .containsEntry("auto.offset.reset", "earliest")
            .containsEntry("max.poll.records", "500");
    }

    @Test
    public void testMinimalConfiguration() {
        // Only topics are required, consumer group can be null
        KafkaInputDefinition config = new KafkaInputDefinition(
                List.of("single-topic"),
                null,
                null
        );
        
        assertThat(config.listenTopics()).containsExactly("single-topic");
        assertThat(config.consumerGroupId()).isNull();
        assertThat(config.kafkaConsumerProperties()).isEmpty();
    }

    @Test
    public void testMultipleTopics() {
        // Test fan-in scenario with multiple input topics
        KafkaInputDefinition config = new KafkaInputDefinition(
                List.of("pipeline.parser.output", "pipeline.enricher.output", "pipeline.validator.output"),
                "aggregator-group",
                Map.of()
        );
        
        assertThat(config.listenTopics()).hasSize(3);
        assertThat(config.consumerGroupId()).isEqualTo("aggregator-group");
    }

    @Test
    public void testNullTopicsValidation() {
        assertThatThrownBy(() -> new KafkaInputDefinition(null, "group", Map.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("listenTopics cannot be null or empty");
    }

    @Test
    public void testEmptyTopicsValidation() {
        assertThatThrownBy(() -> new KafkaInputDefinition(List.of(), "group", Map.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("listenTopics cannot be null or empty");
    }

    @Test
    public void testNullTopicInListValidation() {
        // List.of() throws NullPointerException for null elements
        // This is expected Java behavior - List.of() does not accept null values
        assertThatThrownBy(() -> new KafkaInputDefinition(
                List.of("topic1", null, "topic3"), 
                "group", 
                Map.of()
        ))
            .isInstanceOf(NullPointerException.class);
            
        // Test with ArrayList to properly test our validation
        List<String> topicsWithNull = new java.util.ArrayList<>();
        topicsWithNull.add("topic1");
        topicsWithNull.add(null);
        topicsWithNull.add("topic3");
        
        assertThatThrownBy(() -> new KafkaInputDefinition(
                topicsWithNull, 
                "group", 
                Map.of()
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("listenTopics cannot contain null or blank topics");
    }

    @Test
    public void testBlankTopicInListValidation() {
        assertThatThrownBy(() -> new KafkaInputDefinition(
                List.of("topic1", "  ", "topic3"), 
                "group", 
                Map.of()
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("listenTopics cannot contain null or blank topics");
    }

    @Test
    public void testImmutability() {
        List<String> mutableTopics = new java.util.ArrayList<>();
        mutableTopics.add("topic1");
        
        Map<String, String> mutableProps = new java.util.HashMap<>();
        mutableProps.put("key", "value");
        
        KafkaInputDefinition config = new KafkaInputDefinition(
                mutableTopics,
                "immutable-group",
                mutableProps
        );
        
        // Try to modify original collections
        mutableTopics.add("topic2");
        mutableProps.put("key2", "value2");
        
        // Config should not be affected
        assertThat(config.listenTopics()).hasSize(1);
        assertThat(config.kafkaConsumerProperties()).hasSize(1);
        
        // Returned collections should be immutable
        assertThat(config.listenTopics()).isUnmodifiable();
        assertThat(config.kafkaConsumerProperties()).isUnmodifiable();
    }

    @Test
    public void testSerializationDeserialization() throws Exception {
        KafkaInputDefinition original = new KafkaInputDefinition(
                List.of("document.input", "metadata.input"),
                "processor-group",
                Map.of(
                    "auto.offset.reset", "latest",
                    "max.poll.records", "1000",
                    "fetch.min.bytes", "1024"
                )
        );
        
        String json = getObjectMapper().writeValueAsString(original);
        
        // Verify JSON structure
        assertThat(json).contains("\"listenTopics\":[\"document.input\",\"metadata.input\"]");
        assertThat(json).contains("\"consumerGroupId\":\"processor-group\"");
        assertThat(json).contains("\"auto.offset.reset\":\"latest\"");
        
        // Deserialize
        KafkaInputDefinition deserialized = getObjectMapper().readValue(json, KafkaInputDefinition.class);
        
        assertThat(deserialized.listenTopics()).isEqualTo(original.listenTopics());
        assertThat(deserialized.consumerGroupId()).isEqualTo(original.consumerGroupId());
        assertThat(deserialized.kafkaConsumerProperties()).isEqualTo(original.kafkaConsumerProperties());
    }

    @Test
    public void testDeserializationFromJson() throws Exception {
        String json = """
            {
                "listenTopics": ["pipeline.step1.output", "pipeline.step2.output"],
                "consumerGroupId": "json-consumer-group",
                "kafkaConsumerProperties": {
                    "auto.offset.reset": "earliest",
                    "enable.auto.commit": "false",
                    "max.partition.fetch.bytes": "1048576"
                }
            }
            """;
        
        KafkaInputDefinition config = getObjectMapper().readValue(json, KafkaInputDefinition.class);
        
        assertThat(config.listenTopics()).containsExactly("pipeline.step1.output", "pipeline.step2.output");
        assertThat(config.consumerGroupId()).isEqualTo("json-consumer-group");
        assertThat(config.kafkaConsumerProperties())
            .hasSize(3)
            .containsEntry("enable.auto.commit", "false");
    }

    @Test
    public void testNullConsumerGroupId() throws Exception {
        // Consumer group ID is optional - engine will generate one if needed
        String json = """
            {
                "listenTopics": ["events.input"],
                "kafkaConsumerProperties": {}
            }
            """;
        
        KafkaInputDefinition config = getObjectMapper().readValue(json, KafkaInputDefinition.class);
        
        assertThat(config.listenTopics()).containsExactly("events.input");
        assertThat(config.consumerGroupId()).isNull();
        assertThat(config.kafkaConsumerProperties()).isEmpty();
    }

    @Test
    public void testRealWorldFanInConfiguration() throws Exception {
        // Test configuration for a step that aggregates from multiple sources
        String json = """
            {
                "listenTopics": [
                    "production.parser.output",
                    "production.enricher.output",
                    "production.validator.output",
                    "production.classifier.output"
                ],
                "consumerGroupId": "production.aggregator.consumer-group",
                "kafkaConsumerProperties": {
                    "auto.offset.reset": "earliest",
                    "max.poll.records": "500",
                    "fetch.min.bytes": "10240",
                    "fetch.max.wait.ms": "500",
                    "session.timeout.ms": "30000",
                    "heartbeat.interval.ms": "3000",
                    "max.partition.fetch.bytes": "10485760",
                    "enable.auto.commit": "false",
                    "isolation.level": "read_committed"
                }
            }
            """;
        
        KafkaInputDefinition config = getObjectMapper().readValue(json, KafkaInputDefinition.class);
        
        assertThat(config.listenTopics()).hasSize(4);
        assertThat(config.consumerGroupId()).isEqualTo("production.aggregator.consumer-group");
        assertThat(config.kafkaConsumerProperties()).hasSize(9);
        
        // Verify important consumer properties
        assertThat(config.kafkaConsumerProperties())
            .containsEntry("isolation.level", "read_committed")
            .containsEntry("enable.auto.commit", "false")
            .containsEntry("max.poll.records", "500");
    }
}