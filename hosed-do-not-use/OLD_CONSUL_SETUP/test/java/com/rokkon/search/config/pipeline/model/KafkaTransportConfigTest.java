package com.rokkon.search.config.pipeline.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class KafkaTransportConfigTest {

    @Inject
    ObjectMapper objectMapper;

    @Test
    public void testSerializationComplete() throws Exception {
        Map<String, String> properties = Map.of(
            "acks", "all",
            "retries", "3",
            "compression.type", "gzip"
        );
        KafkaTransportConfig config = new KafkaTransportConfig("document-chunks", properties);
        
        String json = objectMapper.writeValueAsString(config);
        
        assertTrue(json.contains("\"topic\":\"document-chunks\""));
        assertTrue(json.contains("\"kafkaProducerProperties\""));
        assertTrue(json.contains("\"acks\":\"all\""));
        assertTrue(json.contains("\"retries\":\"3\""));
    }

    @Test
    public void testDeserialization() throws Exception {
        String json = """
            {
                "topic": "embeddings-output",
                "kafkaProducerProperties": {
                    "batch.size": "16384",
                    "linger.ms": "10"
                }
            }
            """;
        
        KafkaTransportConfig config = objectMapper.readValue(json, KafkaTransportConfig.class);
        
        assertEquals("embeddings-output", config.topic());
        assertEquals("16384", config.kafkaProducerProperties().get("batch.size"));
        assertEquals("10", config.kafkaProducerProperties().get("linger.ms"));
    }

    @Test
    public void testRoundTrip() throws Exception {
        Map<String, String> props = Map.of("key", "value", "timeout", "30000");
        KafkaTransportConfig original = new KafkaTransportConfig("test-topic", props);
        
        String json = objectMapper.writeValueAsString(original);
        KafkaTransportConfig deserialized = objectMapper.readValue(json, KafkaTransportConfig.class);
        
        assertEquals(original.topic(), deserialized.topic());
        assertEquals(original.kafkaProducerProperties(), deserialized.kafkaProducerProperties());
    }
}