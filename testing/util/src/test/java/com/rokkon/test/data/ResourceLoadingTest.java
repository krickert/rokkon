package com.rokkon.test.data;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class ResourceLoadingTest {

    @Test
    void testResourceLoading() {
        // Test if we can load a known resource
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        
        // Try to load the first tika request file
        String resourcePath = "test-data/tika/requests/tika_request_000.bin";
        
        URL url = cl.getResource(resourcePath);
        System.out.println("URL for " + resourcePath + ": " + url);
        
        try (InputStream is = cl.getResourceAsStream(resourcePath)) {
            assertThat(is).isNotNull();
            System.out.println("Successfully loaded resource: " + resourcePath);
        } catch (Exception e) {
            System.err.println("Failed to load resource: " + e.getMessage());
            throw new AssertionError("Could not load resource: " + resourcePath, e);
        }
    }

    @Test
    void testApplicationPropertiesLoading() {
        // Test if basic resources work
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        String resourcePath = "application.properties";
        
        try (InputStream is = cl.getResourceAsStream(resourcePath)) {
            assertThat(is).isNotNull();
            System.out.println("Successfully loaded application.properties");
        } catch (Exception e) {
            throw new AssertionError("Could not load application.properties", e);
        }
    }
}