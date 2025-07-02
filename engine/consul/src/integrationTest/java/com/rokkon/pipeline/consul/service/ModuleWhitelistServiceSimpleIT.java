package com.rokkon.pipeline.consul.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokkon.pipeline.config.service.ClusterService;
import com.rokkon.pipeline.config.service.ModuleWhitelistService;
import com.rokkon.pipeline.config.service.PipelineConfigService;
import com.rokkon.pipeline.consul.ConsulTestResource;
import com.rokkon.pipeline.consul.connection.ConsulConnectionManager;
import com.rokkon.pipeline.consul.test.IsolatedConsulKvIntegrationTestBase;
import com.rokkon.pipeline.util.ObjectMapperFactory;
import com.rokkon.pipeline.validation.CompositeValidator;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.vertx.ext.consul.ConsulClient;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Simple test to verify ModuleWhitelistService functionality.
 * 
 * IMPORTANT: This test creates REAL instances of required objects instead of using CDI injection.
 * This follows the pattern described in TESTING_STRATEGY.md where integration tests should
 * extend a base class and override methods to provide real implementations.
 */
@QuarkusIntegrationTest
@QuarkusTestResource(ConsulTestResource.class)
@TestProfile(ModuleWhitelistServiceSimpleIT.ModuleWhitelistServiceSimpleITProfile.class)
class ModuleWhitelistServiceSimpleIT extends IsolatedConsulKvIntegrationTestBase {

    private static final Logger LOG = Logger.getLogger(ModuleWhitelistServiceSimpleIT.class);

    // Real implementation of services
    private ModuleWhitelistServiceImpl whitelistService;
    private ClusterServiceImpl clusterService;

    // Custom connection manager that uses our consulClient
    private TestConsulConnectionManager connectionManager;

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
        LOG.infof("Setting up ModuleWhitelistServiceSimpleIT with namespace: %s", testNamespace);

        // Create ObjectMapper
        ObjectMapper objectMapper = ObjectMapperFactory.createConfiguredMapper();

        // Create connection manager that uses our consulClient
        connectionManager = new TestConsulConnectionManager(consulClient);

        // Create ClusterService implementation
        clusterService = new ClusterServiceImpl();
        clusterService.connectionManager = connectionManager;
        clusterService.objectMapper = objectMapper;
        clusterService.kvPrefix = testNamespace; // Use our isolated namespace

        // Create ModuleWhitelistService implementation
        whitelistService = new ModuleWhitelistServiceImpl();
        whitelistService.connectionManager = connectionManager;
        whitelistService.objectMapper = objectMapper;
        whitelistService.clusterService = clusterService;
        whitelistService.kvPrefix = testNamespace; // Use our isolated namespace

        // We don't need these for the simple tests, but they're required by the implementation
        // In a real test, we would need to create real instances of these as well
        whitelistService.pipelineValidator = null;
        whitelistService.pipelineConfigService = null;

        LOG.info("Services created with isolated namespace");
    }

    @Override
    protected void onCleanup() {
        LOG.info("Cleaning up ModuleWhitelistServiceSimpleIT");
    }

    /**
     * Get the ModuleWhitelistService implementation for tests
     */
    protected ModuleWhitelistService getWhitelistService() {
        return whitelistService;
    }

    /**
     * Get the ClusterService implementation for tests
     */
    protected ClusterService getClusterService() {
        return clusterService;
    }

    /**
     * Test methods copied from ModuleWhitelistServiceSimpleTestBase
     */
    @org.junit.jupiter.api.Test
    void testServiceInjection() {
        assertThat(getWhitelistService()).isNotNull();
        assertThat(getClusterService()).isNotNull();
    }

    @org.junit.jupiter.api.Test
    void testListModulesOnEmptyCluster() {
        // This should work even if cluster doesn't exist - it should return empty list
        var modules = getWhitelistService().listWhitelistedModules("non-existent-cluster")
            .await().indefinitely();

        assertThat(modules).isNotNull();
        assertThat(modules).isEmpty();
    }

    /**
     * Custom connection manager that uses our consulClient
     */
    private class TestConsulConnectionManager extends ConsulConnectionManager {
        private final io.vertx.mutiny.ext.consul.ConsulClient mutinyClient;
        private final ConsulClient consulClient;

        TestConsulConnectionManager(io.vertx.mutiny.ext.consul.ConsulClient mutinyClient) {
            this.mutinyClient = mutinyClient;
            this.consulClient = mutinyClient.getDelegate();
        }

        @Override
        public Optional<ConsulClient> getClient() {
            return Optional.of(consulClient);
        }

        @Override
        public Optional<io.vertx.mutiny.ext.consul.ConsulClient> getMutinyClient() {
            return Optional.of(mutinyClient);
        }
    }

    /**
     * Profile that configures test isolation
     */
    public static class ModuleWhitelistServiceSimpleITProfile implements QuarkusTestProfile {
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
