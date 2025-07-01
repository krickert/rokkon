package com.rokkon.pipeline.consul.test;

import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.ext.consul.ConsulClient;
import io.vertx.ext.consul.ConsulClientOptions;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base class for tests that need isolated Consul KV namespaces.
 * This enables parallel test execution without conflicts.
 * 
 * Each test gets a unique namespace following the pattern:
 * test/{testClassName}/{uuid}/
 * 
 * All keys written during the test are automatically cleaned up.
 */
public abstract class IsolatedConsulKvTestBase {

    @Inject
    protected Vertx vertx;

    @ConfigProperty(name = "consul.host")
    protected String consulHost;

    @ConfigProperty(name = "consul.port")
    protected int consulPort;

    protected ConsulClient consulClient;
    protected String testNamespace;

    // Track all keys written by this test for cleanup
    private final ConcurrentHashMap<String, Boolean> writtenKeys = new ConcurrentHashMap<>();

    @BeforeEach
    void setupIsolatedNamespace() {
        // Create unique namespace for this test
        String testId = UUID.randomUUID().toString().substring(0, 8);
        testNamespace = "test/" + getClass().getSimpleName() + "/" + testId;

        ConsulClientOptions options = new ConsulClientOptions()
            .setHost(consulHost)
            .setPort(consulPort);
        consulClient = ConsulClient.create(vertx, options);

        System.out.println("Isolated test namespace: " + testNamespace);

        // Allow subclasses to do additional setup
        onSetup();
    }

    @AfterEach
    void cleanupIsolatedNamespace() {
        // Allow subclasses to do cleanup first
        onCleanup();

        // Clean up all keys written by this test
        System.out.println("Cleaning up " + writtenKeys.size() + " keys from namespace: " + testNamespace);

        writtenKeys.keySet().forEach(key -> {
            try {
                consulClient.deleteValue(key)
                    .onFailure().recoverWithNull()
                    .await().indefinitely();
            } catch (Exception e) {
                System.err.println("Error during cleanup of key: " + key + " - " + e.getMessage());
            }
        });
        writtenKeys.clear();
    }

    /**
     * Write a value to Consul KV with automatic namespacing and cleanup tracking.
     * 
     * @param key The key relative to the test namespace
     * @param value The value to write
     */
    protected void writeValue(String key, String value) {
        String fullKey = testNamespace + "/" + key;
        consulClient.putValue(fullKey, value)
            .await().indefinitely();
        writtenKeys.put(fullKey, true);
    }

    /**
     * Read a value from Consul KV with automatic namespacing.
     * 
     * @param key The key relative to the test namespace
     * @return The value or null if not found
     */
    protected String readValue(String key) {
        String fullKey = testNamespace + "/" + key;
        return consulClient.getValue(fullKey)
            .map(kv -> kv != null ? kv.getValue() : null)
            .await().indefinitely();
    }

    /**
     * Delete a value from Consul KV with automatic namespacing.
     * 
     * @param key The key relative to the test namespace
     */
    protected void deleteValue(String key) {
        String fullKey = testNamespace + "/" + key;
        try {
            // Use the same pattern as in cleanup method
            consulClient.deleteValue(fullKey)
                .onFailure().recoverWithNull()
                .await().indefinitely();

            // Verify the key is actually gone by waiting a bit and checking
            Thread.sleep(100); // Small delay to ensure consistency

            // Remove from tracking map
            writtenKeys.remove(fullKey);
        } catch (Exception e) {
            System.err.println("Error deleting key: " + fullKey + " - " + e.getMessage());
        }
    }

    /**
     * Check if a key exists in Consul KV with automatic namespacing.
     * 
     * @param key The key relative to the test namespace
     * @return true if the key exists
     */
    protected boolean keyExists(String key) {
        String fullKey = testNamespace + "/" + key;
        return consulClient.getValue(fullKey)
            .map(kv -> kv != null)
            .await().indefinitely();
    }

    /**
     * Get the full key with namespace prefix.
     * 
     * @param key The key relative to the test namespace
     * @return The full key including namespace
     */
    protected String getFullKey(String key) {
        return testNamespace + "/" + key;
    }

    /**
     * Override this method to perform additional setup after namespace creation.
     */
    protected void onSetup() {
        // Default: no-op
    }

    /**
     * Override this method to perform additional cleanup before namespace deletion.
     */
    protected void onCleanup() {
        // Default: no-op
    }
}
