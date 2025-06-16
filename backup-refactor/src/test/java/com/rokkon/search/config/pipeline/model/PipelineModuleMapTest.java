package com.rokkon.search.config.pipeline.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class PipelineModuleMapTest {

    @Inject
    ObjectMapper objectMapper;

    @Test
    public void testSerializationWithModules() throws Exception {
        SchemaReference chunkerSchema = new SchemaReference("chunker-schema", 1);
        SchemaReference embedderSchema = new SchemaReference("embedder-schema", 2);
        
        PipelineModuleConfiguration chunker = new PipelineModuleConfiguration(
            "Text Chunker", "chunker-v1", chunkerSchema, Map.of("type", "sentence"));
        PipelineModuleConfiguration embedder = new PipelineModuleConfiguration(
            "OpenAI Embedder", "embedder-openai", embedderSchema, Map.of("model", "text-embedding-ada-002"));
        
        Map<String, PipelineModuleConfiguration> modules = Map.of(
            "chunker-v1", chunker,
            "embedder-openai", embedder
        );
        
        PipelineModuleMap moduleMap = new PipelineModuleMap(modules);
        String json = objectMapper.writeValueAsString(moduleMap);
        
        assertTrue(json.contains("\"availableModules\""));
        assertTrue(json.contains("\"chunker-v1\""));
        assertTrue(json.contains("\"embedder-openai\""));
        assertTrue(json.contains("\"Text Chunker\""));
        assertTrue(json.contains("\"OpenAI Embedder\""));
        assertTrue(json.contains("\"chunker-schema\""));
        assertTrue(json.contains("\"embedder-schema\""));
    }

    @Test
    public void testSerializationEmpty() throws Exception {
        PipelineModuleMap moduleMap = new PipelineModuleMap(null);
        String json = objectMapper.writeValueAsString(moduleMap);
        
        assertTrue(json.contains("\"availableModules\":{}") || !json.contains("availableModules"));
    }

    @Test
    public void testDeserialization() throws Exception {
        String json = """
            {
                "availableModules": {
                    "tika-parser": {
                        "implementationName": "Apache Tika Parser",
                        "implementationId": "tika-parser",
                        "customConfigSchemaReference": {
                            "subject": "tika-schema",
                            "version": 1
                        },
                        "customConfig": {
                            "maxFileSize": "50MB",
                            "timeout": "30s"
                        }
                    },
                    "opensearch-sink": {
                        "implementationName": "OpenSearch Sink",
                        "implementationId": "opensearch-sink",
                        "customConfigSchemaReference": {
                            "subject": "opensearch-schema",
                            "version": 2
                        },
                        "customConfig": {
                            "indexName": "documents",
                            "batchSize": 100
                        }
                    }
                }
            }
            """;
        
        PipelineModuleMap moduleMap = objectMapper.readValue(json, PipelineModuleMap.class);
        
        assertEquals(2, moduleMap.availableModules().size());
        
        PipelineModuleConfiguration tikaParser = moduleMap.availableModules().get("tika-parser");
        assertNotNull(tikaParser);
        assertEquals("Apache Tika Parser", tikaParser.implementationName());
        assertEquals("tika-parser", tikaParser.implementationId());
        assertEquals("50MB", tikaParser.customConfig().get("maxFileSize"));
        
        PipelineModuleConfiguration opensearchSink = moduleMap.availableModules().get("opensearch-sink");
        assertNotNull(opensearchSink);
        assertEquals("OpenSearch Sink", opensearchSink.implementationName());
        assertEquals("opensearch-sink", opensearchSink.implementationId());
        assertEquals(100, opensearchSink.customConfig().get("batchSize"));
    }

    @Test
    public void testDeserializationEmpty() throws Exception {
        String json = """
            {
                "availableModules": {}
            }
            """;
        
        PipelineModuleMap moduleMap = objectMapper.readValue(json, PipelineModuleMap.class);
        assertTrue(moduleMap.availableModules().isEmpty());
    }

    @Test
    public void testDeserializationMissingField() throws Exception {
        String json = "{}";
        
        PipelineModuleMap moduleMap = objectMapper.readValue(json, PipelineModuleMap.class);
        assertTrue(moduleMap.availableModules().isEmpty());
    }

    @Test
    public void testImmutability() {
        PipelineModuleConfiguration module = new PipelineModuleConfiguration("Test", "test", null);
        Map<String, PipelineModuleConfiguration> modules = Map.of("test", module);
        PipelineModuleMap moduleMap = new PipelineModuleMap(modules);
        
        // availableModules map should be immutable
        assertThrows(UnsupportedOperationException.class,
            () -> moduleMap.availableModules().put("new", module));
    }

    @Test
    public void testRoundTrip() throws Exception {
        SchemaReference schema1 = new SchemaReference("schema1", 1);
        SchemaReference schema2 = new SchemaReference("schema2", 2);
        
        PipelineModuleConfiguration module1 = new PipelineModuleConfiguration(
            "Module 1", "mod1", schema1, Map.of("config1", "value1"));
        PipelineModuleConfiguration module2 = new PipelineModuleConfiguration(
            "Module 2", "mod2", schema2, Map.of("config2", "value2"));
        
        Map<String, PipelineModuleConfiguration> modules = Map.of(
            "mod1", module1,
            "mod2", module2
        );
        
        PipelineModuleMap original = new PipelineModuleMap(modules);
        
        String json = objectMapper.writeValueAsString(original);
        PipelineModuleMap deserialized = objectMapper.readValue(json, PipelineModuleMap.class);
        
        assertEquals(original.availableModules().size(), deserialized.availableModules().size());
        
        PipelineModuleConfiguration deserializedMod1 = deserialized.availableModules().get("mod1");
        assertNotNull(deserializedMod1);
        assertEquals(module1.implementationName(), deserializedMod1.implementationName());
        assertEquals(module1.implementationId(), deserializedMod1.implementationId());
        assertEquals(module1.customConfig(), deserializedMod1.customConfig());
        
        PipelineModuleConfiguration deserializedMod2 = deserialized.availableModules().get("mod2");
        assertNotNull(deserializedMod2);
        assertEquals(module2.implementationName(), deserializedMod2.implementationName());
        assertEquals(module2.implementationId(), deserializedMod2.implementationId());
        assertEquals(module2.customConfig(), deserializedMod2.customConfig());
    }
}