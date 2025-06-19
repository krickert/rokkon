package com.rokkon.search.config.pipeline.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class SchemaReferenceTest {

    @Inject
    ObjectMapper objectMapper;

    @Test
    public void testSerialization() throws Exception {
        SchemaReference ref = new SchemaReference("test-schema", 2);
        String json = objectMapper.writeValueAsString(ref);
        
        assertTrue(json.contains("\"subject\":\"test-schema\""));
        assertTrue(json.contains("\"version\":2"));
    }

    @Test
    public void testDeserialization() throws Exception {
        String json = "{\"subject\":\"test-schema\",\"version\":2}";
        SchemaReference ref = objectMapper.readValue(json, SchemaReference.class);
        
        assertEquals("test-schema", ref.subject());
        assertEquals(2, ref.version());
    }

    @Test
    public void testToIdentifier() {
        SchemaReference ref = new SchemaReference("my-schema", 5);
        assertEquals("my-schema:5", ref.toIdentifier());
    }

    @Test
    public void testValidation() {
        // Valid cases
        assertDoesNotThrow(() -> new SchemaReference("valid-subject", 1));
        assertDoesNotThrow(() -> new SchemaReference("another-subject", 10));
        
        // Invalid cases
        assertThrows(IllegalArgumentException.class, () -> new SchemaReference(null, 1));
        assertThrows(IllegalArgumentException.class, () -> new SchemaReference("", 1));
        assertThrows(IllegalArgumentException.class, () -> new SchemaReference("   ", 1));
        assertThrows(IllegalArgumentException.class, () -> new SchemaReference("valid", null));
        assertThrows(IllegalArgumentException.class, () -> new SchemaReference("valid", 0));
        assertThrows(IllegalArgumentException.class, () -> new SchemaReference("valid", -1));
    }

    @Test
    public void testRoundTrip() throws Exception {
        SchemaReference original = new SchemaReference("round-trip-test", 3);
        String json = objectMapper.writeValueAsString(original);
        SchemaReference deserialized = objectMapper.readValue(json, SchemaReference.class);
        
        assertEquals(original.subject(), deserialized.subject());
        assertEquals(original.version(), deserialized.version());
        assertEquals(original.toIdentifier(), deserialized.toIdentifier());
    }
}