package com.rokkon.pipeline.consul;

import com.rokkon.pipeline.consul.connection.ConsulConnectionManager;
import com.rokkon.pipeline.consul.test.ConsulTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import jakarta.inject.Inject;

/**
 * Integration test to verify Consul is running and we can inject dependencies.
 * This is our foundation - if this doesn't work, nothing else will.
 */
@QuarkusIntegrationTest
@QuarkusTestResource(ConsulTestResource.class)
class BasicConsulConnectionIT extends BasicConsulConnectionTestBase {
    
    @Inject
    ConsulConnectionManager connectionManager;
    
    @Override
    protected ConsulConnectionManager getConnectionManager() {
        return connectionManager;
    }
    
    @Override
    protected boolean expectRealConsul() {
        return true; // This is an integration test with real Consul
    }
}