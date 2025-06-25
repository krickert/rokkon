package com.rokkon.connectors.filesystem;

import com.rokkon.connectors.filesystem.mock.RealMockConnectorEngine;
import com.rokkon.search.engine.ConnectorRequest;
import com.rokkon.search.engine.ConnectorResponse;
import com.rokkon.search.model.PipeDoc;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A simpler version of the direct integration test that just verifies that the RealMockConnectorEngine
 * can be used with a simple request.
 */
public class SimplerDirectIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void testSimpleRequest() throws IOException, InterruptedException {
        // Create a test file
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "This is a test file");

        // Create a new RealMockConnectorEngine
        RealMockConnectorEngine mockEngine = new RealMockConnectorEngine(1); // Expecting 1 document

        // Create a simple request
        ConnectorRequest request = ConnectorRequest.newBuilder()
                .setConnectorType("test")
                // .setConnectorId("test-1") // This method doesn't exist in the current version
                .setDocument(PipeDoc.newBuilder()
                        .setId("test-doc")
                        .setSourceUri("file://" + testFile.toAbsolutePath())
                        .build())
                .build();

        // Send the request to the mock engine
        ConnectorResponse response = mockEngine.processConnectorDoc(request).await().indefinitely();

        // Verify the response
        assertNotNull(response, "Response should not be null");
        assertTrue(response.getAccepted(), "Response should be accepted");
        assertEquals("test-stream-id-1", response.getStreamId(), "Stream ID should be test-stream-id-1");

        // Wait for all documents to be processed
        boolean completed = mockEngine.awaitCompletion(1, TimeUnit.SECONDS);
        assertTrue(completed, "Mock engine should have processed all documents within the timeout");

        // Get the received requests
        assertEquals(1, mockEngine.getReceivedRequests().size(),
                "Mock engine should have received 1 request");

        // Verify the received request
        ConnectorRequest receivedRequest = mockEngine.getReceivedRequests().get(0);
        assertEquals("test", receivedRequest.getConnectorType(), "Connector type should be test");
        // assertEquals("test-1", receivedRequest.getConnectorId(), "Connector ID should be test-1"); // This method doesn't exist in the current version
        assertEquals("test-doc", receivedRequest.getDocument().getId(), "Document ID should be test-doc");
        assertEquals("file://" + testFile.toAbsolutePath(), receivedRequest.getDocument().getSourceUri(), "Source URI should match the test file");
    }
}