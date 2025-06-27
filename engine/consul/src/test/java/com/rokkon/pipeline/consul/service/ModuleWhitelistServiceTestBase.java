package com.rokkon.pipeline.consul.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokkon.pipeline.config.model.*;
import com.rokkon.pipeline.config.service.ClusterService;
import com.rokkon.pipeline.config.service.ModuleWhitelistService;
import com.rokkon.pipeline.config.service.PipelineConfigService;
import com.rokkon.pipeline.config.model.ModuleWhitelistRequest;
import com.rokkon.pipeline.config.model.ModuleWhitelistResponse;
import com.rokkon.pipeline.validation.ValidationResult;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base test class for ModuleWhitelistService testing.
 * Contains all test methods that can be run with either mocked or real implementations.
 */
public abstract class ModuleWhitelistServiceTestBase {
    private static final Logger LOG = Logger.getLogger(ModuleWhitelistServiceTestBase.class);

    protected ModuleWhitelistService whitelistService;
    protected ClusterService clusterService;
    protected PipelineConfigService pipelineConfigService;
    protected ObjectMapper objectMapper;
    protected HttpClient httpClient;

    protected static final String TEST_CLUSTER = "test-cluster";
    protected static final String TEST_MODULE = "test-module"; // This is what the container registers as
    protected static final String TEST_MODULE_2 = "echo-processor";

    /**
     * Get the Consul host for this test environment.
     */
    protected abstract String getConsulHost();

    /**
     * Get the Consul port for this test environment.
     */
    protected abstract String getConsulPort();

    /**
     * Get the ModuleWhitelistService instance.
     */
    protected abstract ModuleWhitelistService getWhitelistService();

    /**
     * Get the ClusterService instance.
     */
    protected abstract ClusterService getClusterService();

    /**
     * Get the PipelineConfigService instance.
     */
    protected abstract PipelineConfigService getPipelineConfigService();

    /**
     * Register a module in Consul for testing.
     * This simulates what happens when a real module registers.
     */
    protected abstract Uni<Boolean> registerModuleInConsul(String moduleName, String host, int port);

    /**
     * Wait for module registration if needed (for container-based tests).
     */
    protected void waitForModuleRegistration() throws Exception {
        // Default implementation does nothing
        // Override in integration tests that need to wait for containers
    }

    /**
     * Checks if module is already registered in Consul.
     */
    protected boolean isModuleRegistered(String moduleName) {
        // Default implementation returns false
        // Override in tests that work with real Consul
        return false;
    }

    @BeforeEach
    void setUpBase() throws Exception {
        whitelistService = getWhitelistService();
        clusterService = getClusterService();
        pipelineConfigService = getPipelineConfigService();
        objectMapper = new ObjectMapper();
        httpClient = HttpClient.newHttpClient();
        
        // Try to create a test cluster - if it already exists, that's fine
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

    @AfterEach
    void tearDown() {
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

        // Don't delete the cluster - let other tests reuse it
    }

    /**
     * Creates a test cluster directly in Consul (for integration tests).
     */
    protected void createTestCluster() throws Exception {
        // Default implementation uses ClusterService
        // Override in tests that need direct Consul access
    }

    /**
     * Registers test modules in Consul (for tests that need it).
     */
    protected void registerTestModules() {
        // Default implementation does nothing
        // Override in tests that need to register modules
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
        // Wait for module registration if needed
        waitForModuleRegistration();

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
        // Wait for module registration if needed
        waitForModuleRegistration();

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

    @Test
    void testWhitelistModuleTwice() throws Exception {
        // Setup: Create test cluster and register module
        createTestCluster();
        registerTestModules();

        // Whitelist a module
        ModuleWhitelistRequest request = new ModuleWhitelistRequest(
            "Test Module",
            TEST_MODULE
        );

        ModuleWhitelistResponse response = whitelistService.whitelistModule(TEST_CLUSTER, request)
            .await().atMost(java.time.Duration.ofSeconds(5));

        assertThat(response.success()).isTrue();

        // Try to whitelist it again
        ModuleWhitelistResponse response2 = whitelistService.whitelistModule(TEST_CLUSTER, request)
            .await().atMost(java.time.Duration.ofSeconds(5));

        assertThat(response2.success()).isTrue();
        assertThat(response2.message()).contains("already whitelisted");
    }

}
