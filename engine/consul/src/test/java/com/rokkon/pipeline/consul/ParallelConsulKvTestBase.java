package com.rokkon.pipeline.consul;

import io.vertx.mutiny.ext.consul.ConsulClient;
import io.vertx.ext.consul.KeyValue; // Using non-mutiny KeyValue as it's returned by the mutiny ConsulClient
import org.jboss.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base test class demonstrating that our test isolation works for parallel execution.
 * Multiple instances of this test can run in parallel without interfering.
 */
@Execution(ExecutionMode.CONCURRENT) // Enable parallel execution
public abstract class ParallelConsulKvTestBase {
    private static final Logger LOG = Logger.getLogger(ParallelConsulKvTestBase.class);
    
    protected String testId;
    protected String testKvPrefix;
    protected final ConcurrentHashMap<String, Boolean> writtenKeys = new ConcurrentHashMap<>();
    
    protected abstract ConsulClient getConsulClient();
    protected abstract boolean isRealConsul();
    
    @BeforeEach
    void setup() {
        testId = UUID.randomUUID().toString().substring(0, 8);
        testKvPrefix = "test-kv/" + testId;
        LOG.infof("Test setup with ID: %s, prefix: %s", testId, testKvPrefix);
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
    void testParallelKvWrites() throws Exception {
        // Write test data using our isolated prefix
        putTestKv("config/app", "version: 1.0.0");
        putTestKv("config/features", "enabled: true");
        
        // Read back to verify isolation
        String appConfig = getTestKv("config/app");
        String featuresConfig = getTestKv("config/features");
        
        assertThat(appConfig).isEqualTo("version: 1.0.0");
        assertThat(featuresConfig).isEqualTo("enabled: true");
        
        LOG.infof("✓ Test %s wrote to isolated KV namespace: %s", testId, testKvPrefix);
    }
    
    @Test
    void testConcurrentWritesWithinTest() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(5);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        // Simulate multiple threads writing to the same test's namespace
        for (int i = 0; i < 10; i++) {
            final int index = i;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                String key = "concurrent/thread-" + index;
                String value = "value-from-thread-" + index;
                
                putTestKv(key, value);
                
                // Verify write was successful
                String readValue = getTestKv(key);
                assertThat(readValue).isEqualTo(value);
            }, executor);
            
            futures.add(future);
        }
        
        // Wait for all writes to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .get(10, TimeUnit.SECONDS);
        
        // Verify all values are present
        for (int i = 0; i < 10; i++) {
            String value = getTestKv("concurrent/thread-" + i);
            assertThat(value).isEqualTo("value-from-thread-" + i);
        }
        
        executor.shutdown();
        LOG.infof("✓ Concurrent writes within test %s successful", testId);
    }
    
    @Test
    void testServiceIsolation() {
        // Store service config in isolated KV
        putTestKv("services/parallel-test-service/config", """
            service:
              timeout: 30s
              retries: 3
            """);
        
        // Verify service config is isolated
        String config = getTestKv("services/parallel-test-service/config");
        
        assertThat(config).contains("timeout: 30s");
        
        LOG.infof("✓ Service config stored in isolated namespace: %s", testKvPrefix);
    }
    
    @Test 
    void testNoInterferenceAcrossTests() {
        ConsulClient consulClient = getConsulClient();
        
        // This test should not see data from other tests
        String otherTestKey = "test-kv/other-test-id/config/app";
        
        // Try to read from another test's namespace (should not exist)
        String otherTestValue = consulClient.getValue(otherTestKey)
            .map(kv -> kv != null ? kv.getValue() : null)
            .await().indefinitely();
        
        assertThat(otherTestValue).isNull();
        
        // Write to our namespace
        putTestKv("isolation/verified", "true");
        
        // Verify our write worked
        String ourValue = getTestKv("isolation/verified");
        assertThat(ourValue).isEqualTo("true");
        
        LOG.info("✓ Test isolation verified - no interference from other tests");
    }
    
    @Test
    void testCleanupDoesNotAffectOtherTests() {
        ConsulClient consulClient = getConsulClient();
        
        // Write some test data
        putTestKv("cleanup/test1", "data1");
        putTestKv("cleanup/test2", "data2");
        
        // Manually delete one key
        String deleteKey = getTestKvKey("cleanup/test1");
        consulClient.deleteValue(deleteKey)
            .await().indefinitely();
        writtenKeys.remove(deleteKey);
        
        // Verify only that key was deleted
        assertThat(getTestKv("cleanup/test1")).isNull();
        assertThat(getTestKv("cleanup/test2")).isEqualTo("data2");
        
        LOG.info("✓ Cleanup isolation verified");
    }
    
    protected void putTestKv(String key, String value) {
        ConsulClient consulClient = getConsulClient();
        String fullKey = getTestKvKey(key);
        consulClient.putValue(fullKey, value)
            .await().indefinitely();
        writtenKeys.put(fullKey, true);
    }
    
    protected String getTestKv(String key) {
        ConsulClient consulClient = getConsulClient();
        String fullKey = getTestKvKey(key);
        return consulClient.getValue(fullKey)
            .map(kv -> kv != null ? kv.getValue() : null)
            .await().indefinitely();
    }
    
    protected String getTestKvKey(String key) {
        return testKvPrefix + "/" + key;
    }
}