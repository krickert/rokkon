package com.rokkon.pipeline.consul;

import com.rokkon.pipeline.consul.test.ConsulTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.ext.consul.ConsulClient;
import io.vertx.ext.consul.ConsulClientOptions;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;

import java.util.Map;
import java.util.UUID;

/**
 * Integration test demonstrating isolated Consul KV writes for parallel test execution.
 * Each test gets its own unique namespace in Consul KV to avoid conflicts.
 */
@QuarkusIntegrationTest
@QuarkusTestResource(ConsulTestResource.class)
@io.quarkus.test.junit.TestProfile(IsolatedConsulKvIT.IsolatedKvProfile.class)
public class IsolatedConsulKvIT extends IsolatedConsulKvTestBase {
    
    @Inject
    Vertx vertx;
    
    @ConfigProperty(name = "consul.host")
    String consulHost;
    
    @ConfigProperty(name = "consul.port")
    int consulPort;
    
    private ConsulClient consulClient;
    
    @BeforeEach
    void setupConsulClient() {
        super.setup();
        ConsulClientOptions options = new ConsulClientOptions()
            .setHost(consulHost)
            .setPort(consulPort);
        consulClient = ConsulClient.create(vertx, options);
    }
    
    @Override
    protected ConsulClient getConsulClient() {
        return consulClient;
    }
    
    @Override
    protected boolean isRealConsul() {
        return true; // This is an integration test with real Consul
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