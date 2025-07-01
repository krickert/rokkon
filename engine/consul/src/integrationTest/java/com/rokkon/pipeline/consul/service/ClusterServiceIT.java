package com.rokkon.pipeline.consul.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokkon.pipeline.config.service.ClusterService;
import com.rokkon.pipeline.consul.ConsulTestResource;
import com.rokkon.pipeline.consul.connection.ConsulConnectionManager;
import com.rokkon.pipeline.consul.test.IsolatedConsulKvIntegrationTestBase;
import com.rokkon.pipeline.util.ObjectMapperFactory;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.vertx.core.Vertx;
import io.vertx.ext.consul.ConsulClient;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for ClusterService that uses a real Consul instance.
 * 
 * IMPORTANT: This test creates REAL instances of required objects instead of using CDI injection.
 * This follows the pattern described in TESTING_STRATEGY.md where integration tests should
 * extend a base class and override methods to provide real implementations.
 */
@QuarkusIntegrationTest
@QuarkusTestResource(ConsulTestResource.class)
@TestProfile(ClusterServiceIT.ClusterServiceITProfile.class)
class ClusterServiceIT extends IsolatedConsulKvIntegrationTestBase {

    private static final Logger LOG = Logger.getLogger(ClusterServiceIT.class);

    // Real implementation of ClusterService
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
        LOG.infof("Setting up ClusterServiceIT with namespace: %s", testNamespace);

        // Create ObjectMapper
        ObjectMapper objectMapper = ObjectMapperFactory.createConfiguredMapper();

        // Create connection manager that uses our consulClient
        connectionManager = new TestConsulConnectionManager(consulClient);

        // Create ClusterService implementation
        clusterService = new ClusterServiceImpl();
        clusterService.connectionManager = connectionManager;
        clusterService.objectMapper = objectMapper;
        clusterService.kvPrefix = testNamespace; // Use our isolated namespace

        LOG.info("ClusterService created with isolated namespace");
    }

    @Override
    protected void onCleanup() {
        LOG.info("Cleaning up ClusterServiceIT");
    }

    /**
     * Get the ClusterService implementation for tests
     */
    protected ClusterService getClusterService() {
        return clusterService;
    }

    /**
     * Test methods copied from ClusterServiceTestBase
     * We need to implement these directly since we can't extend both
     * IsolatedConsulKvIntegrationTestBase and ClusterServiceTestBase
     */
    @org.junit.jupiter.api.Test
    void testCreateCluster() {
        String clusterName = "base-test-cluster";

        LOG.infof("Testing cluster creation: %s", clusterName);

        com.rokkon.pipeline.validation.ValidationResult result = getClusterService().createCluster(clusterName)
            .subscribe().withSubscriber(io.smallrye.mutiny.helpers.test.UniAssertSubscriber.create())
            .awaitItem(java.time.Duration.ofSeconds(5))
            .getItem();

        LOG.infof("Create cluster result: valid=%s, errors=%s", 
                  result.valid(), result.errors());

        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @org.junit.jupiter.api.Test
    void testGetCluster() {
        String clusterName = "base-get-test-cluster";

        // First create a cluster
        com.rokkon.pipeline.validation.ValidationResult createResult = getClusterService().createCluster(clusterName)
            .subscribe().withSubscriber(io.smallrye.mutiny.helpers.test.UniAssertSubscriber.create())
            .awaitItem(java.time.Duration.ofSeconds(5))
            .getItem();

        assertThat(createResult.valid()).isTrue();

        // Now get the cluster
        java.util.Optional<com.rokkon.pipeline.config.model.ClusterMetadata> cluster = getClusterService().getCluster(clusterName)
            .subscribe().withSubscriber(io.smallrye.mutiny.helpers.test.UniAssertSubscriber.create())
            .awaitItem(java.time.Duration.ofSeconds(5))
            .getItem();

        assertThat(cluster).isPresent();
        assertThat(cluster.get().name()).isEqualTo(clusterName);
        assertThat(cluster.get().metadata()).containsEntry("status", "active");
    }

    @org.junit.jupiter.api.Test
    void testClusterExists() {
        String clusterName = "base-exists-test-cluster";

        // Check non-existent cluster
        Boolean exists = getClusterService().clusterExists(clusterName)
            .subscribe().withSubscriber(io.smallrye.mutiny.helpers.test.UniAssertSubscriber.create())
            .awaitItem(java.time.Duration.ofSeconds(5))
            .getItem();

        assertThat(exists).isFalse();

        // Create cluster
        com.rokkon.pipeline.validation.ValidationResult createResult = getClusterService().createCluster(clusterName)
            .subscribe().withSubscriber(io.smallrye.mutiny.helpers.test.UniAssertSubscriber.create())
            .awaitItem(java.time.Duration.ofSeconds(5))
            .getItem();

        assertThat(createResult.valid()).isTrue();

        // Check existing cluster
        exists = getClusterService().clusterExists(clusterName)
            .subscribe().withSubscriber(io.smallrye.mutiny.helpers.test.UniAssertSubscriber.create())
            .awaitItem(java.time.Duration.ofSeconds(5))
            .getItem();

        assertThat(exists).isTrue();
    }

    @org.junit.jupiter.api.Test
    void testCreateDuplicateCluster() {
        String clusterName = "base-duplicate-test-cluster";

        // Create first cluster
        com.rokkon.pipeline.validation.ValidationResult firstResult = getClusterService().createCluster(clusterName)
            .subscribe().withSubscriber(io.smallrye.mutiny.helpers.test.UniAssertSubscriber.create())
            .awaitItem(java.time.Duration.ofSeconds(5))
            .getItem();

        assertThat(firstResult.valid()).isTrue();

        // Try to create duplicate
        com.rokkon.pipeline.validation.ValidationResult duplicateResult = getClusterService().createCluster(clusterName)
            .subscribe().withSubscriber(io.smallrye.mutiny.helpers.test.UniAssertSubscriber.create())
            .awaitItem(java.time.Duration.ofSeconds(5))
            .getItem();

        assertThat(duplicateResult.valid()).isFalse();
        assertThat(duplicateResult.errors())
            .contains("Cluster '" + clusterName + "' already exists");
    }

    @org.junit.jupiter.api.Test
    void testDeleteCluster() {
        String clusterName = "base-delete-test-cluster";

        // Create cluster
        com.rokkon.pipeline.validation.ValidationResult createResult = getClusterService().createCluster(clusterName)
            .subscribe().withSubscriber(io.smallrye.mutiny.helpers.test.UniAssertSubscriber.create())
            .awaitItem(java.time.Duration.ofSeconds(5))
            .getItem();

        assertThat(createResult.valid()).isTrue();

        // Verify it exists
        Boolean exists = getClusterService().clusterExists(clusterName)
            .subscribe().withSubscriber(io.smallrye.mutiny.helpers.test.UniAssertSubscriber.create())
            .awaitItem(java.time.Duration.ofSeconds(5))
            .getItem();

        assertThat(exists).isTrue();

        // Delete cluster
        com.rokkon.pipeline.validation.ValidationResult deleteResult = getClusterService().deleteCluster(clusterName)
            .subscribe().withSubscriber(io.smallrye.mutiny.helpers.test.UniAssertSubscriber.create())
            .awaitItem(java.time.Duration.ofSeconds(5))
            .getItem();

        assertThat(deleteResult.valid()).isTrue();

        // Verify it's gone
        exists = getClusterService().clusterExists(clusterName)
            .subscribe().withSubscriber(io.smallrye.mutiny.helpers.test.UniAssertSubscriber.create())
            .awaitItem(java.time.Duration.ofSeconds(5))
            .getItem();

        assertThat(exists).isFalse();
    }

    @org.junit.jupiter.api.Test
    void testEmptyClusterName() {
        // Test with empty string
        com.rokkon.pipeline.validation.ValidationResult result = getClusterService().createCluster("")
            .subscribe().withSubscriber(io.smallrye.mutiny.helpers.test.UniAssertSubscriber.create())
            .awaitItem(java.time.Duration.ofSeconds(5))
            .getItem();

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).contains("Cluster name cannot be empty");

        // Test with null
        result = getClusterService().createCluster(null)
            .subscribe().withSubscriber(io.smallrye.mutiny.helpers.test.UniAssertSubscriber.create())
            .awaitItem(java.time.Duration.ofSeconds(5))
            .getItem();

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).contains("Cluster name cannot be empty");
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
    public static class ClusterServiceITProfile implements QuarkusTestProfile {
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
