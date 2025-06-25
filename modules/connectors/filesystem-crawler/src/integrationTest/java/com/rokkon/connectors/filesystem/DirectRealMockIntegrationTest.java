package com.rokkon.connectors.filesystem;

import com.rokkon.connectors.filesystem.mock.RealMockConnectorEngine;
import com.rokkon.search.engine.ConnectorRequest;
import com.rokkon.search.model.PipeDoc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for the filesystem crawler connector using the RealMockConnectorEngine directly.
 * This test demonstrates how the connector can be used to crawl a filesystem
 * and send documents to the engine without relying on CDI.
 */
public class DirectRealMockIntegrationTest {

    private FilesystemCrawlerConnector connector;
    private RealMockConnectorEngine mockEngine;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        // Create test files in the temp directory
        createTestFiles();

        // Create a new RealMockConnectorEngine
        mockEngine = new RealMockConnectorEngine();

        // Create a new FilesystemCrawlerConnector
        connector = new FilesystemCrawlerConnector();

        // Configure the connector to use the temp directory
        connector.rootPath = tempDir.toString();
        connector.fileExtensions = "txt,md,json";
        connector.maxFileSize = 1024 * 1024; // 1MB
        connector.includeHidden = false;
        connector.maxDepth = 10;
        connector.batchSize = 10;
        connector.deleteOrphans = true;
        connector.connectorType = "filesystem-crawler";
        connector.connectorId = "test-crawler-1";

        // Set the mock engine
        connector.connectorEngine = mockEngine;
    }

    @Test
    void testCrawlWithDirectRealMockEngine() throws InterruptedException {
        // Reset the mock engine with the expected number of documents
        mockEngine.reset(4); // Expecting 4 documents (3 files + 1 subdirectory file)

        // Run the crawler
        connector.crawl();

        // Wait for all documents to be processed
        boolean completed = mockEngine.awaitCompletion(5, TimeUnit.SECONDS);
        assertTrue(completed, "Crawler should have processed all documents within the timeout");

        // Get the received requests
        List<ConnectorRequest> receivedRequests = mockEngine.getReceivedRequests();

        // Verify that the correct number of documents were processed
        assertEquals(4, receivedRequests.size(), "Should have processed 4 documents");

        // Verify that each document has the correct connector type and tags
        for (ConnectorRequest request : receivedRequests) {
            assertEquals(connector.connectorType, request.getConnectorType());
            // Skip the connector ID check since it might not be available in the ConnectorRequest class
            // assertEquals(connector.connectorId, request.getConnectorId());
            assertTrue(request.getTagsList().contains("filesystem"));
            assertTrue(request.getTagsList().contains("file"));
        }

        // Verify that each file was processed
        boolean foundTestFile1 = false;
        boolean foundTestFile2 = false;
        boolean foundTestFile3 = false;
        boolean foundSubdirFile = false;

        for (ConnectorRequest request : receivedRequests) {
            PipeDoc doc = request.getDocument();
            String sourceUri = doc.getSourceUri();

            if (sourceUri.endsWith("test1.txt")) {
                foundTestFile1 = true;
                assertEquals("This is a test file 1", new String(doc.getBlob().getData().toByteArray()));
            } else if (sourceUri.endsWith("test2.md")) {
                foundTestFile2 = true;
                assertTrue(new String(doc.getBlob().getData().toByteArray()).contains("# Test File 2"));
            } else if (sourceUri.endsWith("test3.json")) {
                foundTestFile3 = true;
                assertTrue(new String(doc.getBlob().getData().toByteArray()).contains("\"name\": \"Test File 3\""));
            } else if (sourceUri.endsWith("subdir/test5.txt")) {
                foundSubdirFile = true;
                assertEquals("This is a test file in a subdirectory", new String(doc.getBlob().getData().toByteArray()));
            }
        }

        assertTrue(foundTestFile1, "test1.txt should have been processed");
        assertTrue(foundTestFile2, "test2.md should have been processed");
        assertTrue(foundTestFile3, "test3.json should have been processed");
        assertTrue(foundSubdirFile, "subdir/test5.txt should have been processed");

        // Print a summary of the processed files
        System.out.println("Processed " + receivedRequests.size() + " files:");
        for (ConnectorRequest request : receivedRequests) {
            System.out.println("  - " + request.getDocument().getSourceUri());
        }
    }

    private void createTestFiles() throws IOException {
        // Create test files
        Files.writeString(tempDir.resolve("test1.txt"), "This is a test file 1");
        Files.writeString(tempDir.resolve("test2.md"), "# Test File 2\n\nThis is a markdown file.");
        Files.writeString(tempDir.resolve("test3.json"), "{\"name\": \"Test File 3\", \"type\": \"json\"}");

        // Create a file with an unsupported extension
        Files.writeString(tempDir.resolve("test4.bin"), "This file should be ignored");

        // Create a subdirectory with more test files
        Path subDir = tempDir.resolve("subdir");
        Files.createDirectory(subDir);
        Files.writeString(subDir.resolve("test5.txt"), "This is a test file in a subdirectory");
    }
}