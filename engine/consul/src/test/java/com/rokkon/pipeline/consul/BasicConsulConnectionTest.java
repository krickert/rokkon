package com.rokkon.pipeline.consul;

import com.rokkon.pipeline.consul.connection.ConsulConnectionManager;
import com.rokkon.pipeline.consul.test.ConsulTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Most basic test to verify Consul is running and we can inject dependencies.
 * This is our foundation - if this doesn't work, nothing else will.
 */
@QuarkusTest
@QuarkusTestResource(ConsulTestResource.class)
class BasicConsulConnectionTest {
    
    @Inject
    ConsulConnectionManager connectionManager;
    
    @Test
    void testConsulIsRunning() {
        // Verify the connection manager is injected
        assertThat(connectionManager).isNotNull();
        
        // Verify we have a Consul client
        assertThat(connectionManager.getClient()).isPresent();
        
        // That's it - we have Consul running and can inject our beans
        System.out.println("✓ Consul is running and ConsulConnectionManager is injected");
    }
}