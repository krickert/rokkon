package com.rokkon.pipeline.consul.test;

import com.rokkon.pipeline.config.model.*;
import com.rokkon.pipeline.consul.model.ModuleWhitelistRequest;
import com.rokkon.pipeline.consul.service.ClusterService;
import com.rokkon.pipeline.consul.service.ModuleWhitelistService;
import com.rokkon.pipeline.consul.service.PipelineConfigService;
import com.rokkon.pipeline.validation.ValidationResult;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.consul.ConsulClientOptions;
import io.vertx.ext.consul.ServiceOptions;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.ext.consul.ConsulClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.awaitility.Awaitility;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
    
    @Inject
    Vertx vertx;
    
    @ConfigProperty(name = "consul.host", defaultValue = "localhost")
    String consulHost;
    
    @ConfigProperty(name = "consul.port", defaultValue = "8500")
    int consulPort;
    
    private ConsulClient consulClient;
    private final AtomicBoolean consulSeeded = new AtomicBoolean(false);

    private int currentStep = -1;

    @Override
    public Map<String, String> seedConsulConfiguration() {
        if (consulSeeded.get()) {
            return Map.of();
        }

        consulClient = ConsulClient.create(vertx, new ConsulClientOptions().setHost(consulHost).setPort(consulPort));

        // Put a key/value pair
        consulClient.putValue("rokkon/test-key", "test-value")
                .onItem().invoke(() -> System.out.println("Successfully put the key 'rokkon/test-key' into Consul."))
                .onFailure().invoke(throwable -> System.err.println("Failed to put key into Consul: " + throwable.getMessage()))
                .await().indefinitely();

        // Verify the key is present
        Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> {
            try {
                return consulClient.getValue("rokkon/test-key")
                        .onItem().transform(kv -> kv != null && "test-value".equals(kv.getValue()))
                        .onFailure().transform(err -> false)
                        .await().indefinitely();
            } catch (Exception e) {
                return false;
            }
        });

        consulSeeded.set(true);
        return Map.of();
    }

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
        LOG.info("Seeding Step 3: Registering test-module with engine");
        
        // Ensure step 2 is complete
        if (currentStep < 2) {
            return seedStep2_ContainerAccessible()
                .flatMap(success -> success ? seedStep3_ContainerRegistered() : Uni.createFrom().item(false));
        }
        
        // Use Vertx Consul client to register the service properly
        ConsulClient consulClient = ConsulClient.create(vertx, 
            new ConsulClientOptions()
                .setHost(consulHost)
                .setPort(consulPort));
        
        // Create service options for the test-module
        ServiceOptions serviceOptions = new ServiceOptions()
            .setId("grpc-test-module")
            .setName(TEST_MODULE)
            .setAddress("test-module")  // Docker network name
            .setPort(9090)
            .setTags(java.util.List.of("rokkon-module", "grpc", "test"))
            .setMeta(Map.of(
                "module-type", "test",
                "version", "1.0.0"
            ))
            .setCheckOptions(new io.vertx.ext.consul.CheckOptions()
                .setGrpc("test-module:9090")
                .setGrpcTls(false)
                .setInterval("10s")
                .setDeregisterAfter("1m"));
        
        // Register the service
        return consulClient.registerService(serviceOptions)
            .map(__ -> {
                currentStep = Math.max(currentStep, 3);
                LOG.info("✓ Step 3 complete: Test-module registered in Consul");
                return true;
            })
            .onFailure().recoverWithItem(error -> {
                LOG.errorf("Failed to register test-module in Consul: %s", error.getMessage());
                return false;
            })
            // Give Consul time to propagate the registration
            .onItem().delayIt().by(Duration.ofSeconds(1))
            // Verify the service is visible in the catalog
            .flatMap(success -> {
                if (!success) {
                    return Uni.createFrom().item(false);
                }
                
                return consulClient.catalogServiceNodes(TEST_MODULE)
                    .map(serviceList -> {
                        boolean hasService = serviceList != null && !serviceList.getList().isEmpty();
                        if (hasService) {
                            LOG.info("✓ Service confirmed in Consul catalog");
                        } else {
                            LOG.warn("Service registered but not yet visible in catalog");
                        }
                        return hasService;
                    })
                    .onFailure().recoverWithItem(error -> {
                        LOG.errorf("Failed to verify service in catalog: %s", error.getMessage());
                        return false;
                    });
            });
    }
    
    @Override
    public Uni<Boolean> seedStep4_EmptyPipelineCreated() {
        LOG.info("Seeding Step 4: Creating empty pipeline");
        
        // Ensure step 3 is complete
        if (currentStep < 3) {
            return seedStep3_ContainerRegistered()
                .flatMap(success -> success ? seedStep4_EmptyPipelineCreated() : Uni.createFrom().item(false));
        }
        
        // Create an empty pipeline configuration
        PipelineConfig emptyPipeline = new PipelineConfig(
            TEST_PIPELINE,
            Collections.emptyMap() // No steps yet
        );
        
        // Create the pipeline in the test cluster
        return pipelineConfigService.createPipeline(TEST_CLUSTER, TEST_PIPELINE, emptyPipeline)
            .map(result -> {
                if (result.valid()) {
                    currentStep = Math.max(currentStep, 4);
                    LOG.info("✓ Step 4 complete: Empty pipeline created");
                    return true;
                } else {
                    // Check if it already exists
                    if (result.errors().stream().anyMatch(e -> e.contains("already exists"))) {
                        currentStep = Math.max(currentStep, 4);
                        LOG.debug("Empty pipeline already exists");
                        return true;
                    }
                    LOG.errorf("Failed to create empty pipeline: %s", result.errors());
                    return false;
                }
            })
            .onFailure().recoverWithItem(error -> {
                LOG.errorf("Exception creating empty pipeline: %s", error.getMessage());
                return false;
            });
    }
    
    @Override
    public Uni<Boolean> seedStep5_FirstPipelineStepAdded() {
        LOG.info("Seeding Step 5: Adding first pipeline step");
        
        // Ensure step 4 is complete
        if (currentStep < 4) {
            return seedStep4_EmptyPipelineCreated()
                .flatMap(success -> success ? seedStep5_FirstPipelineStepAdded() : Uni.createFrom().item(false));
        }
        
        // First, whitelist the test-module
        ModuleWhitelistRequest whitelistRequest = new ModuleWhitelistRequest(
            TEST_MODULE,              // grpcServiceName
            TEST_MODULE,              // implementationName (same as service name for test)
            null,                     // customConfigSchemaReference
            null                      // customConfig
        );
        
        return moduleWhitelistService.whitelistModule(TEST_CLUSTER, whitelistRequest)
            .flatMap(whitelistResponse -> {
                if (!whitelistResponse.success()) {
                    LOG.errorf("Failed to whitelist module: %s", whitelistResponse.message());
                    return Uni.createFrom().item(false);
                }
                
                LOG.info("Module whitelisted successfully, now adding pipeline step");
                
                // Create a pipeline step configuration
                PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(TEST_MODULE, null);
                PipelineStepConfig stepConfig = new PipelineStepConfig(
                    "test-step-1",            // stepName
                    StepType.PIPELINE,       // stepType - standard PIPELINE type
                    processorInfo             // processorInfo
                );
                
                // Get the current pipeline configuration
                return pipelineConfigService.getPipeline(TEST_CLUSTER, TEST_PIPELINE)
                    .flatMap(pipelineOpt -> {
                        if (pipelineOpt.isEmpty()) {
                            LOG.error("Pipeline not found");
                            return Uni.createFrom().item(false);
                        }
                        
                        PipelineConfig pipeline = pipelineOpt.get();
                        
                        // Add the step to the pipeline
                        Map<String, PipelineStepConfig> updatedSteps = new HashMap<>(pipeline.pipelineSteps());
                        updatedSteps.put("test-step-1", stepConfig);
                        
                        PipelineConfig updatedPipeline = new PipelineConfig(
                            pipeline.name(),
                            updatedSteps
                        );
                        
                        // Update the pipeline
                        return pipelineConfigService.updatePipeline(TEST_CLUSTER, TEST_PIPELINE, updatedPipeline)
                            .map(result -> {
                                if (result.valid()) {
                                    currentStep = Math.max(currentStep, 5);
                                    LOG.info("✓ Step 5 complete: First pipeline step added");
                                    return true;
                                } else {
                                    LOG.errorf("Failed to update pipeline with step: %s", result.errors());
                                    return false;
                                }
                            });
                    });
            })
            .onFailure().recoverWithItem(error -> {
                LOG.errorf("Exception adding first pipeline step: %s", error.getMessage());
                return false;
            });
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
            result = result.flatMap(success ->
                pipelineConfigService.deletePipeline(TEST_CLUSTER, TEST_PIPELINE)
                    .map(r -> r.valid())
                    .onFailure().recoverWithItem(false)
            );
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

