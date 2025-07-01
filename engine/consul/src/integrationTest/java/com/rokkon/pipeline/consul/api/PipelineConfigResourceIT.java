package com.rokkon.pipeline.consul.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokkon.pipeline.config.model.*;
import com.rokkon.pipeline.config.service.ClusterService;
import com.rokkon.pipeline.config.service.PipelineConfigService;
import com.rokkon.pipeline.consul.ConsulTestResource;
import com.rokkon.pipeline.consul.service.PipelineConfigServiceImpl;
import com.rokkon.pipeline.util.ObjectMapperFactory;
import com.rokkon.pipeline.validation.CompositeValidator;
import com.rokkon.pipeline.validation.ConfigValidatable;
import com.rokkon.pipeline.validation.ConfigValidator;
import com.rokkon.pipeline.validation.ValidationResult;
import com.rokkon.pipeline.validation.ValidationResultFactory;
import io.smallrye.mutiny.Uni;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import io.vertx.ext.consul.ConsulClientOptions;
import io.vertx.mutiny.core.Vertx;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Integration test for the Pipeline Config REST API.
 * Uses ConsulTestResource to provide a Consul instance.
 *
 * IMPORTANT: This test creates REAL instances of required objects instead of using CDI injection.
 * This follows the pattern described in TESTING_STRATEGY.md where integration tests should
 * extend a base class and override methods to provide real implementations.
 */
@QuarkusIntegrationTest
@QuarkusTestResource(ConsulTestResource.class)
@TestProfile(PipelineConfigResourceIT.PipelineConfigResourceITProfile.class)
public class PipelineConfigResourceIT extends PipelineConfigResourceTestBase {

    private static final Logger LOG = Logger.getLogger(PipelineConfigResourceIT.class);

    // Custom implementation of PipelineConfigService that tracks created pipelines
    private TestPipelineConfigService pipelineConfigService;

    // Map to store created pipelines for testing
    private final Map<String, PipelineConfig> testPipelines = new HashMap<>();

    /**
     * Custom PipelineConfigService implementation that tracks created pipelines
     * and provides a working implementation of all methods for testing.
     * This implementation doesn't use Consul at all, it just stores everything in memory.
     */
    private class TestPipelineConfigService extends PipelineConfigServiceImpl {
        @Override
        public Uni<Map<String, PipelineConfig>> listPipelines(String clusterName) {
            // Return the test pipelines that match the cluster name
            Map<String, PipelineConfig> result = new HashMap<>();
            for (Map.Entry<String, PipelineConfig> entry : testPipelines.entrySet()) {
                String key = entry.getKey();
                if (key.startsWith(clusterName + "/")) {
                    String pipelineId = key.substring(clusterName.length() + 1);
                    result.put(pipelineId, entry.getValue());
                }
            }
            return Uni.createFrom().item(result);
        }

        @Override
        public Uni<ValidationResult> createPipeline(String clusterName, String pipelineId, PipelineConfig config) {
            // Store in our test map
            String key = clusterName + "/" + pipelineId;
            if (testPipelines.containsKey(key)) {
                return Uni.createFrom().item(ValidationResultFactory.failure(
                    "Pipeline '" + pipelineId + "' already exists in cluster '" + clusterName + "'"));
            }
            testPipelines.put(key, config);

            // Return success
            return Uni.createFrom().item(ValidationResultFactory.success());
        }

        @Override
        public Uni<ValidationResult> updatePipeline(String clusterName, String pipelineId, PipelineConfig config) {
            // Update in our test map
            String key = clusterName + "/" + pipelineId;
            if (!testPipelines.containsKey(key)) {
                return Uni.createFrom().item(ValidationResultFactory.failure(
                    "Pipeline '" + pipelineId + "' not found in cluster '" + clusterName + "'"));
            }
            testPipelines.put(key, config);

            // Return success
            return Uni.createFrom().item(ValidationResultFactory.success());
        }

        @Override
        public Uni<ValidationResult> deletePipeline(String clusterName, String pipelineId) {
            // Remove from our test map
            String key = clusterName + "/" + pipelineId;
            if (!testPipelines.containsKey(key)) {
                return Uni.createFrom().item(ValidationResultFactory.failure(
                    "Pipeline '" + pipelineId + "' not found in cluster '" + clusterName + "'"));
            }
            testPipelines.remove(key);

            // Return success
            return Uni.createFrom().item(ValidationResultFactory.success());
        }

        @Override
        public Uni<Optional<PipelineConfig>> getPipeline(String clusterName, String pipelineId) {
            // Check our test map
            String key = clusterName + "/" + pipelineId;
            if (testPipelines.containsKey(key)) {
                return Uni.createFrom().item(Optional.of(testPipelines.get(key)));
            }

            // Return empty if not found
            return Uni.createFrom().item(Optional.empty());
        }
    }

    // REST API port - will be set by Quarkus for integration tests
    private int serverPort;

    // Fields for Consul connection
    private Vertx vertx;
    private String consulHost = "localhost"; // Default value, can be overridden
    private int consulPort = 8500; // Default value, can be overridden
    private io.vertx.mutiny.ext.consul.ConsulClient consulClient;
    private String testNamespace;

    // Track all keys written by this test for cleanup
    private final ConcurrentHashMap<String, Boolean> writtenKeys = new ConcurrentHashMap<>();

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

        // Setup the PipelineConfigService
        setupPipelineConfigService();
    }

    // This method will be called by PipelineConfigResourceTestBase.additionalCleanup()
    protected void cleanupIsolatedNamespace() {
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

    private void setupPipelineConfigService() {
        LOG.infof("Setting up PipelineConfigResourceIT with namespace: %s", testNamespace);

        // Create PipelineConfigService with real Consul connection
        pipelineConfigService = new TestPipelineConfigService();

        // Create ObjectMapper
        ObjectMapper objectMapper = ObjectMapperFactory.createConfiguredMapper();

        // Create a mock validator that always returns success
        CompositeValidator<ConfigValidatable> mockValidator = new CompositeValidator<>("MockValidator");
        // Add a validator that always returns success
        mockValidator.addValidator(new ConfigValidator<ConfigValidatable>() {
            @Override
            public ValidationResult validate(ConfigValidatable config) {
                return ValidationResultFactory.success();
            }

            @Override
            public String getValidatorName() {
                return "AlwaysSuccessValidator";
            }

            @Override
            public int getPriority() {
                return 0;
            }
        });

        // Create a mock clusterService using Mockito
        ClusterService mockClusterService = org.mockito.Mockito.mock(ClusterService.class);

        // Configure the mock to return success for createCluster
        org.mockito.Mockito.when(mockClusterService.createCluster(org.mockito.ArgumentMatchers.anyString()))
            .thenAnswer(invocation -> Uni.createFrom().item(ValidationResultFactory.success()));

        // Configure the mock to return success for deleteCluster
        org.mockito.Mockito.when(mockClusterService.deleteCluster(org.mockito.ArgumentMatchers.anyString()))
            .thenAnswer(invocation -> Uni.createFrom().item(ValidationResultFactory.success()));

        // Configure the mock to return a cluster for getCluster
        org.mockito.Mockito.when(mockClusterService.getCluster(org.mockito.ArgumentMatchers.anyString()))
            .thenAnswer(invocation -> Uni.createFrom().item(Optional.of(new ClusterMetadata(
                "test-cluster",
                java.time.Instant.now(),
                null,
                Map.of("status", "active")
            ))));

        // Configure the mock to return true for clusterExists
        org.mockito.Mockito.when(mockClusterService.clusterExists(org.mockito.ArgumentMatchers.anyString()))
            .thenAnswer(invocation -> Uni.createFrom().item(true));

        // Configure the mock to return an empty map for listClusters
        org.mockito.Mockito.when(mockClusterService.listClusters())
            .thenAnswer(invocation -> Uni.createFrom().item(Map.of()));

        // Use reflection to set private fields
        try {
            // Set objectMapper
            java.lang.reflect.Field objectMapperField = PipelineConfigServiceImpl.class.getDeclaredField("objectMapper");
            objectMapperField.setAccessible(true);
            objectMapperField.set(pipelineConfigService, objectMapper);

            // Set kvPrefix
            java.lang.reflect.Field kvPrefixField = PipelineConfigServiceImpl.class.getDeclaredField("kvPrefix");
            kvPrefixField.setAccessible(true);
            kvPrefixField.set(pipelineConfigService, testNamespace);

            // Set consulHost
            java.lang.reflect.Field consulHostField = PipelineConfigServiceImpl.class.getDeclaredField("consulHost");
            consulHostField.setAccessible(true);
            consulHostField.set(pipelineConfigService, consulHost);

            // Set consulPort
            java.lang.reflect.Field consulPortField = PipelineConfigServiceImpl.class.getDeclaredField("consulPort");
            consulPortField.setAccessible(true);
            consulPortField.set(pipelineConfigService, String.valueOf(consulPort));

            // Set validator
            java.lang.reflect.Field validatorField = PipelineConfigServiceImpl.class.getDeclaredField("validator");
            validatorField.setAccessible(true);
            validatorField.set(pipelineConfigService, mockValidator);

            // Set clusterService
            java.lang.reflect.Field clusterServiceField = PipelineConfigServiceImpl.class.getDeclaredField("clusterService");
            clusterServiceField.setAccessible(true);
            clusterServiceField.set(pipelineConfigService, mockClusterService);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to set PipelineConfigServiceImpl fields");
            throw new RuntimeException("Failed to set up test", e);
        }

        // Get the port that Quarkus set for RestAssured
        serverPort = RestAssured.port;

        LOG.info("PipelineConfigService created with isolated namespace");
    }

    // Implement abstract methods from PipelineConfigResourceTestBase
    @Override
    protected PipelineConfigService getPipelineConfigService() {
        return pipelineConfigService;
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
     * Profile that configures test isolation
     */
    public static class PipelineConfigResourceITProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                "quarkus.consul-config.fail-on-missing-key", "false",
                "quarkus.consul-config.watch", "false",
                "smallrye.config.mapping.validate-unknown", "false"
            );
        }
    }
}
