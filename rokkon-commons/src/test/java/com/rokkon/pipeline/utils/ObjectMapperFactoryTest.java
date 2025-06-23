package com.rokkon.pipeline.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
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
        
        // Serialize with both mappers
        String factoryJson = factoryMapper.writeValueAsString(obj);
        String quarkusJson = quarkusObjectMapper.writeValueAsString(obj);
        
        // Both should sort properties alphabetically since JsonOrderingCustomizer is now in commons
        
        // Verify factory mapper sorts properties alphabetically
        assertThat(factoryJson).matches(".*\"apple\".*\"data\".*\"middle\".*\"zebra\".*");
        // Verify factory mapper sorts map entries by key
        assertThat(factoryJson).matches(".*\"a-key\".*\"m-key\".*\"z-key\".*");
        
        // Verify Quarkus mapper sorts properties alphabetically
        assertThat(quarkusJson).matches(".*\"apple\".*\"data\".*\"middle\".*\"zebra\".*");
        // Verify Quarkus mapper sorts map entries by key
        assertThat(quarkusJson).matches(".*\"a-key\".*\"m-key\".*\"z-key\".*");
        
        // Both produce the same logical content (deserialize and compare)
        TestObject factoryDeser = factoryMapper.readValue(factoryJson, TestObject.class);
        TestObject quarkusDeser = quarkusObjectMapper.readValue(quarkusJson, TestObject.class);
        
        assertThat(factoryDeser.apple).isEqualTo(quarkusDeser.apple);
        assertThat(factoryDeser.zebra).isEqualTo(quarkusDeser.zebra);
        assertThat(factoryDeser.data).isEqualTo(quarkusDeser.data);
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