package com.rokkon.connectors.filesystem.mock;

import com.rokkon.search.engine.ConnectorRequest;
import com.rokkon.search.engine.ConnectorResponse;
import com.rokkon.search.model.PipeDoc;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple unit test for the MockConnectorEngine.
 * This test verifies that the MockConnectorEngine is working correctly without relying on CDI.
 */
public class MockConnectorEngineUnitTest {

    @Test
    void testMockConnectorEngine() throws InterruptedException {
        // Create a new MockConnectorEngine
        MockConnectorEngine mockEngine = new MockConnectorEngine(1); // Expecting 1 document
        
        // Create a simple request
        ConnectorRequest request = ConnectorRequest.newBuilder()
                .setConnectorType("test")
                .setConnectorId("test-1")
                .setDocument(PipeDoc.newBuilder().setId("test-doc").build())
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
        assertEquals("test-1", receivedRequest.getConnectorId(), "Connector ID should be test-1");
        assertEquals("test-doc", receivedRequest.getDocument().getId(), "Document ID should be test-doc");
    }
}