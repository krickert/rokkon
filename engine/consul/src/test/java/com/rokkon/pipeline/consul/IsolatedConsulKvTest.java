package com.rokkon.pipeline.consul;

import com.rokkon.pipeline.consul.test.ConsulTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.ext.consul.ConsulClient;
import io.vertx.ext.consul.ConsulClientOptions;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test demonstrating isolated Consul KV writes for parallel test execution.
 * Each test gets its own unique namespace in Consul KV to avoid conflicts.
 */
@QuarkusTest
@QuarkusTestResource(ConsulTestResource.class)
@io.quarkus.test.junit.TestProfile(IsolatedConsulKvTest.IsolatedKvProfile.class)
public class IsolatedConsulKvTest {
    
    @Inject
    Vertx vertx;
    
    @ConfigProperty(name = "consul.host")
    String consulHost;
    
    @ConfigProperty(name = "consul.port")
    int consulPort;
    
    private ConsulClient consulClient;
    private String testNamespace;
    
    // Track all keys written by this test for cleanup
    private final ConcurrentHashMap<String, Boolean> writtenKeys = new ConcurrentHashMap<>();
    
    @BeforeEach
    void setup() {
        // Create unique namespace for this test
        testNamespace = "test/" + getClass().getSimpleName() + "/" + UUID.randomUUID().toString();
        
        ConsulClientOptions options = new ConsulClientOptions()
            .setHost(consulHost)
            .setPort(consulPort);
        consulClient = ConsulClient.create(vertx, options);
        
        System.out.println("Test namespace: " + testNamespace);
    }
    
    @AfterEach
    void cleanup() {
        // Clean up all keys written by this test
        writtenKeys.keySet().forEach(key -> {
            consulClient.deleteValue(key)
                .onFailure().recoverWithNull()
                .await().indefinitely();
        });
        writtenKeys.clear();
    }
    
    @Test
    void testIsolatedWrites() {
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
        System.out.println("✓ Isolated write/read successful in namespace: " + testNamespace);
    }
    
    @Test
    void testParallelSafeWrites() {
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
        
        System.out.println("✓ Multiple isolated writes successful");
    }
    
    @Test
    void testNoInterferenceWithOtherNamespaces() {
        // Write to our namespace
        String ourKey = testNamespace + "/config/test";
        writeValue(ourKey, "our-value");
        
        // Try to read from another namespace (should not exist)
        String otherKey = "test/OtherTest/12345/config/test";
        String otherValue = consulClient.getValue(otherKey)
            .map(kv -> kv != null ? kv.getValue() : null)
            .await().indefinitely();
        
        assertThat(otherValue).isNull();
        System.out.println("✓ No interference with other test namespaces");
    }
    
    @Test
    void testCleanupVerification() {
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
        
        System.out.println("✓ Cleanup mechanism verified");
    }
    
    private void writeValue(String key, String value) {
        consulClient.putValue(key, value)
            .await().indefinitely();
        writtenKeys.put(key, true);
    }
    
    /**
     * Profile for isolated consul-config testing.
     * Each test could have its own config namespace.
     */
    public static class IsolatedKvProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            // Generate unique namespace for this test run
            String testId = UUID.randomUUID().toString().substring(0, 8);
            return Map.of(
                // Each test run gets unique config paths
                "quarkus.consul-config.properties-value-keys[0]", "config/test-" + testId + "/application",
                "quarkus.consul-config.properties-value-keys[1]", "config/test-" + testId + "/test",
                "quarkus.consul-config.fail-on-missing-key", "false",
                // Disable watch for tests to avoid resource leaks
                "quarkus.consul-config.watch", "false"
            );
        }
    }
}