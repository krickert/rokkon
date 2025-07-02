package com.rokkon.pipeline.consul.test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.ext.consul.ConsulClient;
import io.vertx.ext.consul.ConsulClientOptions;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.jboss.logging.Logger;

import java.util.UUID;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.net.ServerSocket;
import java.io.IOException;

/**
 * Base class for Consul integration tests.
 * 
 * Each test gets its own isolated namespace using unique prefixes:
 * - Unique cluster name per test
 * - Unique KV prefix per test
 * - Unique service name prefix per test
 * 
 * This allows tests to run in parallel against the same Consul instance
 * without interfering with each other.
 */
@QuarkusIntegrationTest
@QuarkusTestResource(ConsulTestResource.class)
public abstract class ConsulIntegrationTestBase {

    protected static final Logger LOG = Logger.getLogger(ConsulIntegrationTestBase.class);

    // Create Vertx instance manually instead of using @Inject
    protected Vertx vertx = Vertx.vertx();

    // Get Consul host and port from system properties set by ConsulTestResource
    // Use default values in case the properties aren't set
    protected String consulHost = System.getProperty("consul.host", "localhost");
    protected int consulPort = Integer.parseInt(System.getProperty("consul.port", "8500"));

    // Each test gets unique identifiers
    protected String testId;
    protected String testClusterName;
    protected String testKvPrefix;
    protected String testServicePrefix;

    // Direct Consul client for verification
    protected ConsulClient consulClient;

    @BeforeEach
    void setupBase(TestInfo testInfo) {
        // Generate unique test ID based on test method name and a short UUID
        String methodName = testInfo.getTestMethod()
            .map(method -> method.getName())
            .orElse("unknown");
        String shortUuid = UUID.randomUUID().toString().substring(0, 8);

        // Create unique identifiers for this test
        testId = methodName + "-" + shortUuid;
        testClusterName = "test-cluster-" + testId;
        testKvPrefix = "test-kv/" + testId + "/";
        testServicePrefix = "test-service-" + testId + "-";

        LOG.infof("Setting up test '%s' with ID: %s", methodName, testId);
        LOG.infof("  Cluster: %s", testClusterName);
        LOG.infof("  KV Prefix: %s", testKvPrefix);
        LOG.infof("  Service Prefix: %s", testServicePrefix);

        // Create Consul client for direct verification
        ConsulClientOptions options = new ConsulClientOptions()
            .setHost(consulHost)
            .setPort(consulPort);
        consulClient = ConsulClient.create(vertx, options);

        // Allow subclasses to do additional setup
        setupTest();
    }

    @AfterEach
    void teardownBase() {
        LOG.infof("Cleaning up test '%s'", testId);

        try {
            // Clean up any services with our test prefix
            cleanupTestServices();

            // Clean up any KV entries with our test prefix
            cleanupTestKvEntries();

            // Allow subclasses to do additional cleanup
            cleanupTest();

        } catch (Exception e) {
            LOG.errorf("Error during test cleanup: %s", e.getMessage());
        } finally {
            if (consulClient != null) {
                consulClient.close();
            }
        }
    }

    /**
     * Clean up all services that start with our test prefix.
     */
    private void cleanupTestServices() {
        consulClient.localServices()
            .onItem().transformToUni(services -> {
                if (services == null || services.isEmpty()) {
                    return Uni.createFrom().voidItem();
                }

                // Find all services that belong to this test
                return Uni.join().all(
                    services.stream()
                        .filter(service -> service.getName() != null && service.getName().startsWith(testServicePrefix))
                        .map(service -> {
                            LOG.debugf("Deregistering test service: %s", service.getId());
                            return consulClient.deregisterService(service.getId());
                        })
                        .toList()
                ).andFailFast().replaceWithVoid();
            })
            .await().atMost(Duration.ofSeconds(5));
    }

    /**
     * Clean up all KV entries under our test prefix.
     */
    private void cleanupTestKvEntries() {
        // Delete all KV entries under our test prefix (recursive)
        consulClient.deleteValues(testKvPrefix)
            .onFailure().recoverWithNull() // Ignore if prefix doesn't exist
            .await().atMost(Duration.ofSeconds(5));
    }

    /**
     * Get a prefixed service name for this test.
     */
    protected String getTestServiceName(String baseName) {
        return testServicePrefix + baseName;
    }

    /**
     * Get a prefixed KV key for this test.
     */
    protected String getTestKvKey(String key) {
        return testKvPrefix + key;
    }

    /**
     * Override this to perform additional test-specific setup.
     */
    protected void setupTest() {
        // Subclasses can override
    }

    /**
     * Override this to perform additional test-specific cleanup.
     */
    protected void cleanupTest() {
        // Subclasses can override
    }

    /**
     * Helper method to register a test service with health check.
     */
    protected Uni<Void> registerTestService(String serviceName, String host, int port) {
        return consulClient.registerService(
            new io.vertx.ext.consul.ServiceOptions()
                .setId(getTestServiceName(serviceName + "-" + UUID.randomUUID().toString().substring(0, 8)))
                .setName(getTestServiceName(serviceName))
                .setAddress(host)
                .setPort(port)
                .setCheckOptions(new io.vertx.ext.consul.CheckOptions()
                    .setTtl("10s")
                    .setDeregisterAfter("30s"))
        );
    }

    /**
     * Helper method to store test data in KV store.
     */
    protected Uni<Boolean> putTestKv(String key, String value) {
        return consulClient.putValue(getTestKvKey(key), value);
    }

    /**
     * Helper method to get test data from KV store.
     */
    protected Uni<String> getTestKv(String key) {
        return consulClient.getValue(getTestKvKey(key))
            .map(keyValue -> keyValue != null ? keyValue.getValue() : null);
    }

    /**
     * Find an available port for testing.
     */
    protected static int findAvailablePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException("Could not find available port", e);
        }
    }
}
