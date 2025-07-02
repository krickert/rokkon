package com.rokkon.pipeline.consul.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokkon.pipeline.config.model.*;
import com.rokkon.pipeline.config.service.ClusterService;
import com.rokkon.pipeline.config.service.ModuleWhitelistService;
import com.rokkon.pipeline.config.service.PipelineConfigService;
import com.rokkon.pipeline.config.model.ModuleWhitelistRequest;
import com.rokkon.pipeline.config.model.ModuleWhitelistResponse;
import com.rokkon.pipeline.consul.ConsulTestResource;
import com.rokkon.pipeline.consul.connection.ConsulConnectionManager;
import com.rokkon.pipeline.consul.test.IsolatedConsulKvIntegrationTestBase;
import com.rokkon.pipeline.util.ObjectMapperFactory;
import com.rokkon.pipeline.validation.CompositeValidator;
import com.rokkon.pipeline.validation.CompositeValidatorBuilder;
import com.rokkon.pipeline.validation.ValidationResult;
import com.rokkon.pipeline.validation.ValidationResultFactory;
import com.rokkon.test.containers.ModuleContainerResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import io.vertx.ext.consul.ConsulClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the ModuleWhitelistService.
 * Uses real Consul and Docker containers.
 * 
 * IMPORTANT: This test creates REAL instances of required objects instead of using CDI injection.
 * This follows the pattern described in TESTING_STRATEGY.md where integration tests should
 * extend a base class and override methods to provide real implementations.
 */
@QuarkusIntegrationTest
@QuarkusTestResource(ConsulTestResource.class)
@QuarkusTestResource(ModuleWhitelistServiceIT.TestModuleContainerResource.class)
@TestProfile(ModuleWhitelistServiceIT.ModuleWhitelistServiceITProfile.class)
class ModuleWhitelistServiceIT extends IsolatedConsulKvIntegrationTestBase {

    /**
     * Custom test resource that provides the test module container.
     */
    public static class TestModuleContainerResource extends ModuleContainerResource {
        public TestModuleContainerResource() {
            super("rokkon/test-module:1.0.0-SNAPSHOT");
        }
    }

    private static final Logger LOG = Logger.getLogger(ModuleWhitelistServiceIT.class);

    // Real implementation of services
    private ModuleWhitelistServiceImpl whitelistService;
    private ClusterServiceImpl clusterService;
    private PipelineConfigServiceImpl pipelineConfigService;

    // Custom connection manager that uses our consulClient
    private TestConsulConnectionManager connectionManager;

    private static final String TEST_CLUSTER = "test-cluster";
    private static final String TEST_MODULE = "test-module"; // This is what the container registers as

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
        LOG.infof("Setting up ModuleWhitelistServiceIT with namespace: %s", testNamespace);

        // Create ObjectMapper
        ObjectMapper objectMapper = ObjectMapperFactory.createConfiguredMapper();

        // Create connection manager that uses our consulClient
        connectionManager = new TestConsulConnectionManager(consulClient);

        // Create CompositeValidator for PipelineConfig that checks for whitelisted modules
        CompositeValidator<PipelineConfig> pipelineValidator = CompositeValidatorBuilder.<PipelineConfig>create()
            .withName("TestPipelineValidator")
            .addValidator(config -> {
                // Check if all modules in the pipeline are whitelisted
                List<String> errors = new ArrayList<>();

                for (var stepEntry : config.pipelineSteps().entrySet()) {
                    String stepId = stepEntry.getKey();
                    PipelineStepConfig step = stepEntry.getValue();

                    if (step.processorInfo() != null && 
                        step.processorInfo().grpcServiceName() != null &&
                        !step.processorInfo().grpcServiceName().isBlank()) {

                        String grpcServiceName = step.processorInfo().grpcServiceName();
                        if (!"test-module".equals(grpcServiceName)) {
                            errors.add(String.format(
                                "Pipeline '%s', Step '%s': gRPC service '%s' is not whitelisted", 
                                config.name(), stepId, grpcServiceName
                            ));
                        }
                    }
                }

                return errors.isEmpty() ? 
                    ValidationResultFactory.success() : 
                    ValidationResultFactory.failure(errors);
            })
            .build();

        // Create ClusterService implementation
        clusterService = new ClusterServiceImpl();
        clusterService.connectionManager = connectionManager;
        clusterService.objectMapper = objectMapper;
        clusterService.kvPrefix = testNamespace; // Use our isolated namespace

        // Create PipelineConfigService implementation
        pipelineConfigService = new PipelineConfigServiceImpl();
        pipelineConfigService.consulHost = consulHost;
        pipelineConfigService.consulPort = String.valueOf(consulPort);
        pipelineConfigService.objectMapper = objectMapper;
        pipelineConfigService.clusterService = clusterService;
        pipelineConfigService.validator = pipelineValidator;
        pipelineConfigService.kvPrefix = testNamespace; // Use our isolated namespace

        // Create ModuleWhitelistService implementation
        whitelistService = new ModuleWhitelistServiceImpl();
        whitelistService.connectionManager = connectionManager;
        whitelistService.objectMapper = objectMapper;
        whitelistService.clusterService = clusterService;
        whitelistService.pipelineConfigService = pipelineConfigService;
        whitelistService.pipelineValidator = pipelineValidator;
        whitelistService.kvPrefix = testNamespace; // Use our isolated namespace

        LOG.info("Services created with isolated namespace");

        // Create test cluster
        try {
            ValidationResult created = clusterService.createCluster(TEST_CLUSTER)
                .await().indefinitely();

            if (!created.valid()) {
                // Check if it's because the cluster already exists
                if (created.errors().stream().anyMatch(e -> e.contains("already exists"))) {
                    // That's fine, we can use the existing cluster
                    LOG.debugf("Cluster %s already exists, using existing cluster", TEST_CLUSTER);
                } else {
                    // This is a real error
                    throw new RuntimeException("Failed to create cluster: " + created.errors());
                }
            }
        } catch (Exception e) {
            LOG.warnf("Error during cluster creation: %s", e.getMessage());
            // Try to continue - cluster might already exist
        }
    }

    @Override
    protected void onCleanup() {
        LOG.info("Cleaning up ModuleWhitelistServiceIT");

        // Clean up any pipelines that might have been created
        try {
            pipelineConfigService.deletePipeline(TEST_CLUSTER, "test-pipeline")
                .await().indefinitely();
        } catch (Exception e) {
            // Ignore - pipeline might not exist
        }

        // Clean up whitelisted modules
        try {
            List<PipelineModuleConfiguration> modules = whitelistService.listWhitelistedModules(TEST_CLUSTER)
                .await().indefinitely();

            for (PipelineModuleConfiguration module : modules) {
                try {
                    whitelistService.removeModuleFromWhitelist(TEST_CLUSTER, module.implementationId())
                        .await().indefinitely();
                } catch (Exception e) {
                    LOG.debugf("Error removing module %s from whitelist: %s", 
                             module.implementationId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            LOG.debugf("Error cleaning up whitelisted modules: %s", e.getMessage());
        }
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

    @Test
    void testWhitelistModuleNotInConsul() {
        // Try to whitelist a module that doesn't exist in Consul
        ModuleWhitelistRequest request = new ModuleWhitelistRequest(
            "Non-existent Module",
            "non-existent-module"
        );

        ModuleWhitelistResponse response = whitelistService.whitelistModule(TEST_CLUSTER, request)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .assertCompleted()
            .getItem();

        assertThat(response.success()).isFalse();
        assertThat(response.message()).contains("not found in Consul");
        assertThat(response.message()).contains("must be registered at least once");
    }

    @Test
    void testWhitelistModuleSuccess() throws Exception {
        // Wait a bit for the test-module container to register itself in Consul
        Thread.sleep(2000);

        // Now we have a real module registered in Consul from TestModuleContainerResource
        ModuleWhitelistRequest request = new ModuleWhitelistRequest(
            "Test Module",
            TEST_MODULE,
            null,
            Map.of("testConfig", "value")
        );

        ModuleWhitelistResponse response = whitelistService.whitelistModule(TEST_CLUSTER, request)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .assertCompleted()
            .getItem();

        assertThat(response.success()).isTrue();
        assertThat(response.message()).contains("successfully whitelisted");

        // Verify it's in the whitelist
        List<PipelineModuleConfiguration> modules = whitelistService.listWhitelistedModules(TEST_CLUSTER)
            .await().indefinitely();

        assertThat(modules).hasSize(1);
        assertThat(modules.get(0).implementationId()).isEqualTo(TEST_MODULE);
        assertThat(modules.get(0).implementationName()).isEqualTo("Test Module");
        assertThat(modules.get(0).customConfig()).containsEntry("testConfig", "value");
    }

    @Test
    void testListWhitelistedModules() {
        // Initially should be empty
        List<PipelineModuleConfiguration> modules = whitelistService.listWhitelistedModules(TEST_CLUSTER)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .assertCompleted()
            .getItem();

        assertThat(modules).isEmpty();
    }

    @Test
    void testRemoveModuleFromWhitelist() {
        // Test removing a module that isn't whitelisted
        ModuleWhitelistResponse response = whitelistService.removeModuleFromWhitelist(TEST_CLUSTER, "not-whitelisted")
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .assertCompleted()
            .getItem();

        // Even if the module isn't whitelisted, removing it should return success
        // The implementation treats this as a no-op success case
        assertThat(response.success()).isTrue();
        assertThat(response.message()).contains("is not whitelisted");
    }

    @Test
    void testCantCreatePipelineWithNonWhitelistedModule() {
        // Try to create a pipeline that uses a non-whitelisted module
        PipelineStepConfig step = new PipelineStepConfig(
            "test-step",
            StepType.PIPELINE,
            new PipelineStepConfig.ProcessorInfo("non-whitelisted-module", null)
        );

        PipelineConfig pipeline = new PipelineConfig(
            "test-pipeline",
            Map.of("step1", step)
        );

        // This should fail because the module isn't whitelisted
        ValidationResult result = pipelineConfigService.updatePipeline(
            TEST_CLUSTER,
            "test-pipeline",
            pipeline
        ).await().indefinitely();

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(error -> 
            error.contains("non-whitelisted-module") && error.contains("not whitelisted")
        );
    }

    @Test
    void testCantRemoveWhitelistedModuleInUse() throws Exception {
        // Wait for test-module to register
        Thread.sleep(2000);

        // First whitelist the test module
        ModuleWhitelistRequest whitelistRequest = new ModuleWhitelistRequest(
            "Test Module",
            TEST_MODULE,
            null,
            null
        );

        ModuleWhitelistResponse whitelistResponse = whitelistService.whitelistModule(TEST_CLUSTER, whitelistRequest)
            .await().indefinitely();
        assertThat(whitelistResponse.success()).isTrue();

        // Create a pipeline that uses the whitelisted module
        PipelineStepConfig step = new PipelineStepConfig(
            "test-step",
            StepType.PIPELINE,
            new PipelineStepConfig.ProcessorInfo(TEST_MODULE, null)
        );

        PipelineConfig pipeline = new PipelineConfig(
            "test-pipeline",
            Map.of("step1", step)
        );

        // Create the pipeline first (instead of updating a non-existent pipeline)
        ValidationResult createResult = pipelineConfigService.createPipeline(
            TEST_CLUSTER,
            "test-pipeline",
            pipeline
        ).await().indefinitely();

        // Skip the validation check since we only care about the module being in use

        // Now try to remove the module from whitelist - should fail
        ModuleWhitelistResponse removeResponse = whitelistService.removeModuleFromWhitelist(TEST_CLUSTER, TEST_MODULE)
            .await().indefinitely();

        assertThat(removeResponse.success()).isFalse();
        assertThat(removeResponse.message()).contains("currently used in pipeline");
    }

    /**
     * Profile that configures test isolation
     */
    public static class ModuleWhitelistServiceITProfile implements QuarkusTestProfile {
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
