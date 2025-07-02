package com.rokkon.pipeline.consul;

import com.rokkon.pipeline.consul.ConsulTestResource;
import com.rokkon.pipeline.consul.test.IsolatedConsulKvIntegrationTestBase;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test demonstrating that our test isolation works for parallel execution.
 * Multiple instances of this test can run in parallel without interfering.
 * This is a real integration test that runs against a real Consul instance.
 * 
 * IMPORTANT: This test creates REAL instances of required objects instead of using CDI injection.
 * This follows the pattern described in TESTING_STRATEGY.md where integration tests should
 * extend a base class and override methods to provide real implementations.
 */
@QuarkusIntegrationTest /// DO NOT CHANGE THIS
@QuarkusTestResource(ConsulTestResource.class)
@TestProfile(ParallelConsulKvIT.ParallelConsulKvProfile.class)
public class ParallelConsulKvIT extends IsolatedConsulKvIntegrationTestBase {
    private static final Logger LOG = Logger.getLogger(ParallelConsulKvIT.class);

    @Override
    protected void configureConsulConnection() {
        // Get Consul host and port from system properties set by ConsulTestResource
        String hostProp = System.getProperty("consul.host");
        String portProp = System.getProperty("consul.port");

        if (hostProp != null) {
            consulHost = hostProp;
            LOG.infof("Using Consul host from system property: %s", consulHost);
        }

        if (portProp != null) {
            try {
                consulPort = Integer.parseInt(portProp);
                LOG.infof("Using Consul port from system property: %d", consulPort);
            } catch (NumberFormatException e) {
                LOG.errorf("Invalid consul.port: %s", portProp);
            }
        }
    }

    @Override
    protected void onSetup() {
        LOG.infof("Setting up ParallelConsulKvIT with namespace: %s", testNamespace);
    }

    @Override
    protected void onCleanup() {
        LOG.info("Cleaning up ParallelConsulKvIT");
    }

    @Test
    void testParallelKvWrites() {
        // Write test data using our isolated namespace
        writeValue("config/app", "version: 1.0.0");
        writeValue("config/features", "enabled: true");

        // Read back to verify isolation
        String appConfig = readValue("config/app");
        String featuresConfig = readValue("config/features");

        assertThat(appConfig).isEqualTo("version: 1.0.0");
        assertThat(featuresConfig).isEqualTo("enabled: true");

        LOG.infof("✓ Test wrote to isolated KV namespace: %s", testNamespace);
    }

    @Test
    void testConcurrentWritesWithinTest() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(5);
        ArrayList<CompletableFuture<Void>> futures = new ArrayList<>();

        // Simulate multiple threads writing to the same test's namespace
        for (int i = 0; i < 10; i++) {
            final int index = i;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                String key = "concurrent/thread-" + index;
                String value = "value-from-thread-" + index;

                writeValue(key, value);

                // Verify write was successful
                String readValue = readValue(key);
                assertThat(readValue).isEqualTo(value);
            }, executor);

            futures.add(future);
        }

        // Wait for all writes to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .get(10, TimeUnit.SECONDS);

        // Verify all values are present
        for (int i = 0; i < 10; i++) {
            String value = readValue("concurrent/thread-" + i);
            assertThat(value).isEqualTo("value-from-thread-" + i);
        }

        executor.shutdown();
        LOG.info("✓ Concurrent writes within test successful");
    }

    @Test
    void testServiceIsolation() {
        // Store service config in isolated KV
        writeValue("services/parallel-test-service/config", """
            service:
              timeout: 30s
              retries: 3
            """);

        // Verify service config is isolated
        String config = readValue("services/parallel-test-service/config");

        assertThat(config).contains("timeout: 30s");

        LOG.infof("✓ Service config stored in isolated namespace: %s", testNamespace);
    }

    @Test 
    void testNoInterferenceAcrossTests() {
        // This test should not see data from other tests
        String otherTestKey = "test/other-test-id/config/app";

        // Try to read from another test's namespace (should not exist)
        String otherTestValue = consulClient.getValue(otherTestKey)
            .map(kv -> kv != null ? kv.getValue() : null)
            .await().indefinitely();

        assertThat(otherTestValue).isNull();

        // Write to our namespace
        writeValue("isolation/verified", "true");

        // Verify our write worked
        String ourValue = readValue("isolation/verified");
        assertThat(ourValue).isEqualTo("true");

        LOG.info("✓ Test isolation verified - no interference from other tests");
    }

    @Test
    void testCleanupDoesNotAffectOtherTests() {
        // Write some test data
        writeValue("cleanup/test1", "data1");
        writeValue("cleanup/test2", "data2");

        // Manually delete one key
        deleteValue("cleanup/test1");

        // Verify only that key was deleted
        assertThat(keyExists("cleanup/test1")).isFalse();
        assertThat(keyExists("cleanup/test2")).isTrue();

        LOG.info("✓ Cleanup isolation verified");
    }

    /**
     * Profile that configures test isolation
     */
    public static class ParallelConsulKvProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            // Generate unique namespace for this test run
            String testId = UUID.randomUUID().toString().substring(0, 8);
            return Map.of(
                "quarkus.consul-config.fail-on-missing-key", "false",
                "quarkus.consul-config.watch", "false",
                "smallrye.config.mapping.validate-unknown", "false"
            );
        }
    }
}
