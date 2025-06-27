package com.rokkon.pipeline.consul;

import io.vertx.mutiny.ext.consul.ConsulClient;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base class for testing isolated Consul KV writes for parallel test execution.
 * Each test gets its own unique namespace in Consul KV to avoid conflicts.
 */
public abstract class IsolatedConsulKvTestBase {
    private static final Logger LOG = Logger.getLogger(IsolatedConsulKvTestBase.class);
    
    protected String testNamespace;
    protected final ConcurrentHashMap<String, Boolean> writtenKeys = new ConcurrentHashMap<>();
    
    protected abstract ConsulClient getConsulClient();
    protected abstract boolean isRealConsul();
    
    @BeforeEach
    void setup() {
        // Create unique namespace for this test
        testNamespace = "test/" + getClass().getSimpleName() + "/" + UUID.randomUUID().toString();
        LOG.infof("Test namespace: %s", testNamespace);
    }
    
    @AfterEach
    void cleanup() {
        if (isRealConsul()) {
            // Clean up all keys written by this test
            ConsulClient consulClient = getConsulClient();
            writtenKeys.keySet().forEach(key -> {
                consulClient.deleteValue(key)
                    .onFailure().recoverWithNull()
                    .await().indefinitely();
            });
        }
        writtenKeys.clear();
    }
    
    @Test
    void testIsolatedWrites() {
        ConsulClient consulClient = getConsulClient();
        String key = testNamespace + "/config/app";
        String value = """
            application:
              name: test-app
              version: 1.0.0
            """;
        
        // Write to isolated namespace
        writeValue(key, value);
        
        // Read back
        String readValue = consulClient.getValue(key)
            .map(kv -> kv.getValue())
            .await().indefinitely();
        
        assertThat(readValue).isEqualTo(value);
        LOG.infof("✓ Isolated write/read successful in namespace: %s", testNamespace);
    }
    
    @Test
    void testParallelSafeWrites() {
        ConsulClient consulClient = getConsulClient();
        
        // Write multiple values in our isolated namespace
        for (int i = 0; i < 5; i++) {
            String key = testNamespace + "/config/service-" + i;
            String value = "service-" + i + "-config";
            writeValue(key, value);
        }
        
        // Verify all values are isolated
        for (int i = 0; i < 5; i++) {
            String key = testNamespace + "/config/service-" + i;
            String readValue = consulClient.getValue(key)
                .map(kv -> kv.getValue())
                .await().indefinitely();
            
            assertThat(readValue).isEqualTo("service-" + i + "-config");
        }
        
        LOG.info("✓ Multiple isolated writes successful");
    }
    
    @Test
    void testNoInterferenceWithOtherNamespaces() {
        ConsulClient consulClient = getConsulClient();
        
        // Write to our namespace
        String ourKey = testNamespace + "/config/test";
        writeValue(ourKey, "our-value");
        
        // Try to read from another namespace (should not exist)
        String otherKey = "test/OtherTest/12345/config/test";
        String otherValue = consulClient.getValue(otherKey)
            .map(kv -> kv != null ? kv.getValue() : null)
            .await().indefinitely();
        
        assertThat(otherValue).isNull();
        LOG.info("✓ No interference with other test namespaces");
    }
    
    @Test
    void testCleanupVerification() {
        ConsulClient consulClient = getConsulClient();
        
        // Write some test data
        String key1 = testNamespace + "/cleanup-test/key1";
        String key2 = testNamespace + "/cleanup-test/key2";
        
        writeValue(key1, "value1");
        writeValue(key2, "value2");
        
        // Verify they exist
        assertThat(consulClient.getValue(key1)
            .map(kv -> kv != null)
            .await().indefinitely()).isTrue();
        
        // Manually trigger cleanup for one key
        consulClient.deleteValue(key1).await().indefinitely();
        writtenKeys.remove(key1);
        
        // Verify it's gone
        String deletedValue = consulClient.getValue(key1)
            .map(kv -> kv != null ? kv.getValue() : null)
            .await().indefinitely();
        assertThat(deletedValue).isNull();
        
        LOG.info("✓ Cleanup mechanism verified");
    }
    
    protected void writeValue(String key, String value) {
        ConsulClient consulClient = getConsulClient();
        consulClient.putValue(key, value)
            .await().indefinitely();
        writtenKeys.put(key, true);
    }
}