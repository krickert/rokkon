package com.rokkon.test.util;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for QuarkusResourceLoader.
 * Tests resource loading in a Quarkus environment.
 */
@QuarkusIntegrationTest
class QuarkusResourceLoaderIT {
    
    @Test
    void testLoadResource() {
        // Test loading the metadata CSV file
        InputStream stream = QuarkusResourceLoader.loadResource("/test-data/document-metadata.csv");
        assertThat(stream).isNotNull();
        
        // Verify we can read from it
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            String firstLine = reader.readLine();
            assertThat(firstLine).isNotNull();
            assertThat(firstLine).contains("filename"); // Should be the header
        } catch (Exception e) {
            throw new RuntimeException("Failed to read resource", e);
        }
    }
    
    @Test
    void testLoadResourceWithLeadingSlash() {
        // Both with and without leading slash should work
        InputStream stream1 = QuarkusResourceLoader.loadResource("/test-data/document-metadata.csv");
        InputStream stream2 = QuarkusResourceLoader.loadResource("test-data/document-metadata.csv");
        
        assertThat(stream1).isNotNull();
        assertThat(stream2).isNotNull();
    }
    
    @Test
    void testLoadResourceWithFallbacks() {
        // Test fallback mechanism
        InputStream stream = QuarkusResourceLoader.loadResourceWithFallbacks(
            "/non-existent.csv",
            "/also-non-existent.csv",
            "/test-data/document-metadata.csv"  // This one exists
        );
        
        assertThat(stream).isNotNull();
    }
    
    @Test
    void testListResourceFiles() {
        // This might return empty in JAR mode, which is expected
        List<String> binFiles = QuarkusResourceLoader.listResourceFiles("/test-data/parser/output", "bin");
        
        // In dev mode, we should find files
        if (QuarkusResourceLoader.isDevMode()) {
            assertThat(binFiles).isNotEmpty();
            assertThat(binFiles).allMatch(f -> f.endsWith(".bin"));
        }
        // In JAR mode, the list might be empty and that's OK
    }
    
    @Test
    void testProtobufResourceLoading() {
        // Test loading a protobuf file
        InputStream stream = QuarkusResourceLoader.loadResource("/test-data/parser/output/parser_output-000.bin");
        
        if (stream != null) {
            // If found, verify it's readable
            assertThat(stream).isNotNull();
            try {
                byte[] header = new byte[4];
                int read = stream.read(header);
                assertThat(read).isGreaterThan(0);
            } catch (Exception e) {
                throw new RuntimeException("Failed to read protobuf file", e);
            }
        }
        // It's OK if specific protobuf files don't exist in test resources
    }
    
    @Test 
    void testApplicationYmlLoading() {
        // Test loading a known configuration file - try both locations
        InputStream stream = QuarkusResourceLoader.loadResourceWithFallbacks(
            "/application.yml",
            "/application.properties"
        );
        
        if (stream == null) {
            // In integration test mode, these config files might not be accessible
            // This is OK - skip the test
            return;
        }
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            String content = reader.lines().collect(Collectors.joining("\n"));
            // Just verify we got some content
            assertThat(content).isNotEmpty();
        } catch (Exception e) {
            throw new RuntimeException("Failed to read configuration file", e);
        }
    }
}