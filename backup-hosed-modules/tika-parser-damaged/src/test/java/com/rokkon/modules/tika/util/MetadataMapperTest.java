package com.rokkon.modules.tika.util;

import org.apache.tika.metadata.Metadata;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MetadataMapper utility class.
 */
class MetadataMapperTest {

    @Test
    void testToMapWithNullMetadata() {
        Map<String, String> result = MetadataMapper.toMap(null);
        
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testToMapWithEmptyMetadata() {
        Metadata metadata = new Metadata();
        
        Map<String, String> result = MetadataMapper.toMap(metadata);
        
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testToMapWithSingleValues() {
        Metadata metadata = new Metadata();
        metadata.set("title", "Test Document");
        metadata.set("Content-Type", "text/plain");
        metadata.set("dc:creator", "Test Author");
        
        Map<String, String> result = MetadataMapper.toMap(metadata);
        
        assertEquals(3, result.size());
        assertEquals("Test Document", result.get("title"));
        assertEquals("text/plain", result.get("content_type"));
        assertEquals("Test Author", result.get("dc_creator"));
    }

    @Test
    void testToMapWithMultipleValues() {
        Metadata metadata = new Metadata();
        metadata.add("keywords", "keyword1");
        metadata.add("keywords", "keyword2");
        metadata.add("keywords", "keyword3");
        
        Map<String, String> result = MetadataMapper.toMap(metadata);
        
        assertEquals(1, result.size());
        assertEquals("keyword1; keyword2; keyword3", result.get("keywords"));
    }

    @Test
    void testToMapWithNullAndEmptyValues() {
        Metadata metadata = new Metadata();
        metadata.set("title", "Valid Title");
        metadata.set("empty", "");
        metadata.set("null-value", null);
        metadata.set("whitespace", "   ");
        
        Map<String, String> result = MetadataMapper.toMap(metadata);
        
        assertEquals(1, result.size());
        assertEquals("Valid Title", result.get("title"));
        assertFalse(result.containsKey("empty"));
        assertFalse(result.containsKey("null_value"));
        assertFalse(result.containsKey("whitespace"));
    }

    @Test
    void testKeyNormalization() {
        Metadata metadata = new Metadata();
        metadata.set("Content-Type", "text/plain");
        metadata.set("dc:title", "Document Title");
        metadata.set("Image Width", "800");
        metadata.set("meta.keywords", "test");
        
        Map<String, String> result = MetadataMapper.toMap(metadata);
        
        assertTrue(result.containsKey("content_type"));
        assertTrue(result.containsKey("dc_title"));
        assertTrue(result.containsKey("image_width"));
        assertTrue(result.containsKey("meta_keywords"));
    }

    @Test
    void testGetValueWithFallbacks() {
        Metadata metadata = new Metadata();
        metadata.set("dc:title", "Document Title");
        metadata.set("subject", "Document Subject");
        
        // Test primary key found
        String result1 = MetadataMapper.getValue(metadata, "dc:title", "title", "Title");
        assertEquals("Document Title", result1);
        
        // Test fallback key used
        String result2 = MetadataMapper.getValue(metadata, "title", "subject", "Title");
        assertEquals("Document Subject", result2);
        
        // Test no key found
        String result3 = MetadataMapper.getValue(metadata, "nonexistent", "also-nonexistent");
        assertEquals("", result3);
    }

    @Test
    void testGetValueWithNullMetadata() {
        String result = MetadataMapper.getValue(null, "title", "dc:title");
        assertEquals("", result);
    }

    @Test
    void testVeryLongValueFiltering() {
        Metadata metadata = new Metadata();
        String longValue = "A".repeat(15000); // Very long value
        metadata.set("long-field", longValue);
        metadata.set("normal-field", "Normal Value");
        
        Map<String, String> result = MetadataMapper.toMap(metadata);
        
        assertEquals(1, result.size());
        assertEquals("Normal Value", result.get("normal_field"));
        assertFalse(result.containsKey("long_field"));
    }

    @Test
    void testBinaryDataFiltering() {
        Metadata metadata = new Metadata();
        // Create a string with binary-like data (lots of non-printable characters)
        StringBuilder binaryData = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            binaryData.append((char) (i % 32)); // Non-printable characters
        }
        
        metadata.set("binary-field", binaryData.toString());
        metadata.set("text-field", "This is normal text content");
        
        Map<String, String> result = MetadataMapper.toMap(metadata);
        
        assertEquals(1, result.size());
        assertEquals("This is normal text content", result.get("text_field"));
        assertFalse(result.containsKey("binary_field"));
    }

    @Test
    void testShortStringsNotFilteredAsBinary() {
        Metadata metadata = new Metadata();
        // Use characters that are definitely non-printable but won't be trimmed  
        metadata.set("short-binary", "a\u0001b"); // Short string with non-printable chars
        metadata.set("normal-field", "Normal");
        
        Map<String, String> result = MetadataMapper.toMap(metadata);
        
        // Short strings with binary characters should NOT be filtered out
        assertEquals(2, result.size());
        assertTrue(result.containsKey("short_binary"));
        assertTrue(result.containsKey("normal_field"));
    }
}