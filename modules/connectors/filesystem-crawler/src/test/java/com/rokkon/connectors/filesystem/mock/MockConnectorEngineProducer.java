package com.rokkon.connectors.filesystem.mock;

import com.rokkon.search.engine.ConnectorEngine;
import io.quarkus.arc.DefaultBean;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import org.jboss.logging.Logger;

/**
 * CDI producer for the MockConnectorEngine in test mode.
 * This class provides a singleton instance of the MockConnectorEngine
 * that can be injected into the FilesystemCrawlerConnector during tests.
 */
@ApplicationScoped
public class MockConnectorEngineProducer {

    private static final Logger LOG = Logger.getLogger(MockConnectorEngineProducer.class);

    private final MockConnectorEngine mockEngine = new MockConnectorEngine();

    /**
     * Produces a MockConnectorEngine instance for test mode.
     * 
     * @return A singleton instance of MockConnectorEngine
     */
    @Produces
    @Singleton
    @DefaultBean
    public ConnectorEngine produceMockConnectorEngine() {
        LOG.info("Producing MockConnectorEngine for test mode");
        return mockEngine;
    }

    /**
     * Get the singleton MockConnectorEngine instance.
     * This can be used to configure the mock engine before tests.
     * 
     * @return The singleton MockConnectorEngine instance
     */
    public MockConnectorEngine getMockEngine() {
        return mockEngine;
    }
}
