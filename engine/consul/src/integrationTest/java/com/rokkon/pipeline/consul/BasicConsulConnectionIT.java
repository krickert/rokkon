package com.rokkon.pipeline.consul;

import com.rokkon.pipeline.consul.test.IsolatedConsulKvIntegrationTestBase;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test to verify Consul is running and we can connect to it.
 * This is our foundation - if this doesn't work, nothing else will.
 */
@QuarkusIntegrationTest/// DO NOT CHANGE THIS !!!! IT IS CHEATING!!!
@QuarkusTestResource(ConsulTestResource.class)
@TestProfile(BasicConsulConnectionIT.BasicConsulConnectionProfile.class)
class BasicConsulConnectionIT extends IsolatedConsulKvIntegrationTestBase {

    private static final Logger LOG = Logger.getLogger(BasicConsulConnectionIT.class);

    @Override
    protected void configureConsulConnection() {
        // Get Consul host and port from system properties set by ConsulTestResource
        String hostProp = System.getProperty("consul.host");
        String portProp = System.getProperty("consul.port");

        if (hostProp != null) {
            consulHost = hostProp;
        }

        if (portProp != null) {
            try {
                consulPort = Integer.parseInt(portProp);
            } catch (NumberFormatException e) {
                LOG.error("Invalid consul.port: " + portProp);
            }
        }

        LOG.infof("Configuring Consul connection to: %s:%d", consulHost, consulPort);
    }

    @Test
    void testConsulConnection() {
        // Write a test value to Consul
        String testKey = "test/connection";
        String testValue = "connection-successful";

        writeValue(testKey, testValue);

        // Read it back
        String readValue = readValue(testKey);

        // Verify the connection works
        assertThat(readValue).isEqualTo(testValue);
        LOG.info("✓ Consul connection test successful");
    }

    @Override
    protected void onSetup() {
        LOG.info("Setting up BasicConsulConnectionIT with namespace: " + testNamespace);
    }

    @Override
    protected void onCleanup() {
        LOG.info("Cleaning up BasicConsulConnectionIT");
    }

    /**
     * Profile that configures test isolation
     */
    public static class BasicConsulConnectionProfile implements QuarkusTestProfile {
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
