package com.rokkon.connectors.filesystem;

import com.rokkon.connectors.filesystem.mock.MockConnectorEngine;
import com.rokkon.connectors.filesystem.mock.MockConnectorEngineProducer;
import com.rokkon.search.engine.ConnectorRequest;
import com.rokkon.search.engine.ConnectorResponse;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class FilesystemCrawlerConnectorTest {

    @Inject
    FilesystemCrawlerConnector connector;

    @Inject
    MockConnectorEngineProducer mockEngineProducer;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        // Create test files in the temp directory
        createTestFiles();

        // Configure the connector to use the temp directory
        connector.rootPath = tempDir.toString();
        connector.fileExtensions = "txt,md,json";
        connector.maxFileSize = 1024 * 1024; // 1MB
        connector.includeHidden = false;
        connector.maxDepth = 10;
        connector.batchSize = 10;
        connector.deleteOrphans = true;

        // Reset the mock engine
        MockConnectorEngine mockEngine = mockEngineProducer.getMockEngine();
        mockEngine.reset();

        // Configure the mock engine to return a successful response
        mockEngine.setResponseFunction(request -> ConnectorResponse.newBuilder()
                .setStreamId("test-stream-id")
                .setAccepted(true)
                .setMessage("Document accepted")
                .build());
    }

    @Test
    void testCrawl() throws InterruptedException {
        // Get the mock engine
        MockConnectorEngine mockEngine = mockEngineProducer.getMockEngine();

        // Reset the mock engine with the expected number of documents
        mockEngine.reset(3); // Expecting 3 documents

        // Run the crawler
        connector.crawl();

        // Wait for all documents to be processed
        boolean completed = mockEngine.awaitCompletion(5, TimeUnit.SECONDS);
        assertTrue(completed, "Crawler should have processed all documents within the timeout");

        // Get the received requests
        List<ConnectorRequest> receivedRequests = mockEngine.getReceivedRequests();

        // Verify that the correct number of files were processed
        assertEquals(3, receivedRequests.size(), "Should have processed 3 documents");

        // Verify that each request has the correct connector type and tags
        for (ConnectorRequest request : receivedRequests) {
            assertEquals(connector.connectorType, request.getConnectorType());
            assertEquals(connector.connectorId, request.getConnectorId());
            assertTrue(request.getTagsList().contains("filesystem"));
            assertTrue(request.getTagsList().contains("file"));
        }

        // Verify that each file was processed
        boolean foundTestFile1 = false;
        boolean foundTestFile2 = false;
        boolean foundTestFile3 = false;

        for (ConnectorRequest request : receivedRequests) {
            String sourceUri = request.getDocument().getSourceUri();
            if (sourceUri.endsWith("test1.txt")) {
                foundTestFile1 = true;
            } else if (sourceUri.endsWith("test2.md")) {
                foundTestFile2 = true;
            } else if (sourceUri.endsWith("test3.json")) {
                foundTestFile3 = true;
            }
        }

        assertTrue(foundTestFile1, "test1.txt should have been processed");
        assertTrue(foundTestFile2, "test2.md should have been processed");
        assertTrue(foundTestFile3, "test3.json should have been processed");
    }

    @Test
    void testOrphanDetection() throws IOException, InterruptedException {
        // Get the mock engine
        MockConnectorEngine mockEngine = mockEngineProducer.getMockEngine();

        // Reset the mock engine for the first crawl
        mockEngine.reset(3); // Expecting 3 documents initially

        // First crawl to establish the baseline
        connector.crawl();

        // Wait for all documents to be processed
        boolean completed = mockEngine.awaitCompletion(5, TimeUnit.SECONDS);
        assertTrue(completed, "Crawler should have processed all documents within the timeout");

        // Reset the mock engine for the second crawl
        mockEngine.reset();

        // Delete one of the files
        Files.delete(tempDir.resolve("test1.txt"));

        // Run the crawler again
        connector.crawl();

        // Get the received requests from the second crawl
        List<ConnectorRequest> receivedRequests = mockEngine.getReceivedRequests();

        // Verify that the orphaned file was processed
        boolean foundOrphanedFile = false;

        for (ConnectorRequest request : receivedRequests) {
            if (request.getTagsList().contains("orphaned")) {
                foundOrphanedFile = true;
                assertTrue(request.getDocument().getMetadataMap().containsKey("orphaned"));
                assertEquals("true", request.getDocument().getMetadataMap().get("orphaned"));
            }
        }

        assertTrue(foundOrphanedFile, "Orphaned file should have been processed");
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
