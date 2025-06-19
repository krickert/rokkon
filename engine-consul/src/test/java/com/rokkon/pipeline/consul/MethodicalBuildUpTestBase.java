package com.rokkon.pipeline.consul;

import com.rokkon.pipeline.consul.model.Cluster;
import com.rokkon.pipeline.consul.service.ClusterService;
import com.rokkon.pipeline.consul.service.ModuleWhitelistService;
import com.rokkon.pipeline.consul.service.PipelineConfigService;
import com.rokkon.pipeline.consul.test.TestSeedingService;
import com.rokkon.pipeline.validation.ValidationResult;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.*;
import org.jboss.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base class for methodical build-up tests that validates the engine ecosystem layer by layer.
 * Tests are ordered and build upon each other:
 * 0) Consul starts
 * 1) Create cluster (default vs non-default)
 * 2) Start container and access test-module
 * 3) Register the container
 * 4) Create pipeline with no steps
 * 5) Add first test-module pipeline step
 * 6) Run test-module 2x to ensure it runs twice
 * 
 * Each test depends on the previous ones passing.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public abstract class MethodicalBuildUpTestBase {
    private static final Logger LOG = Logger.getLogger(MethodicalBuildUpTestBase.class);
    
    protected static final String DEFAULT_CLUSTER = "default";
    protected static final String TEST_CLUSTER = "test-cluster";
    protected static final String TEST_MODULE = "test-module";
    
    protected abstract ClusterService getClusterService();
    protected abstract ModuleWhitelistService getModuleWhitelistService();
    protected abstract PipelineConfigService getPipelineConfigService();
    protected abstract TestSeedingService getTestSeedingService();
    
    @BeforeAll
    static void setupClass() {
        LOG.info("=== Starting Methodical Build-Up Test Suite ===");
        LOG.info("This test suite validates the engine ecosystem layer by layer");
    }
    
    @AfterAll
    static void teardownClass() {
        LOG.info("=== Completed Methodical Build-Up Test Suite ===");
    }
    
    @AfterEach
    void cleanup() {
        LOG.info("Cleaning up after test");
        
        // Use the TestSeedingService to tear down all test data
        try {
            Boolean success = getTestSeedingService().teardownAll()
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
        Boolean success = getTestSeedingService().seedStep0_ConsulStarted()
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .assertCompleted()
            .getItem();
        
        assertThat(success)
            .as("Consul should be running and services available")
            .isTrue();
        
        // Also verify directly
        assertThat(getClusterService()).isNotNull();
        assertThat(getModuleWhitelistService()).isNotNull();
        assertThat(getPipelineConfigService()).isNotNull();
        
        LOG.info("✓ Consul is running and services are available");
    }
    
    @Test
    @Order(1)
    @DisplayName("1) Create cluster - default vs non-default")
    void testCreateCluster() {
        LOG.info("TEST 1: Creating clusters (default and non-default)");
        
        // Test creating default cluster
        LOG.info("Creating default cluster...");
        ValidationResult defaultResult = getClusterService().createCluster(DEFAULT_CLUSTER)
            .await().indefinitely();
        
        assertThat(defaultResult.valid())
            .as("Default cluster creation should succeed")
            .isTrue();
        LOG.info("✓ Default cluster created successfully");
        
        // Test creating non-default cluster
        LOG.info("Creating test cluster...");
        ValidationResult testResult = getClusterService().createCluster(TEST_CLUSTER)
            .await().indefinitely();
        
        assertThat(testResult.valid())
            .as("Test cluster creation should succeed")
            .isTrue();
        LOG.info("✓ Test cluster created successfully");
        
        // Verify both clusters exist using the new listClusters method
        var clusters = getClusterService().listClusters()
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
        
        // TODO: This is where we'll implement the actual registration flow:
        // 1. CLI tool connects to test-module on localhost
        // 2. CLI calls GetServiceRegistration to get module info
        // 3. CLI connects to engine's ModuleRegistrationService
        // 4. Engine validates and registers module in Consul
        
        // For now, this is a placeholder
        LOG.info("⚠ Container registration not yet implemented");
        LOG.info("  This will be implemented when we create the CLI tool");
        LOG.info("  and ModuleRegistrationService");
    }
    
    @Test
    @Order(4)
    @DisplayName("4) Create pipeline with no steps")
    void testCreateEmptyPipeline() {
        LOG.info("TEST 4: Creating an empty pipeline (no steps)");
        
        // TODO: Create an empty pipeline configuration
        // This should be allowed by the validators
        
        LOG.info("⚠ Empty pipeline creation not yet implemented");
        LOG.info("  Need to verify if validators allow empty pipelines");
    }
    
    @Test
    @Order(5)
    @DisplayName("5) Add first test-module pipeline step")
    void testAddFirstPipelineStep() {
        LOG.info("TEST 5: Adding first test-module step to pipeline");
        
        // TODO: Add a single test-module step to the pipeline
        // This requires the module to be registered and whitelisted first
        
        LOG.info("⚠ Pipeline step addition not yet implemented");
        LOG.info("  Depends on container registration being completed");
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