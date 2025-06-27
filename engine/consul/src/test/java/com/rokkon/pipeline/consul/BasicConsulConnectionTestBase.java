package com.rokkon.pipeline.consul;

import com.rokkon.pipeline.consul.connection.ConsulConnectionManager;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base class for testing Consul connection functionality.
 * Provides common test logic that can be used by both unit and integration tests.
 */
public abstract class BasicConsulConnectionTestBase {
    private static final Logger LOG = Logger.getLogger(BasicConsulConnectionTestBase.class);
    
    protected abstract ConsulConnectionManager getConnectionManager();
    protected abstract boolean expectRealConsul();
    
    @Test
    void testConsulConnectionManager() {
        // Verify the connection manager is injected
        ConsulConnectionManager connectionManager = getConnectionManager();
        assertThat(connectionManager).isNotNull();
        
        if (expectRealConsul()) {
            // For integration tests, verify we have a real Consul client
            assertThat(connectionManager.getMutinyClient()).isPresent();
            LOG.info("✓ Consul is running and ConsulConnectionManager has active client");
        } else {
            // For unit tests, we might have a mock or empty client
            LOG.info("✓ ConsulConnectionManager is injected (mock/test mode)");
        }
    }
}