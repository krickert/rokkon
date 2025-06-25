package com.rokkon.connectors.filesystem.mock;

import com.rokkon.search.engine.ConnectorEngine;
import com.rokkon.search.engine.ConnectorRequest;
import com.rokkon.search.engine.ConnectorResponse;
import io.smallrye.mutiny.Uni;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * A real implementation of the ConnectorEngine interface for integration testing.
 * This class can be used in integration tests to simulate the behavior of the real ConnectorEngine.
 */
public class RealMockConnectorEngine implements ConnectorEngine {
    private static final Logger LOG = Logger.getLogger(RealMockConnectorEngine.class);

    private final List<ConnectorRequest> receivedRequests = new ArrayList<>();
    private CountDownLatch latch;
    private Function<ConnectorRequest, ConnectorResponse> responseFunction;

    /**
     * Create a new RealMockConnectorEngine with default behavior.
     */
    public RealMockConnectorEngine() {
        this(0);
    }

    /**
     * Create a new RealMockConnectorEngine with a latch for the expected number of documents.
     *
     * @param expectedDocuments The number of documents expected to be processed
     */
    public RealMockConnectorEngine(int expectedDocuments) {
        this.latch = expectedDocuments > 0 ? new CountDownLatch(expectedDocuments) : null;
        this.responseFunction = this::defaultResponseFunction;
    }

    /**
     * Set a custom response function for this mock.
     *
     * @param responseFunction A function that takes a ConnectorRequest and returns a ConnectorResponse
     */
    public void setResponseFunction(Function<ConnectorRequest, ConnectorResponse> responseFunction) {
        this.responseFunction = responseFunction;
    }

    /**
     * Reset the mock to its initial state.
     */
    public void reset() {
        receivedRequests.clear();
    }

    /**
     * Reset the mock with a new expected document count.
     *
     * @param expectedDocuments The number of documents expected to be processed
     */
    public void reset(int expectedDocuments) {
        receivedRequests.clear();
        this.latch = expectedDocuments > 0 ? new CountDownLatch(expectedDocuments) : null;
    }

    /**
     * Process a connector document request.
     *
     * @param request The connector request
     * @return A Uni containing the connector response
     */
    @Override
    public Uni<ConnectorResponse> processConnectorDoc(ConnectorRequest request) {
        LOG.debug("RealMockConnectorEngine received request: " + request.getDocument().getSourceUri());

        // Store the request for later verification
        receivedRequests.add(request);

        // Count down the latch if it exists
        if (latch != null) {
            latch.countDown();
        }

        // Generate a response using the response function
        ConnectorResponse response = responseFunction.apply(request);

        // Return the response wrapped in a Uni
        return Uni.createFrom().item(response);
    }

    /**
     * Get all received requests.
     *
     * @return A list of all received requests
     */
    public List<ConnectorRequest> getReceivedRequests() {
        return receivedRequests;
    }

    /**
     * Wait for all expected documents to be processed.
     *
     * @param timeout The maximum time to wait
     * @param unit The time unit of the timeout
     * @return true if all documents were processed, false if the timeout was reached
     * @throws InterruptedException If the thread is interrupted while waiting
     */
    public boolean awaitCompletion(long timeout, TimeUnit unit) throws InterruptedException {
        if (latch == null) {
            return true;
        }
        return latch.await(timeout, unit);
    }

    /**
     * Default response function that returns a successful response.
     *
     * @param request The connector request
     * @return A successful connector response
     */
    private ConnectorResponse defaultResponseFunction(ConnectorRequest request) {
        return ConnectorResponse.newBuilder()
                .setStreamId("test-stream-id-" + receivedRequests.size())
                .setAccepted(true)
                .setMessage("Document accepted by RealMockConnectorEngine")
                .build();
    }
}