package com.rokkon.pipeline.consul.service;

import com.rokkon.pipeline.config.model.*;
import com.rokkon.pipeline.consul.model.ModuleWhitelistRequest;
import com.rokkon.pipeline.consul.model.ModuleWhitelistResponse;
import com.rokkon.pipeline.consul.test.ConsulTestResource;
import com.rokkon.test.containers.TestModuleContainerResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the ModuleWhitelistService.
 */
@QuarkusTest
@QuarkusTestResource(ConsulTestResource.class)
@QuarkusTestResource(TestModuleContainerResource.class)
class ModuleWhitelistServiceTest {
    
    @Inject
    ModuleWhitelistService whitelistService;
    
    @Inject
    ClusterService clusterService;
    
    @Inject
    PipelineConfigService pipelineConfigService;
    
    private static final String TEST_CLUSTER = "test-cluster";
    private static final String TEST_MODULE = "test-module"; // This is what the container registers as
    
    @BeforeEach
    void setUp() {
        // Create a test cluster using the proper service
        boolean created = clusterService.createCluster(TEST_CLUSTER)
            .await().indefinitely();
        assertThat(created).isTrue();
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
        PipelineUpdateResult result = pipelineConfigService.updatePipeline(
            TEST_CLUSTER,
            "test-pipeline",
            pipeline
        ).await().indefinitely();
        
        assertThat(result.success()).isFalse();
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
        
        // This should succeed because the module is whitelisted
        PipelineUpdateResult pipelineResult = pipelineConfigService.updatePipeline(
            TEST_CLUSTER,
            "test-pipeline",
            pipeline
        ).await().indefinitely();
        
        assertThat(pipelineResult.success()).isTrue();
        
        // Now try to remove the module from whitelist - should fail
        ModuleWhitelistResponse removeResponse = whitelistService.removeModuleFromWhitelist(TEST_CLUSTER, TEST_MODULE)
            .await().indefinitely();
            
        assertThat(removeResponse.success()).isFalse();
        assertThat(removeResponse.message()).contains("currently used in pipeline");
    }
}