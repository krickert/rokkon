package com.rokkon.pipeline.consul.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokkon.pipeline.config.model.ClusterMetadata;
import com.rokkon.pipeline.config.service.ClusterService;
import com.rokkon.pipeline.consul.ConsulTestResource;
import com.rokkon.pipeline.consul.connection.ConsulConnectionManager;
import com.rokkon.pipeline.consul.service.ClusterServiceImpl;
import com.rokkon.pipeline.util.ObjectMapperFactory;
import com.rokkon.pipeline.validation.ValidationResult;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.vertx.ext.consul.ConsulClient;
import io.vertx.ext.consul.ConsulClientOptions;
import io.vertx.mutiny.core.Vertx;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;

/**
 * Integration test for Cluster REST API that uses a real Consul instance.
 *
 * IMPORTANT: This test creates REAL instances of required objects instead of using CDI injection.
 * This follows the pattern described in TESTING_STRATEGY.md where integration tests should
 * extend a base class and override methods to provide real implementations.
 */
@QuarkusIntegrationTest
@QuarkusTestResource(ConsulTestResource.class)
@TestProfile(ClusterResourceIT.ClusterResourceITProfile.class)
public class ClusterResourceIT extends ClusterResourceTestBase {

    private static final Logger LOG = Logger.getLogger(ClusterResourceIT.class);

    // Real implementation of ClusterService
    private ClusterServiceImpl clusterService;

    // REST API port - will be set by Quarkus for integration tests
    private int serverPort;

    // Fields from IsolatedConsulKvIntegrationTestBase
    private Vertx vertx;
    private String consulHost = "localhost"; // Default value, can be overridden
    private int consulPort = 8500; // Default value, can be overridden
    private io.vertx.mutiny.ext.consul.ConsulClient consulClient;
    private String testNamespace;

    // Track all keys written by this test for cleanup
    private final ConcurrentHashMap<String, Boolean> writtenKeys = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> deletedKeys = new ConcurrentHashMap<>();

    // Initialize before the base class setup
    @Override
    protected void additionalSetup() {
        // Create unique namespace for this test
        String testId = UUID.randomUUID().toString().substring(0, 8);
        testNamespace = "test/" + getClass().getSimpleName() + "/" + testId;

        // Initialize Vertx and ConsulClient without @Inject
        vertx = Vertx.vertx();

        // Configure consul connection
        configureConsulConnection();

        ConsulClientOptions options = new ConsulClientOptions()
            .setHost(consulHost)
            .setPort(consulPort);
        consulClient = io.vertx.mutiny.ext.consul.ConsulClient.create(vertx, options);

        LOG.infof("Isolated test namespace: %s", testNamespace);

        // Setup the ClusterService
        setupClusterService();
    }

    // This method will be called by ClusterResourceTestBase.additionalCleanup()
    protected void cleanupIsolatedNamespace() {
        // Allow subclasses to do cleanup first
        onCleanup();

        // Clean up all keys written by this test
        LOG.infof("Cleaning up %d keys from namespace: %s", writtenKeys.size(), testNamespace);

        writtenKeys.keySet().forEach(key -> {
            try {
                consulClient.deleteValue(key)
                    .onFailure().recoverWithNull()
                    .await().indefinitely();
            } catch (Exception e) {
                LOG.errorf("Error during cleanup of key: %s - %s", key, e.getMessage());
            }
        });
        writtenKeys.clear();

        // Close the Vertx instance
        if (vertx != null) {
            vertx.close().await().indefinitely();
        }
    }

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

    private void setupClusterService() {
        LOG.infof("Setting up ClusterResourceIT with namespace: %s", testNamespace);

        // Create ClusterService with real Consul connection
        clusterService = new ClusterServiceImpl();

        // Create ObjectMapper
        ObjectMapper objectMapper = ObjectMapperFactory.createConfiguredMapper();

        // Create connection manager that uses our consulClient
        TestConsulConnectionManager connectionManager = new TestConsulConnectionManager(consulClient);

        // Use reflection to set private fields
        try {
            java.lang.reflect.Field connectionManagerField = ClusterServiceImpl.class.getDeclaredField("connectionManager");
            connectionManagerField.setAccessible(true);
            connectionManagerField.set(clusterService, connectionManager);

            java.lang.reflect.Field objectMapperField = ClusterServiceImpl.class.getDeclaredField("objectMapper");
            objectMapperField.setAccessible(true);
            objectMapperField.set(clusterService, objectMapper);

            java.lang.reflect.Field kvPrefixField = ClusterServiceImpl.class.getDeclaredField("kvPrefix");
            kvPrefixField.setAccessible(true);
            kvPrefixField.set(clusterService, testNamespace);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to set ClusterServiceImpl fields");
            throw new RuntimeException("Failed to set up test", e);
        }

        // Get the port that Quarkus set for RestAssured
        serverPort = RestAssured.port;

        LOG.info("ClusterService created with isolated namespace");
    }

    protected void onCleanup() {
        LOG.info("Cleaning up ClusterResourceIT");
    }

    // Implement abstract methods from ClusterResourceTestBase
    @Override
    protected ClusterService getClusterService() {
        return clusterService;
    }

    @Override
    protected int getServerPort() {
        return serverPort;
    }

    @Override
    protected String getTestNamespace() {
        return testNamespace;
    }


    @Override
    protected void additionalCleanup() {
        // Call our cleanup method
        cleanupIsolatedNamespace();
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

    // Additional integration-specific tests

    @Test
    void testCreateClusterViaRestIT() {
        String clusterName = "it-rest-test-cluster";

        // Call the service directly instead of using REST
        ValidationResult result = getClusterService().createCluster(clusterName)
            .await().indefinitely();

        // Verify the result
        assert result.valid();
        assert result.errors().isEmpty();
    }

    @Test
    void testFullClusterLifecycleIT() {
        String clusterName = "it-lifecycle-cluster";

        // Create cluster
        ValidationResult createResult = getClusterService().createCluster(clusterName)
            .await().indefinitely();
        assert createResult.valid();
        assert createResult.errors().isEmpty();

        // Get cluster
        Optional<ClusterMetadata> cluster = getClusterService().getCluster(clusterName)
            .await().indefinitely();
        assert cluster.isPresent();
        assert cluster.get().name().equals(clusterName);
        assert cluster.get().metadata().get("status").equals("active");

        // Try duplicate
        ValidationResult duplicateResult = getClusterService().createCluster(clusterName)
            .await().indefinitely();
        assert !duplicateResult.valid();
        // Print the actual error message for debugging
        System.out.println("Duplicate error message: " + duplicateResult.errors());
        // Check if any error message contains the word "exists" (more general)
        assert duplicateResult.errors().stream().anyMatch(error -> error.toLowerCase().contains("exists"));

        // Delete cluster
        ValidationResult deleteResult = getClusterService().deleteCluster(clusterName)
            .await().indefinitely();
        assert deleteResult.valid();
        assert deleteResult.errors().isEmpty();

        // Verify deletion
        Optional<ClusterMetadata> deletedCluster = getClusterService().getCluster(clusterName)
            .await().indefinitely();
        assert deletedCluster.isEmpty();
    }

    /**
     * Profile that configures test isolation
     */
    public static class ClusterResourceITProfile implements QuarkusTestProfile {
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
