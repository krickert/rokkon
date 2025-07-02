package com.rokkon.pipeline.config.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base test class for KafkaTransportConfig serialization/deserialization.
 * Tests all the enhanced features including DLQ topic derivation, defaults, and producer properties.
 */
public abstract class KafkaTransportConfigTestBase {

    protected abstract ObjectMapper getObjectMapper();

    @Test
    public void testDefaultValues() {
        KafkaTransportConfig config = new KafkaTransportConfig(
                "test-topic", null, null, null, null, null);
        
        assertThat(config.topic()).isEqualTo("test-topic");
        assertThat(config.partitionKeyField()).isEqualTo("pipedocId");
        assertThat(config.compressionType()).isEqualTo("snappy");
        assertThat(config.batchSize()).isEqualTo(16384);
        assertThat(config.lingerMs()).isEqualTo(10);
        assertThat(config.kafkaProducerProperties()).isEmpty();
    }

    @Test
    public void testDlqTopicDerivation() {
        KafkaTransportConfig config = new KafkaTransportConfig(
                "document-processing.parser.input", null, null, null, null, null);
        
        assertThat(config.getDlqTopic()).isEqualTo("document-processing.parser.input.dlq");
        
        // Test null topic
        KafkaTransportConfig nullTopicConfig = new KafkaTransportConfig(
                null, null, null, null, null, null);
        assertThat(nullTopicConfig.getDlqTopic()).isNull();
    }

    @Test
    public void testCustomValues() {
        KafkaTransportConfig config = new KafkaTransportConfig(
                "custom-topic",
                "customerId",
                "lz4",
                32768,
                50,
                Map.of("acks", "all", "max.request.size", "20971520")
        );
        
        assertThat(config.topic()).isEqualTo("custom-topic");
        assertThat(config.partitionKeyField()).isEqualTo("customerId");
        assertThat(config.compressionType()).isEqualTo("lz4");
        assertThat(config.batchSize()).isEqualTo(32768);
        assertThat(config.lingerMs()).isEqualTo(50);
        assertThat(config.kafkaProducerProperties()).containsEntry("acks", "all");
    }

    @Test
    public void testGetAllProducerProperties() {
        KafkaTransportConfig config = new KafkaTransportConfig(
                "test-topic",
                null,
                "gzip",
                65536,
                100,
                Map.of("acks", "all", "retries", "3")
        );
        
        Map<String, String> allProps = config.getAllProducerProperties();
        
        // Check merged properties
        assertThat(allProps).containsEntry("compression.type", "gzip");
        assertThat(allProps).containsEntry("batch.size", "65536");
        assertThat(allProps).containsEntry("linger.ms", "100");
        assertThat(allProps).containsEntry("acks", "all");
        assertThat(allProps).containsEntry("retries", "3");
        
        // Verify immutability
        assertThat(allProps).isUnmodifiable();
    }

    @Test
    public void testSerializationDeserialization() throws Exception {
        KafkaTransportConfig original = new KafkaTransportConfig(
                "pipeline.step.input",
                "documentId",
                "snappy",
                32768,
                20,
                Map.of("max.in.flight.requests.per.connection", "5")
        );
        
        String json = getObjectMapper().writeValueAsString(original);
        
        // Verify JSON structure
        assertThat(json).contains("\"topic\":\"pipeline.step.input\"");
        assertThat(json).contains("\"partitionKeyField\":\"documentId\"");
        assertThat(json).contains("\"compressionType\":\"snappy\"");
        assertThat(json).contains("\"batchSize\":32768");
        assertThat(json).contains("\"lingerMs\":20");
        assertThat(json).contains("\"max.in.flight.requests.per.connection\":\"5\"");
        
        // Deserialize
        KafkaTransportConfig deserialized = getObjectMapper().readValue(json, KafkaTransportConfig.class);
        
        assertThat(deserialized.topic()).isEqualTo(original.topic());
        assertThat(deserialized.partitionKeyField()).isEqualTo(original.partitionKeyField());
        assertThat(deserialized.compressionType()).isEqualTo(original.compressionType());
        assertThat(deserialized.batchSize()).isEqualTo(original.batchSize());
        assertThat(deserialized.lingerMs()).isEqualTo(original.lingerMs());
        assertThat(deserialized.kafkaProducerProperties()).isEqualTo(original.kafkaProducerProperties());
    }

    @Test
    public void testMinimalSerialization() throws Exception {
        // Only topic is provided, everything else defaults
        String json = """
            {
                "topic": "minimal-topic"
            }
            """;
        
        KafkaTransportConfig config = getObjectMapper().readValue(json, KafkaTransportConfig.class);
        
        assertThat(config.topic()).isEqualTo("minimal-topic");
        assertThat(config.partitionKeyField()).isEqualTo("pipedocId");
        assertThat(config.compressionType()).isEqualTo("snappy");
        assertThat(config.batchSize()).isEqualTo(16384);
        assertThat(config.lingerMs()).isEqualTo(10);
        assertThat(config.getDlqTopic()).isEqualTo("minimal-topic.dlq");
    }

    @Test
    public void testNegativeAndZeroValues() {
        // Test that negative/zero values are replaced with defaults
        KafkaTransportConfig config = new KafkaTransportConfig(
                "test-topic",
                "",           // blank partition key field
                "",           // blank compression type
                0,            // zero batch size
                -1,           // negative linger ms
                null
        );
        
        assertThat(config.partitionKeyField()).isEqualTo("pipedocId");
        assertThat(config.compressionType()).isEqualTo("snappy");
        assertThat(config.batchSize()).isEqualTo(16384);
        assertThat(config.lingerMs()).isEqualTo(10);
    }

    @Test
    public void testTopicNamingConvention() {
        // Test typical pipeline topic naming
        KafkaTransportConfig config = new KafkaTransportConfig(
                "document-processing.parser.input", null, null, null, null, null);
        
        assertThat(config.topic()).isEqualTo("document-processing.parser.input");
        assertThat(config.getDlqTopic()).isEqualTo("document-processing.parser.input.dlq");
        
        // The validation of no dots in custom topics would be done by validators,
        // not by the model itself
    }

    @Test
    public void testRealWorldConfiguration() throws Exception {
        // Test a configuration that might be used in production
        String json = """
            {
                "topic": "production.document-parser.input",
                "partitionKeyField": "pipedocId",
                "compressionType": "snappy",
                "batchSize": 65536,
                "lingerMs": 50,
                "kafkaProducerProperties": {
                    "acks": "all",
                    "retries": "3",
                    "max.in.flight.requests.per.connection": "5",
                    "enable.idempotence": "true",
                    "max.request.size": "20971520"
                }
            }
            """;
        
        KafkaTransportConfig config = getObjectMapper().readValue(json, KafkaTransportConfig.class);
        
        assertThat(config.topic()).isEqualTo("production.document-parser.input");
        assertThat(config.compressionType()).isEqualTo("snappy");
        assertThat(config.batchSize()).isEqualTo(65536);
        assertThat(config.lingerMs()).isEqualTo(50);
        
        // Check important producer properties
        assertThat(config.kafkaProducerProperties())
            .containsEntry("acks", "all")
            .containsEntry("enable.idempotence", "true")
            .containsEntry("max.request.size", "20971520");
        
        // Verify DLQ topic
        assertThat(config.getDlqTopic()).isEqualTo("production.document-parser.input.dlq");
        
        // Verify merged properties include both explicit and additional
        Map<String, String> allProps = config.getAllProducerProperties();
        assertThat(allProps)
            .containsEntry("compression.type", "snappy")
            .containsEntry("batch.size", "65536")
            .containsEntry("linger.ms", "50")
            .containsEntry("acks", "all")
            .hasSize(8); // 3 explicit + 5 additional
    }
}