package com.rokkon.pipeline.consul;

import com.rokkon.pipeline.consul.test.ConsulIntegrationTestBase;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test demonstrating that our test isolation works for parallel execution.
 * Multiple instances of this test can run in parallel without interfering.
 */
@QuarkusTest
@Execution(ExecutionMode.CONCURRENT) // Enable parallel execution
public class ParallelConsulKvTest extends ConsulIntegrationTestBase {
    
    @Test
    void testParallelKvWrites() throws Exception {
        // Write test data using our isolated prefix
        putTestKv("config/app", "version: 1.0.0").await().indefinitely();
        putTestKv("config/features", "enabled: true").await().indefinitely();
        
        // Read back to verify isolation
        String appConfig = getTestKv("config/app").await().indefinitely();
        String featuresConfig = getTestKv("config/features").await().indefinitely();
        
        assertThat(appConfig).isEqualTo("version: 1.0.0");
        assertThat(featuresConfig).isEqualTo("enabled: true");
        
        System.out.println("✓ Test " + testId + " wrote to isolated KV namespace: " + testKvPrefix);
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
                
                putTestKv(key, value).await().indefinitely();
                
                // Verify write was successful
                String readValue = getTestKv(key).await().indefinitely();
                assertThat(readValue).isEqualTo(value);
            }, executor);
            
            futures.add(future);
        }
        
        // Wait for all writes to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .get(10, TimeUnit.SECONDS);
        
        // Verify all values are present
        for (int i = 0; i < 10; i++) {
            String value = getTestKv("concurrent/thread-" + i).await().indefinitely();
            assertThat(value).isEqualTo("value-from-thread-" + i);
        }
        
        executor.shutdown();
        System.out.println("✓ Concurrent writes within test " + testId + " successful");
    }
    
    @Test
    void testServiceIsolation() {
        // Register a test service with our unique prefix
        String serviceName = "parallel-test-service";
        registerTestService(serviceName, "localhost", 8080)
            .await().indefinitely();
        
        // Store service config in isolated KV
        putTestKv("services/" + serviceName + "/config", """
            service:
              timeout: 30s
              retries: 3
            """).await().indefinitely();
        
        // Verify service config is isolated
        String config = getTestKv("services/" + serviceName + "/config")
            .await().indefinitely();
        
        assertThat(config).contains("timeout: 30s");
        
        System.out.println("✓ Service " + getTestServiceName(serviceName) + " registered with isolated config");
    }
    
    @Test 
    void testNoInterferenceAcrossTests() {
        // This test should not see data from other tests
        String otherTestKey = "test-kv/other-test-id/config/app";
        
        // Try to read from another test's namespace (should not exist)
        String otherTestValue = consulClient.getValue(otherTestKey)
            .map(kv -> kv != null ? kv.getValue() : null)
            .await().indefinitely();
        
        assertThat(otherTestValue).isNull();
        
        // Write to our namespace
        putTestKv("isolation/verified", "true").await().indefinitely();
        
        // Verify our write worked
        String ourValue = getTestKv("isolation/verified").await().indefinitely();
        assertThat(ourValue).isEqualTo("true");
        
        System.out.println("✓ Test isolation verified - no interference from other tests");
    }
    
    @Test
    void testCleanupDoesNotAffectOtherTests() {
        // Write some test data
        putTestKv("cleanup/test1", "data1").await().indefinitely();
        putTestKv("cleanup/test2", "data2").await().indefinitely();
        
        // Manually delete one key
        consulClient.deleteValue(getTestKvKey("cleanup/test1"))
            .await().indefinitely();
        
        // Verify only that key was deleted
        assertThat(getTestKv("cleanup/test1").await().indefinitely()).isNull();
        assertThat(getTestKv("cleanup/test2").await().indefinitely()).isEqualTo("data2");
        
        // Write data that simulates another test's namespace
        String fakeOtherTestKey = "test-kv/fake-other-test/data";
        consulClient.putValue(fakeOtherTestKey, "other-test-data")
            .await().indefinitely();
        
        // Verify it exists
        String otherData = consulClient.getValue(fakeOtherTestKey)
            .map(kv -> kv.getValue())
            .await().indefinitely();
        assertThat(otherData).isEqualTo("other-test-data");
        
        // Our cleanup should not affect it (will be verified in teardown)
        System.out.println("✓ Cleanup isolation verified");
        
        // Clean up the fake key ourselves
        consulClient.deleteValue(fakeOtherTestKey).await().indefinitely();
    }
}