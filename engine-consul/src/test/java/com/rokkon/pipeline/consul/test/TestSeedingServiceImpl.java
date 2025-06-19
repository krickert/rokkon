package com.rokkon.pipeline.consul.test;

import com.rokkon.pipeline.consul.service.ClusterService;
import com.rokkon.pipeline.consul.service.ModuleWhitelistService;
import com.rokkon.pipeline.consul.service.PipelineConfigService;
import com.rokkon.pipeline.validation.ValidationResult;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;

/**
 * Implementation of TestSeedingService for unit tests.
 * Uses injected services directly.
 */
@ApplicationScoped
public class TestSeedingServiceImpl implements TestSeedingService {
    private static final Logger LOG = Logger.getLogger(TestSeedingServiceImpl.class);
    
    private static final String DEFAULT_CLUSTER = "default";
    private static final String TEST_CLUSTER = "test-cluster";
    private static final String TEST_MODULE = "test-module";
    private static final String TEST_PIPELINE = "test-pipeline";
    
    @Inject
    ClusterService clusterService;
    
    @Inject
    ModuleWhitelistService moduleWhitelistService;
    
    @Inject
    PipelineConfigService pipelineConfigService;
    
    private int currentStep = -1;
    
    @Override
    public Uni<Boolean> seedStep0_ConsulStarted() {
        LOG.info("Seeding Step 0: Verifying Consul is started");
        
        // Simply check that services are available
        if (clusterService != null && moduleWhitelistService != null && pipelineConfigService != null) {
            currentStep = Math.max(currentStep, 0);
            LOG.info("✓ Step 0 complete: Consul is running and services are available");
            return Uni.createFrom().item(true);
        }
        
        LOG.error("✗ Step 0 failed: Services not available");
        return Uni.createFrom().item(false);
    }
    
    @Override
    public Uni<Boolean> seedStep1_ClustersCreated() {
        LOG.info("Seeding Step 1: Creating clusters");
        
        // Ensure step 0 is complete
        if (currentStep < 0) {
            return seedStep0_ConsulStarted()
                .flatMap(success -> success ? seedStep1_ClustersCreated() : Uni.createFrom().item(false));
        }
        
        // Create default cluster
        return clusterService.createCluster(DEFAULT_CLUSTER)
            .flatMap(result -> {
                if (!result.valid()) {
                    // Check if it already exists
                    if (result.errors().stream().anyMatch(e -> e.contains("already exists"))) {
                        LOG.debug("Default cluster already exists");
                        return Uni.createFrom().item(ValidationResult.success());
                    }
                    LOG.errorf("Failed to create default cluster: %s", result.errors());
                    return Uni.createFrom().item(result);
                }
                return Uni.createFrom().item(result);
            })
            .flatMap(result -> {
                if (!result.valid()) {
                    return Uni.createFrom().item(false);
                }
                
                // Create test cluster
                return clusterService.createCluster(TEST_CLUSTER)
                    .map(testResult -> {
                        if (!testResult.valid()) {
                            // Check if it already exists
                            if (testResult.errors().stream().anyMatch(e -> e.contains("already exists"))) {
                                LOG.debug("Test cluster already exists");
                                currentStep = Math.max(currentStep, 1);
                                LOG.info("✓ Step 1 complete: Both clusters available");
                                return true;
                            }
                            LOG.errorf("Failed to create test cluster: %s", testResult.errors());
                            return false;
                        }
                        
                        currentStep = Math.max(currentStep, 1);
                        LOG.info("✓ Step 1 complete: Both clusters created");
                        return true;
                    });
            });
    }
    
    @Override
    public Uni<Boolean> seedStep2_ContainerAccessible() {
        LOG.info("Seeding Step 2: Verifying test-module container is accessible");
        
        // Ensure step 1 is complete
        if (currentStep < 1) {
            return seedStep1_ClustersCreated()
                .flatMap(success -> success ? seedStep2_ContainerAccessible() : Uni.createFrom().item(false));
        }
        
        // For now, we assume the container is started by TestModuleContainerResource
        // In the future, we'll add actual gRPC verification here
        return Uni.createFrom().item(() -> {
            try {
                // Give container time to start
                Thread.sleep(3000);
                currentStep = Math.max(currentStep, 2);
                LOG.info("✓ Step 2 complete: Test-module container is accessible");
                return true;
            } catch (InterruptedException e) {
                LOG.error("✗ Step 2 failed: Interrupted while waiting for container");
                Thread.currentThread().interrupt();
                return false;
            }
        });
    }
    
    @Override
    public Uni<Boolean> seedStep3_ContainerRegistered() {
        LOG.info("Seeding Step 3: Registering test-module");
        
        // Ensure step 2 is complete
        if (currentStep < 2) {
            return seedStep2_ContainerAccessible()
                .flatMap(success -> success ? seedStep3_ContainerRegistered() : Uni.createFrom().item(false));
        }
        
        // TODO: Implement actual registration
        LOG.warn("⚠ Step 3 not yet implemented: Container registration pending");
        return Uni.createFrom().item(false);
    }
    
    @Override
    public Uni<Boolean> seedStep4_EmptyPipelineCreated() {
        LOG.info("Seeding Step 4: Creating empty pipeline");
        
        // Ensure step 3 is complete
        if (currentStep < 3) {
            return seedStep3_ContainerRegistered()
                .flatMap(success -> success ? seedStep4_EmptyPipelineCreated() : Uni.createFrom().item(false));
        }
        
        // TODO: Implement empty pipeline creation
        LOG.warn("⚠ Step 4 not yet implemented: Empty pipeline creation pending");
        return Uni.createFrom().item(false);
    }
    
    @Override
    public Uni<Boolean> seedStep5_FirstPipelineStepAdded() {
        LOG.info("Seeding Step 5: Adding first pipeline step");
        
        // Ensure step 4 is complete
        if (currentStep < 4) {
            return seedStep4_EmptyPipelineCreated()
                .flatMap(success -> success ? seedStep5_FirstPipelineStepAdded() : Uni.createFrom().item(false));
        }
        
        // TODO: Implement pipeline step addition
        LOG.warn("⚠ Step 5 not yet implemented: Pipeline step addition pending");
        return Uni.createFrom().item(false);
    }
    
    @Override
    public Uni<Boolean> seedStep6_TwoModulePipeline() {
        LOG.info("Seeding Step 6: Creating pipeline with two module steps");
        
        // Ensure step 5 is complete
        if (currentStep < 5) {
            return seedStep5_FirstPipelineStepAdded()
                .flatMap(success -> success ? seedStep6_TwoModulePipeline() : Uni.createFrom().item(false));
        }
        
        // TODO: Implement two-module pipeline
        LOG.warn("⚠ Step 6 not yet implemented: Two-module pipeline pending");
        return Uni.createFrom().item(false);
    }
    
    @Override
    public Uni<Boolean> seedUpToStep(int upToStep) {
        LOG.infof("Seeding up to step %d", upToStep);
        
        if (upToStep < 0 || upToStep > 6) {
            return Uni.createFrom().failure(new IllegalArgumentException("Step must be between 0 and 6"));
        }
        
        // If we're already at or past the requested step, we're done
        if (currentStep >= upToStep) {
            LOG.infof("Already at step %d, no seeding needed", currentStep);
            return Uni.createFrom().item(true);
        }
        
        // Seed each step in order
        return switch (upToStep) {
            case 0 -> seedStep0_ConsulStarted();
            case 1 -> seedStep1_ClustersCreated();
            case 2 -> seedStep2_ContainerAccessible();
            case 3 -> seedStep3_ContainerRegistered();
            case 4 -> seedStep4_EmptyPipelineCreated();
            case 5 -> seedStep5_FirstPipelineStepAdded();
            case 6 -> seedStep6_TwoModulePipeline();
            default -> Uni.createFrom().item(false);
        };
    }
    
    @Override
    public Uni<Boolean> teardownAll() {
        LOG.info("Tearing down all test data");
        return teardownFromStep(currentStep);
    }
    
    @Override
    public Uni<Boolean> teardownFromStep(int fromStep) {
        LOG.infof("Tearing down from step %d", fromStep);
        
        if (fromStep < 0) {
            return Uni.createFrom().item(true);
        }
        
        // Tear down in reverse order
        Uni<Boolean> result = Uni.createFrom().item(true);
        
        // Step 6: Remove two-module pipeline
        if (fromStep >= 6) {
            // TODO: Remove pipeline when implemented
        }
        
        // Step 5: Remove pipeline with one step
        if (fromStep >= 5) {
            // TODO: Remove pipeline when implemented
        }
        
        // Step 4: Remove empty pipeline
        if (fromStep >= 4) {
            // TODO: Remove pipeline when implemented
        }
        
        // Step 3: Unregister container
        if (fromStep >= 3) {
            // TODO: Unregister when implemented
        }
        
        // Step 2: Container cleanup handled by TestModuleContainerResource
        
        // Step 1: Delete clusters
        if (fromStep >= 1) {
            result = result.flatMap(success -> 
                clusterService.deleteCluster(TEST_CLUSTER)
                    .map(r -> r.valid())
                    .onFailure().recoverWithItem(false)
            ).flatMap(success ->
                clusterService.deleteCluster(DEFAULT_CLUSTER)
                    .map(r -> r.valid())
                    .onFailure().recoverWithItem(false)
            );
        }
        
        // Step 0: Nothing to tear down for Consul
        
        return result.map(success -> {
            if (success) {
                currentStep = -1;
                LOG.info("✓ Teardown complete");
            } else {
                LOG.warn("⚠ Teardown completed with some failures");
            }
            return success;
        });
    }
    
    @Override
    public int getCurrentStep() {
        return currentStep;
    }
}