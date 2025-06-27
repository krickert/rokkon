package com.rokkon.pipeline.consul;

import io.vertx.mutiny.ext.consul.ConsulClient;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base class for testing Consul configuration operations.
 * Provides common test logic for both unit and integration tests.
 */
public abstract class ConsulConfigSuccessFailTestBase {
    private static final Logger LOG = Logger.getLogger(ConsulConfigSuccessFailTestBase.class);
    
    protected abstract ConsulClient getConsulClient();
    protected abstract boolean isRealConsul();
    
    @Test
    void testSuccessWhenConfigExists() {
        // This test can work with both real and mock Consul
        
        ConsulClient consulClient = getConsulClient();
        
        // First, put configuration in Consul
        String yamlConfig = """
            rokkon:
              test:
                consul-loaded: "value-from-consul"
                nested:
                  property: "nested-value"
            """;
        
        consulClient.putValue("config/test-success", yamlConfig)
            .await().indefinitely();
        
        // Verify we can read it back
        String readValue = consulClient.getValue("config/test-success")
            .map(kv -> kv.getValue())
            .await().indefinitely();
        
        assertThat(readValue).isEqualTo(yamlConfig);
        LOG.info("✓ Configuration successfully stored in Consul KV at config/test-success");
    }
    
    @Test
    void testConfigNotFoundBehavior() {
        // This test can work with both real and mock Consul
        
        ConsulClient consulClient = getConsulClient();
        
        // Try to read a key that doesn't exist
        String nonExistentValue = consulClient.getValue("config/non-existent")
            .map(kv -> kv != null ? kv.getValue() : null)
            .await().indefinitely();
        
        assertThat(nonExistentValue).isNull();
        LOG.info("✓ Non-existent key returns null as expected");
    }
    
    @Test
    void testUpdateExistingConfig() {
        // This test can work with both real and mock Consul
        
        ConsulClient consulClient = getConsulClient();
        String key = "config/test-update";
        
        // Put initial value
        String initialConfig = """
            test:
              value: "initial"
            """;
        consulClient.putValue(key, initialConfig)
            .await().indefinitely();
        
        // Update value
        String updatedConfig = """
            test:
              value: "updated"
              new-property: "added"
            """;
        consulClient.putValue(key, updatedConfig)
            .await().indefinitely();
        
        // Read back
        String readValue = consulClient.getValue(key)
            .map(kv -> kv.getValue())
            .await().indefinitely();
        
        assertThat(readValue).isEqualTo(updatedConfig);
        LOG.info("✓ Configuration update successful");
    }
    
    @Test
    void testDeleteConfig() {
        // This test can work with both real and mock Consul
        
        ConsulClient consulClient = getConsulClient();
        String key = "config/test-delete";
        
        // Put value
        consulClient.putValue(key, "test-value")
            .await().indefinitely();
        
        // Verify it exists
        assertThat(consulClient.getValue(key)
            .map(kv -> kv != null)
            .await().indefinitely()).isTrue();
        
        // Delete it
        consulClient.deleteValue(key)
            .await().indefinitely();
        
        // Verify it's gone
        String deletedValue = consulClient.getValue(key)
            .map(kv -> kv != null ? kv.getValue() : null)
            .await().indefinitely();
        assertThat(deletedValue).isNull();
        
        LOG.info("✓ Configuration deletion successful");
    }
}