package com.rokkon.search.config.pipeline.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class PipelineModuleConfigurationTest {

    @Inject
    ObjectMapper objectMapper;

    @Test
    public void testSerializationComplete() throws Exception {
        SchemaReference schemaRef = new SchemaReference("chunker-schema", 2);
        Map<String, Object> config = Map.of(
            "maxTokens", 1000,
            "overlap", 100,
            "splitOnSentences", true
        );
        
        PipelineModuleConfiguration module = new PipelineModuleConfiguration(
            "Text Chunker Service",
            "chunker-service-v1", 
            schemaRef,
            config
        );
        
        String json = objectMapper.writeValueAsString(module);
        
        assertTrue(json.contains("\"implementationName\":\"Text Chunker Service\""));
        assertTrue(json.contains("\"implementationId\":\"chunker-service-v1\""));
        assertTrue(json.contains("\"customConfigSchemaReference\""));
        assertTrue(json.contains("\"subject\":\"chunker-schema\""));
        assertTrue(json.contains("\"version\":2"));
        assertTrue(json.contains("\"customConfig\""));
        assertTrue(json.contains("\"maxTokens\":1000"));
        assertTrue(json.contains("\"splitOnSentences\":true"));
    }

    @Test
    public void testSerializationMinimal() throws Exception {
        PipelineModuleConfiguration module = new PipelineModuleConfiguration(
            "Simple Embedder",
            "embedder-basic",
            null, // no schema reference
            null  // no custom config
        );
        
        String json = objectMapper.writeValueAsString(module);
        
        assertTrue(json.contains("\"implementationName\":\"Simple Embedder\""));
        assertTrue(json.contains("\"implementationId\":\"embedder-basic\""));
        // Should have empty customConfig
        assertTrue(json.contains("\"customConfig\":{}") || !json.contains("customConfig"));
    }

    @Test
    public void testDeserialization() throws Exception {
        String json = """
            {
                "implementationName": "OpenSearch Sink",
                "implementationId": "opensearch-sink-v2",
                "customConfigSchemaReference": {
                    "subject": "opensearch-schema", 
                    "version": 3
                },
                "customConfig": {
                    "indexName": "documents",
                    "batchSize": 100,
                    "timeout": "30s"
                }
            }
            """;
        
        PipelineModuleConfiguration module = objectMapper.readValue(json, PipelineModuleConfiguration.class);
        
        assertEquals("OpenSearch Sink", module.implementationName());
        assertEquals("opensearch-sink-v2", module.implementationId());
        assertNotNull(module.customConfigSchemaReference());
        assertEquals("opensearch-schema", module.customConfigSchemaReference().subject());
        assertEquals(3, module.customConfigSchemaReference().version());
        assertEquals("documents", module.customConfig().get("indexName"));
        assertEquals(100, module.customConfig().get("batchSize"));
        assertEquals("30s", module.customConfig().get("timeout"));
    }

    @Test
    public void testDeserializationMinimal() throws Exception {
        String json = """
            {
                "implementationName": "Minimal Service",
                "implementationId": "minimal-v1"
            }
            """;
        
        PipelineModuleConfiguration module = objectMapper.readValue(json, PipelineModuleConfiguration.class);
        
        assertEquals("Minimal Service", module.implementationName());
        assertEquals("minimal-v1", module.implementationId());
        assertNull(module.customConfigSchemaReference());
        assertTrue(module.customConfig().isEmpty());
    }

    @Test
    public void testValidation() {
        SchemaReference schemaRef = new SchemaReference("test-schema", 1);
        
        // Valid cases
        assertDoesNotThrow(() -> new PipelineModuleConfiguration("Valid Name", "valid-id", schemaRef, null));
        assertDoesNotThrow(() -> new PipelineModuleConfiguration("Valid Name", "valid-id", null, Map.of("key", "value")));
        
        // Invalid cases
        assertThrows(IllegalArgumentException.class, 
            () -> new PipelineModuleConfiguration(null, "valid-id", schemaRef, null));
        assertThrows(IllegalArgumentException.class, 
            () -> new PipelineModuleConfiguration("", "valid-id", schemaRef, null));
        assertThrows(IllegalArgumentException.class, 
            () -> new PipelineModuleConfiguration("   ", "valid-id", schemaRef, null));
        assertThrows(IllegalArgumentException.class, 
            () -> new PipelineModuleConfiguration("Valid Name", null, schemaRef, null));
        assertThrows(IllegalArgumentException.class, 
            () -> new PipelineModuleConfiguration("Valid Name", "", schemaRef, null));
        assertThrows(IllegalArgumentException.class, 
            () -> new PipelineModuleConfiguration("Valid Name", "   ", schemaRef, null));
    }

    @Test
    public void testConvenienceConstructor() {
        SchemaReference schemaRef = new SchemaReference("test-schema", 1);
        PipelineModuleConfiguration module = new PipelineModuleConfiguration("Test Service", "test-id", schemaRef);
        
        assertEquals("Test Service", module.implementationName());
        assertEquals("test-id", module.implementationId());
        assertEquals(schemaRef, module.customConfigSchemaReference());
        assertTrue(module.customConfig().isEmpty());
    }

    @Test
    public void testImmutability() {
        Map<String, Object> config = Map.of("key", "value");
        PipelineModuleConfiguration module = new PipelineModuleConfiguration("Test", "test-id", null, config);
        
        // CustomConfig map should be immutable
        assertThrows(UnsupportedOperationException.class, 
            () -> module.customConfig().put("new", "value"));
    }

    @Test
    public void testRoundTrip() throws Exception {
        SchemaReference schemaRef = new SchemaReference("round-trip-schema", 5);
        Map<String, Object> config = Map.of(
            "stringValue", "test",
            "intValue", 42,
            "boolValue", false
        );
        
        PipelineModuleConfiguration original = new PipelineModuleConfiguration(
            "Round Trip Test", "round-trip-id", schemaRef, config);
        
        String json = objectMapper.writeValueAsString(original);
        PipelineModuleConfiguration deserialized = objectMapper.readValue(json, PipelineModuleConfiguration.class);
        
        assertEquals(original.implementationName(), deserialized.implementationName());
        assertEquals(original.implementationId(), deserialized.implementationId());
        assertEquals(original.customConfigSchemaReference().subject(), 
                    deserialized.customConfigSchemaReference().subject());
        assertEquals(original.customConfigSchemaReference().version(), 
                    deserialized.customConfigSchemaReference().version());
        assertEquals(original.customConfig(), deserialized.customConfig());
    }
}