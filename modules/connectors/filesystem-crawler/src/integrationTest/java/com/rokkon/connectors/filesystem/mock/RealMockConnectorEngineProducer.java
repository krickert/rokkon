package com.rokkon.connectors.filesystem.mock;

import com.rokkon.search.engine.ConnectorEngine;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import org.jboss.logging.Logger;

/**
 * CDI producer for the RealMockConnectorEngine in integration test mode.
 * This class provides a singleton instance of the RealMockConnectorEngine
 * that can be injected into the FilesystemCrawlerConnector during integration tests.
 */
@ApplicationScoped
public class RealMockConnectorEngineProducer {

    private static final Logger LOG = Logger.getLogger(RealMockConnectorEngineProducer.class);

    private final RealMockConnectorEngine mockEngine = new RealMockConnectorEngine();

    /**
     * Produces a RealMockConnectorEngine instance for integration test mode.
     * 
     * @return A singleton instance of RealMockConnectorEngine
     */
    @Produces
    @Singleton
    @DefaultBean
    public ConnectorEngine produceRealMockConnectorEngine() {
        LOG.info("Producing RealMockConnectorEngine for integration test mode");
        return mockEngine;
    }

    /**
     * Get the singleton RealMockConnectorEngine instance.
     * This can be used to configure the mock engine before tests.
     * 
     * @return The singleton RealMockConnectorEngine instance
     */
    public RealMockConnectorEngine getMockEngine() {
        return mockEngine;
    }
}