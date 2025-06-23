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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Test both success and failure cases for consul-config.
 * This test uses different profiles to test different behaviors.
 */
@QuarkusTest
@QuarkusTestResource(ConsulTestResource.class)
@io.quarkus.test.junit.TestProfile(ConsulConfigSuccessFailTest.SuccessProfile.class)
public class ConsulConfigSuccessFailTest {
    
    @Inject
    Vertx vertx;
    
    @ConfigProperty(name = "consul.host")
    String consulHost;
    
    @ConfigProperty(name = "consul.port")
    int consulPort;
    
    private ConsulClient consulClient;
    
    @BeforeEach
    void setup() {
        ConsulClientOptions options = new ConsulClientOptions()
            .setHost(consulHost)
            .setPort(consulPort);
        consulClient = ConsulClient.create(vertx, options);
    }
    
    @Test
    void testSuccessWhenConfigExists() {
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
        System.out.println("✓ Configuration successfully stored in Consul KV at config/test-success");
    }
    
    @Test
    void testConfigNotFoundBehavior() {
        // Try to read a key that doesn't exist
        String nonExistentValue = consulClient.getValue("config/non-existent")
            .map(kv -> kv != null ? kv.getValue() : null)
            .await().indefinitely();
        
        assertThat(nonExistentValue).isNull();
        System.out.println("✓ Non-existent key returns null as expected");
    }
    
    @Test
    void testUpdateExistingConfig() {
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
        System.out.println("✓ Configuration update successful");
    }
    
    @Test
    void testDeleteConfig() {
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
        
        System.out.println("✓ Configuration deletion successful");
    }
    
    /**
     * Test profile that configures consul-config to look for test-specific keys.
     */
    public static class SuccessProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                "quarkus.consul-config.properties-value-keys[0]", "config/test-success",
                "quarkus.consul-config.fail-on-missing-key", "false"
            );
        }
    }
}