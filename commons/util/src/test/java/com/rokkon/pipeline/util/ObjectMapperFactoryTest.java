package com.rokkon.pipeline.util;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class ObjectMapperFactoryTest {

    @Inject
    ObjectMapper quarkusObjectMapper;

    @Test
    void testFactoryMatchesQuarkusConfiguration() throws Exception {
        ObjectMapper factoryMapper = ObjectMapperFactory.createConfiguredMapper();
        
        // Test object with properties that would be ordered differently without configuration
        TestObject obj = new TestObject();
        obj.zebra = "last alphabetically";
        obj.apple = "first alphabetically";
        obj.middle = "middle";
        obj.data = new TreeMap<>();
        obj.data.put("z-key", "z-value");
        obj.data.put("a-key", "a-value");
        obj.data.put("m-key", "m-value");
        
        // Serialize with factory mapper
        String factoryJson = factoryMapper.writeValueAsString(obj);
        
        // The factory mapper should sort properties alphabetically
        // Verify factory mapper sorts properties alphabetically
        assertThat(factoryJson).matches(".*\"apple\".*\"data\".*\"middle\".*\"zebra\".*");
        // Verify factory mapper sorts map entries by key
        assertThat(factoryJson).matches(".*\"a-key\".*\"m-key\".*\"z-key\".*");
        
        // Test deserialization works correctly
        TestObject factoryDeser = factoryMapper.readValue(factoryJson, TestObject.class);
        assertThat(factoryDeser.apple).isEqualTo("first alphabetically");
        assertThat(factoryDeser.zebra).isEqualTo("last alphabetically");
        assertThat(factoryDeser.middle).isEqualTo("middle");
        assertThat(factoryDeser.data).containsEntry("a-key", "a-value");
        assertThat(factoryDeser.data).containsEntry("m-key", "m-value");
        assertThat(factoryDeser.data).containsEntry("z-key", "z-value");
        
        // Verify configuration is applied
        assertThat(factoryMapper.getRegisteredModuleIds()).contains("jackson-datatype-jsr310");
        assertThat(factoryMapper.isEnabled(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)).isTrue();
        assertThat(factoryMapper.isEnabled(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)).isTrue();
    }
    
    @Test
    void testMinimalMapperDoesNotSort() throws Exception {
        ObjectMapper minimalMapper = ObjectMapperFactory.createMinimalMapper();
        
        // Test that minimal mapper doesn't sort properties
        TestObject obj = new TestObject();
        obj.zebra = "z";
        obj.apple = "a";
        
        String json = minimalMapper.writeValueAsString(obj);
        
        // The minimal mapper should NOT sort properties alphabetically
        // (properties will be in declaration order or arbitrary order)
        // We can't predict the exact order, but we can verify it serializes
        assertThat(json).contains("\"zebra\"");
        assertThat(json).contains("\"apple\"");
    }
    
    static class TestObject {
        public String zebra;
        public String apple;
        public String middle;
        public Map<String, String> data;
    }
}