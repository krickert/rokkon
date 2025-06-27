package com.rokkon.pipeline.consul;

import com.rokkon.pipeline.consul.test.ConsulTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.ext.consul.ConsulClient;
import io.vertx.ext.consul.ConsulClientOptions;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;

import java.util.Map;

/**
 * Integration test for both success and failure cases for consul-config.
 * This test uses different profiles to test different behaviors.
 */
@QuarkusIntegrationTest
@QuarkusTestResource(ConsulTestResource.class)
@TestProfile(ConsulConfigSuccessFailIT.SuccessProfile.class)
public class ConsulConfigSuccessFailIT extends ConsulConfigSuccessFailTestBase {
    
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
    
    @Override
    protected ConsulClient getConsulClient() {
        return consulClient;
    }
    
    @Override
    protected boolean isRealConsul() {
        return true; // Integration test with real Consul
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