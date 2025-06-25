package com.rokkon.connectors.filesystem.mock;

import com.rokkon.search.engine.ConnectorEngine;
import com.rokkon.search.engine.ConnectorRequest;
import com.rokkon.search.engine.ConnectorResponse;
import com.rokkon.search.model.PipeDoc;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple test for the MockConnectorEngine.
 * This test verifies that the MockConnectorEngine is working correctly.
 */
@QuarkusTest
public class MockConnectorEngineTest {

    @Inject
    ConnectorEngine connectorEngine;

    @Inject
    MockConnectorEngineProducer mockEngineProducer;

    @Test
    void testMockConnectorEngine() throws InterruptedException {
        // Verify that the injected ConnectorEngine is our MockConnectorEngine
        assertTrue(connectorEngine instanceof MockConnectorEngine, 
                "Injected ConnectorEngine should be a MockConnectorEngine");
        
        // Get the mock engine from the producer
        MockConnectorEngine mockEngine = mockEngineProducer.getMockEngine();
        
        // Verify that the injected ConnectorEngine is the same instance as the one from the producer
        assertSame(connectorEngine, mockEngine, 
                "Injected ConnectorEngine should be the same instance as the one from the producer");
        
        // Reset the mock engine with the expected number of documents
        mockEngine.reset(1); // Expecting 1 document
        
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
        
        // Wait for all documents to be processed
        boolean completed = mockEngine.awaitCompletion(1, TimeUnit.SECONDS);
        assertTrue(completed, "Mock engine should have processed all documents within the timeout");
        
        // Get the received requests
        assertEquals(1, mockEngine.getReceivedRequests().size(), 
                "Mock engine should have received 1 request");
    }
}