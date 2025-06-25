package com.rokkon.connectors.filesystem;

import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class FilesystemCrawlerHealthCheckTest {

    @Inject
    FilesystemCrawlerHealthCheck healthCheck;

    @Inject
    FilesystemCrawlerConnector connector;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // Configure the connector with test values
        connector.fileExtensions = "txt,md,json";
        connector.maxFileSize = 1024 * 1024; // 1MB
        connector.includeHidden = false;
        connector.maxDepth = 10;
    }

    @Test
    void testHealthCheckWithValidPath() {
        // Set the root path to the temp directory
        healthCheck.rootPath = tempDir.toString();
        
        // Call the health check
        HealthCheckResponse response = healthCheck.call();
        
        // Verify the response
        assertEquals("filesystem-crawler", response.getName());
        assertEquals(HealthCheckResponse.Status.UP, response.getStatus());
        
        // Verify the data
        assertTrue(response.getData().isPresent());
        assertTrue(response.getData().get().containsKey("config"));
        
        @SuppressWarnings("unchecked")
        var config = (java.util.Map<String, Object>) response.getData().get().get("config");
        
        assertEquals(tempDir.toString(), config.get("rootPath"));
        assertEquals(true, config.get("rootPathExists"));
        assertEquals(true, config.get("rootPathReadable"));
    }

    @Test
    void testHealthCheckWithNonExistentPath() {
        // Set the root path to a non-existent directory
        healthCheck.rootPath = tempDir.resolve("non-existent").toString();
        
        // Call the health check
        HealthCheckResponse response = healthCheck.call();
        
        // Verify the response
        assertEquals("filesystem-crawler", response.getName());
        assertEquals(HealthCheckResponse.Status.DOWN, response.getStatus());
        
        // Verify the data
        assertTrue(response.getData().isPresent());
        assertTrue(response.getData().get().containsKey("config"));
        
        @SuppressWarnings("unchecked")
        var config = (java.util.Map<String, Object>) response.getData().get().get("config");
        
        assertEquals(healthCheck.rootPath, config.get("rootPath"));
        assertEquals(false, config.get("rootPathExists"));
    }

    @Test
    void testHealthCheckWithUnreadablePath() throws IOException {
        // Skip this test on Windows as it doesn't support POSIX file permissions
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            return;
        }
        
        // Create a directory with no read permissions
        Path unreadableDir = Files.createDirectory(tempDir.resolve("unreadable"));
        try {
            Files.setPosixFilePermissions(unreadableDir, PosixFilePermissions.fromString("-wx------"));
            
            // Set the root path to the unreadable directory
            healthCheck.rootPath = unreadableDir.toString();
            
            // Call the health check
            HealthCheckResponse response = healthCheck.call();
            
            // Verify the response
            assertEquals("filesystem-crawler", response.getName());
            assertEquals(HealthCheckResponse.Status.DOWN, response.getStatus());
            
            // Verify the data
            assertTrue(response.getData().isPresent());
            assertTrue(response.getData().get().containsKey("config"));
            
            @SuppressWarnings("unchecked")
            var config = (java.util.Map<String, Object>) response.getData().get().get("config");
            
            assertEquals(healthCheck.rootPath, config.get("rootPath"));
            assertEquals(true, config.get("rootPathExists"));
            assertEquals(false, config.get("rootPathReadable"));
        } finally {
            // Restore permissions so the directory can be deleted
            Files.setPosixFilePermissions(unreadableDir, PosixFilePermissions.fromString("rwx------"));
        }
    }
}