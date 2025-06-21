package com.rokkon.pipeline.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base test class for JsonOrderingCustomizer that verifies consistent JSON ordering.
 * Critical for ensuring data scientists' randomly ordered schemas are normalized.
 */
public abstract class JsonOrderingCustomizerTestBase {

    protected abstract ObjectMapper getObjectMapper();

    @Test
    public void testMapEntriesAreSortedByKeys() throws Exception {
        // Create a map with keys in random order
        Map<String, String> unsortedMap = new LinkedHashMap<>();
        unsortedMap.put("zebra", "last");
        unsortedMap.put("apple", "first");
        unsortedMap.put("middle", "center");
        unsortedMap.put("banana", "second");
        
        String json = getObjectMapper().writeValueAsString(unsortedMap);
        
        // Verify keys appear in alphabetical order
        assertThat(json).isEqualTo("{\"apple\":\"first\",\"banana\":\"second\",\"middle\":\"center\",\"zebra\":\"last\"}");
    }

    @Test
    public void testObjectPropertiesAreSortedAlphabetically() throws Exception {
        // Create a simple POJO to test property ordering
        class TestObject {
            public String zField = "z-value";
            public String aField = "a-value";
            public String mField = "m-value";
            public String bField = "b-value";
        }
        
        String json = getObjectMapper().writeValueAsString(new TestObject());
        
        // Verify fields appear in alphabetical order
        assertThat(json).isEqualTo("{\"aField\":\"a-value\",\"bField\":\"b-value\",\"mField\":\"m-value\",\"zField\":\"z-value\"}");
    }

    @Test
    public void testNestedMapsAreSorted() throws Exception {
        Map<String, Object> outer = new LinkedHashMap<>();
        Map<String, String> inner1 = new LinkedHashMap<>();
        inner1.put("z", "26");
        inner1.put("a", "1");
        
        Map<String, String> inner2 = new LinkedHashMap<>();
        inner2.put("beta", "2");
        inner2.put("alpha", "1");
        
        outer.put("second", inner2);
        outer.put("first", inner1);
        
        String json = getObjectMapper().writeValueAsString(outer);
        
        // Both outer and inner maps should be sorted
        assertThat(json).contains("\"first\":{\"a\":\"1\",\"z\":\"26\"}");
        assertThat(json).contains("\"second\":{\"alpha\":\"1\",\"beta\":\"2\"}");
        assertThat(json.indexOf("first")).isLessThan(json.indexOf("second"));
    }

    @Test
    public void testPipelineConfigOrderingConsistency() throws Exception {
        // Simulate what data scientists might create - same config, different order
        String config1 = """
            {
                "stepName": "chunker",
                "stepType": "PIPELINE",
                "processorInfo": {
                    "grpcServiceName": "chunker-service"
                },
                "maxRetries": 3,
                "description": "Chunks documents"
            }
            """;
            
        String config2 = """
            {
                "description": "Chunks documents",
                "maxRetries": 3,
                "stepName": "chunker",
                "processorInfo": {
                    "grpcServiceName": "chunker-service"
                },
                "stepType": "PIPELINE"
            }
            """;
        
        // Parse both configs
        Object obj1 = getObjectMapper().readValue(config1, Object.class);
        Object obj2 = getObjectMapper().readValue(config2, Object.class);
        
        // Serialize both - should produce identical output
        String json1 = getObjectMapper().writeValueAsString(obj1);
        String json2 = getObjectMapper().writeValueAsString(obj2);
        
        assertThat(json1).isEqualTo(json2);
        
        // Verify fields are in alphabetical order
        assertThat(json1).matches(".*description.*maxRetries.*processorInfo.*stepName.*stepType.*");
    }

    @Test
    public void testComplexPipelineGraphOrdering() throws Exception {
        // Create a pipeline graph with pipelines in random order
        Map<String, Object> graph = new LinkedHashMap<>();
        Map<String, Object> pipelines = new LinkedHashMap<>();
        
        // Add pipelines in non-alphabetical order
        pipelines.put("z-pipeline", Map.of("name", "z-pipeline"));
        pipelines.put("a-pipeline", Map.of("name", "a-pipeline"));
        pipelines.put("m-pipeline", Map.of("name", "m-pipeline"));
        
        graph.put("pipelines", pipelines);
        graph.put("version", "1.0");
        graph.put("clusterId", "prod");
        
        String json = getObjectMapper().writeValueAsString(graph);
        
        // Verify root level ordering
        assertThat(json).matches(".*clusterId.*pipelines.*version.*");
        
        // Verify pipeline ordering within pipelines map
        int aPos = json.indexOf("a-pipeline");
        int mPos = json.indexOf("m-pipeline");
        int zPos = json.indexOf("z-pipeline");
        
        assertThat(aPos).isLessThan(mPos);
        assertThat(mPos).isLessThan(zPos);
    }

    @Test
    public void testSchemaFingerprintConsistency() throws Exception {
        // Two schemas that are logically identical but have different field order
        Map<String, Object> schema1 = new LinkedHashMap<>();
        schema1.put("type", "object");
        schema1.put("properties", Map.of(
            "chunkSize", Map.of("type", "integer", "default", 1000),
            "overlap", Map.of("type", "integer", "default", 100)
        ));
        schema1.put("required", new String[]{"chunkSize"});
        
        Map<String, Object> schema2 = new LinkedHashMap<>();
        schema2.put("required", new String[]{"chunkSize"});
        schema2.put("type", "object");
        schema2.put("properties", Map.of(
            "overlap", Map.of("default", 100, "type", "integer"),
            "chunkSize", Map.of("default", 1000, "type", "integer")
        ));
        
        String json1 = getObjectMapper().writeValueAsString(schema1);
        String json2 = getObjectMapper().writeValueAsString(schema2);
        
        // Should produce identical JSON for identical schemas
        assertThat(json1).isEqualTo(json2);
    }
}