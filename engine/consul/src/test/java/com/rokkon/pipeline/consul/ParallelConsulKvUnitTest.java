package com.rokkon.pipeline.consul;

import com.rokkon.pipeline.consul.test.MockConsulClient;
import io.vertx.mutiny.ext.consul.ConsulClient;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for parallel Consul KV operations using MockConsulClient.
 * Tests parallel execution patterns without requiring real Consul.
 * This test doesn't use @QuarkusTest since it uses mocks exclusively.
 */
public class ParallelConsulKvUnitTest {
    private static final Logger LOG = Logger.getLogger(ParallelConsulKvUnitTest.class);
    
    private MockConsulClient mockConsulClient;
    private String testId;
    private String testKvPrefix;
    private final ConcurrentHashMap<String, Boolean> writtenKeys = new ConcurrentHashMap<>();
    
    @BeforeEach
    void setup() {
        mockConsulClient = new MockConsulClient();
        testId = UUID.randomUUID().toString().substring(0, 8);
        testKvPrefix = "test-kv/" + testId;
        LOG.infof("Test setup with ID: %s, prefix: %s", testId, testKvPrefix);
    }
    
    @Test
    void testParallelKvWrites() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(5);
        
        try {
            // Submit parallel write operations
            CompletableFuture<?>[] futures = new CompletableFuture[10];
            for (int i = 0; i < 10; i++) {
                final int index = i;
                futures[i] = CompletableFuture.runAsync(() -> {
                    String key = testKvPrefix + "/concurrent/thread-" + index;
                    String value = "value-from-thread-" + index;
                    putTestKv(key, value);
                    
                    // Verify the write
                    String result = getTestKv(key);
                    assertThat(result).isEqualTo(value);
                }, executor);
            }
            
            // Wait for all operations to complete
            CompletableFuture.allOf(futures).get(10, TimeUnit.SECONDS);
            
            // Verify all keys were written
            for (int i = 0; i < 10; i++) {
                String key = testKvPrefix + "/concurrent/thread-" + i;
                String result = getTestKv(key);
                assertThat(result).isEqualTo("value-from-thread-" + i);
            }
            
            LOG.infof("✓ Concurrent writes within test %s successful", testId);
        } finally {
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
    
    @Test
    void testNoInterferenceAcrossTests() {
        // Write to a key specific to this test
        String myKey = testKvPrefix + "/isolation/verified";
        putTestKv(myKey, "true");
        
        // Try to read from another test's namespace (should not exist)
        String otherTestKey = "test-kv/other-test-id/config/app";
        String otherResult = getTestKv(otherTestKey);
        assertThat(otherResult).isNull();
        
        // Verify our own key exists
        String myResult = getTestKv(myKey);
        assertThat(myResult).isEqualTo("true");
        
        LOG.infof("✓ Test isolation verified - no interference from other tests");
    }
    
    @Test
    void testServiceIsolation() {
        // Each test gets its own namespace for service configuration
        String serviceKey = testKvPrefix + "/services/parallel-test-service/config";
        String serviceConfig = "service:\n  timeout: 30s\n  retries: 3\n";
        
        putTestKv(serviceKey, serviceConfig);
        
        String result = getTestKv(serviceKey);
        assertThat(result).isEqualTo(serviceConfig);
        
        LOG.infof("✓ Service config stored in isolated namespace: %s", testKvPrefix);
    }
    
    @Test
    void testCleanupDoesNotAffectOtherTests() {
        // Write some test data
        String key1 = testKvPrefix + "/cleanup/test1";
        String key2 = testKvPrefix + "/cleanup/test2";
        
        putTestKv(key1, "data1");
        putTestKv(key2, "data2");
        
        // Delete one key
        mockConsulClient.deleteValue(key1).subscribe().asCompletionStage().join();
        writtenKeys.remove(key1);
        
        // Verify only the deleted key is gone
        String result1 = getTestKv(key1);
        String result2 = getTestKv(key2);
        
        assertThat(result1).isNull();
        assertThat(result2).isEqualTo("data2");
        
        LOG.infof("✓ Cleanup isolation verified");
    }
    
    @Test
    void testConcurrentWritesWithinTest() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(5);
        
        try {
            // Write different values to the same namespace concurrently
            CompletableFuture<?>[] futures = new CompletableFuture[10];
            for (int i = 0; i < 10; i++) {
                final int index = i;
                futures[i] = CompletableFuture.runAsync(() -> {
                    String key = testKvPrefix + "/concurrent/thread-" + index;
                    String value = "value-from-thread-" + index;
                    putTestKv(key, value);
                }, executor);
            }
            
            CompletableFuture.allOf(futures).get(10, TimeUnit.SECONDS);
            LOG.infof("✓ Concurrent writes within test %s successful", testId);
        } finally {
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
    
    protected void putTestKv(String key, String value) {
        mockConsulClient.putValue(key, value).subscribe().asCompletionStage().join();
        writtenKeys.put(key, true);
    }
    
    protected String getTestKv(String key) {
        var kv = mockConsulClient.getValue(key).subscribe().asCompletionStage().join();
        return kv != null ? kv.getValue() : null;
    }
}