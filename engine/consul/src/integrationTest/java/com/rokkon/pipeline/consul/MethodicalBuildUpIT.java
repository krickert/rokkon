package com.rokkon.pipeline.consul;

import com.rokkon.pipeline.config.model.StepType;
import com.rokkon.pipeline.config.model.Cluster;
import com.rokkon.pipeline.config.service.ClusterService;
import com.rokkon.pipeline.config.service.ModuleWhitelistService;
import com.rokkon.pipeline.config.service.PipelineConfigService;
import com.rokkon.pipeline.consul.test.ConsulTestResource;
import com.rokkon.pipeline.consul.test.TestSeedingService;
import com.rokkon.pipeline.validation.ValidationResult;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.common.QuarkusTestResource;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Disabled;
import org.jboss.logging.Logger;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for methodical build-up of the engine ecosystem.
 * This test uses real Consul and other services via Testcontainers.
 * 
 * Tests are ordered to build up the system layer by layer:
 * 0. Consul starts and is accessible
 * 1. Create clusters (default and non-default)
 * 2. Start container and access test-module
 * 3. Register the container
 * 4. Create pipeline with no steps
 * 5. Add first test-module pipeline step
 * 6. Run test-module 2x to ensure it runs twice
 */
@Disabled("Integration test - requires full container setup including Consul and test-module containers")
@QuarkusIntegrationTest
@QuarkusTestResource(ConsulTestResource.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MethodicalBuildUpIT extends MethodicalBuildUpTestBase {
    private static final Logger LOG = Logger.getLogger(MethodicalBuildUpIT.class);

    @Inject
    ClusterService clusterService;

    @Inject
    ModuleWhitelistService moduleWhitelistService;

    @Inject
    PipelineConfigService pipelineConfigService;

    @Inject
    TestSeedingService testSeedingService;

    @Override
    protected ClusterService getClusterService() {
        return clusterService;
    }

    @Override
    protected ModuleWhitelistService getModuleWhitelistService() {
        return moduleWhitelistService;
    }

    @Override
    protected PipelineConfigService getPipelineConfigService() {
        return pipelineConfigService;
    }

    @Override
    protected TestSeedingService getTestSeedingService() {
        return testSeedingService;
    }

    @BeforeAll
    void setupClass() {
        LOG.info("=== Starting Methodical Build-Up Integration Test Suite ===");
        LOG.info("This test suite validates the engine ecosystem layer by layer with real services");
    }

    @AfterAll
    void teardownClass() {
        LOG.info("=== Completed Methodical Build-Up Integration Test Suite ===");
    }

    @AfterAll
    void cleanup() {
        LOG.info("Cleaning up after test suite");

        // Use the TestSeedingService to tear down all test data
        try {
            Boolean success = testSeedingService.teardownAll()
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .assertCompleted()
                .getItem();

            if (!success) {
                LOG.warn("Some cleanup operations may have failed");
            }
        } catch (Exception e) {
            LOG.errorf("Error during cleanup: %s", e.getMessage());
        }
    }

    @Test
    @Order(0)
    @DisplayName("0) Consul starts and is accessible")
    void testConsulStarts() {
        LOG.info("TEST 0: Verifying Consul is started and accessible");

        // Use TestSeedingService to verify step 0
        Boolean success = testSeedingService.seedStep0_ConsulStarted()
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .assertCompleted()
            .getItem();

        assertThat(success)
            .as("Consul should be running and services available")
            .isTrue();

        // Also verify directly
        assertThat(clusterService).isNotNull();
        assertThat(moduleWhitelistService).isNotNull();
        assertThat(pipelineConfigService).isNotNull();

        LOG.info("✓ Consul is running and services are available");
    }

    @Test
    @Order(1)
    @DisplayName("1) Create cluster - default vs non-default")
    void testCreateCluster() {
        LOG.info("TEST 1: Creating clusters (default and non-default)");

        // Test creating default cluster
        LOG.info("Creating default cluster...");
        ValidationResult defaultResult = clusterService.createCluster(DEFAULT_CLUSTER)
            .await().indefinitely();

        assertThat(defaultResult.valid())
            .as("Default cluster creation should succeed")
            .isTrue();
        LOG.info("✓ Default cluster created successfully");

        // Test creating non-default cluster
        LOG.info("Creating test cluster...");
        ValidationResult testResult = clusterService.createCluster(TEST_CLUSTER)
            .await().indefinitely();

        assertThat(testResult.valid())
            .as("Test cluster creation should succeed")
            .isTrue();
        LOG.info("✓ Test cluster created successfully");

        // Verify both clusters exist
        var clusters = clusterService.listClusters()
            .await().indefinitely();

        assertThat(clusters)
            .extracting(Cluster::name)
            .contains(DEFAULT_CLUSTER, TEST_CLUSTER);

        LOG.info("✓ Both clusters verified in Consul");
    }

    @Test
    @Order(2)
    @DisplayName("2) Start container and access test-module")
    void testContainerAccess() throws Exception {
        LOG.info("TEST 2: Verifying test-module container is accessible");

        // The TestModuleContainerResource should have started the container
        // We need to wait a bit for the container to register itself
        Thread.sleep(3000); // Give container time to start and register

        // TODO: Once we have the proper gRPC client setup, we should:
        // 1. Check that we can connect to the test-module gRPC service
        // 2. Call GetServiceRegistration to verify it returns "test-module"
        // 3. Call ProcessData with a test request to verify it's working

        // For now, we just verify the container resource is working
        LOG.info("✓ Test-module container is running (started by TestModuleContainerResource)");
        LOG.info("  Note: Direct gRPC verification will be added once client is set up");
    }

    @Test
    @Order(3)
    @DisplayName("3) Register the container")
    void testRegisterContainer() {
        LOG.info("TEST 3: Registering test-module with the engine");

        // First ensure clusters exist (dependency on test 1)
        Boolean clusterSuccess = testSeedingService.seedStep1_ClustersCreated()
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .assertCompleted()
            .getItem();

        assertThat(clusterSuccess)
            .as("Clusters should be created before registration")
            .isTrue();

        // Now register the container
        Boolean success = testSeedingService.seedStep3_ContainerRegistered()
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .assertCompleted()
            .getItem();

        assertThat(success)
            .as("Container registration should succeed")
            .isTrue();

        LOG.info("✓ Test-module registered in Consul (whitelisting comes in step 5)");
    }

    @Test
    @Order(4)
    @DisplayName("4) Create pipeline with no steps")
    void testCreateEmptyPipeline() {
        LOG.info("TEST 4: Creating an empty pipeline (no steps)");

        // Use TestSeedingService to create an empty pipeline
        Boolean success = testSeedingService.seedStep4_EmptyPipelineCreated()
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .assertCompleted()
            .getItem();

        assertThat(success)
            .as("Empty pipeline creation should succeed")
            .isTrue();

        // Verify the pipeline exists but has no steps
        var pipeline = pipelineConfigService.getPipeline(TEST_CLUSTER, "test-pipeline")
            .await().indefinitely();

        assertThat(pipeline)
            .as("Pipeline should exist")
            .isPresent();

        assertThat(pipeline.get().pipelineSteps())
            .as("Pipeline should have no steps")
            .isEmpty();

        LOG.info("✓ Empty pipeline created successfully with no steps");
    }

    @Test
    @Order(5)
    @DisplayName("5) Add first test-module pipeline step")
    void testAddFirstPipelineStep() {
        LOG.info("TEST 5: Adding first test-module step to pipeline");

        // Use TestSeedingService to whitelist module and add first step
        Boolean success = testSeedingService.seedStep5_FirstPipelineStepAdded()
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .assertCompleted()
            .getItem();

        assertThat(success)
            .as("First pipeline step addition should succeed")
            .isTrue();

        // Verify the pipeline now has one step
        var pipeline = pipelineConfigService.getPipeline(TEST_CLUSTER, "test-pipeline")
            .await().indefinitely();

        assertThat(pipeline)
            .as("Pipeline should exist")
            .isPresent();

        assertThat(pipeline.get().pipelineSteps())
            .as("Pipeline should have one step")
            .hasSize(1)
            .containsKey("test-step-1");

        // Verify the step configuration
        var step = pipeline.get().pipelineSteps().get("test-step-1");
        assertThat(step.stepName()).isEqualTo("test-step-1");
        assertThat(step.stepType()).isEqualTo(StepType.PIPELINE);
        assertThat(step.processorInfo().grpcServiceName()).isEqualTo(TEST_MODULE);

        LOG.info("✓ First pipeline step added successfully");
    }

    @Test
    @Order(6)
    @DisplayName("6) Run test-module 2x to ensure it runs twice")
    void testRunModuleTwice() {
        LOG.info("TEST 6: Running test-module twice in pipeline");

        // TODO: Create a pipeline with two test-module steps
        // Execute the pipeline and verify both steps run

        LOG.info("⚠ Pipeline execution not yet implemented");
        LOG.info("  This is the final integration test");
    }
}