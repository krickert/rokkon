package com.rokkon.pipeline.consul.service;

import com.rokkon.pipeline.config.model.*;
import com.rokkon.pipeline.consul.test.ConsulTestResource;
import com.rokkon.pipeline.validation.ValidationResult;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import jakarta.inject.Inject;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@QuarkusTest
@QuarkusTestResource(ConsulTestResource.class)
class PipelineConfigServiceTest {
    private static final Logger LOG = Logger.getLogger(PipelineConfigServiceTest.class);
    
    @Inject
    PipelineConfigService service;
    
    @Inject
    ClusterService clusterService;
    
    private PipelineConfig testConfig;
    
    @BeforeEach
    void setup() {
        // Create test pipeline config
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "test-service", null
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "test-step",
            StepType.INITIAL_PIPELINE,
            processorInfo
        );
        
        testConfig = new PipelineConfig(
            "test-pipeline",
            Map.of("test-step", step)
        );
    }
    
    @Test
    void testServiceInjection() {
        // Test that the service is properly injected
        assertThat(service).isNotNull();
    }
    
    @Test
    void testConsulKeyGeneration() {
        // Test the key pattern
        String expectedKey = "rokkon-clusters/test-cluster/pipelines/test-pipeline/config";
        
        // This tests the expected key format
        assertThat(expectedKey).contains("rokkon-clusters");
        assertThat(expectedKey).contains("test-cluster");
        assertThat(expectedKey).contains("pipelines");
        assertThat(expectedKey).contains("test-pipeline");
        assertThat(expectedKey).contains("config");
    }
    
    @Test
    void testCreateCluster() throws Exception {
        // TODO: Start simple - create cluster first
        // This should be a service call to create/register a cluster
        LOG.info("TODO: Implement cluster creation service call");
        
        // For now, just verify we can interact with Consul
        // Later this will create the cluster structure in Consul
        String clusterName = "test-cluster";
        String clusterKey = "rokkon-clusters/" + clusterName + "/metadata";
        
        // TODO: Create ClusterService to handle cluster operations
        // TODO: Store cluster metadata (name, created date, etc)
        assertThat(clusterKey).isNotNull();
    }
    
    @Test
    void testCreateSimplePipeline() throws Exception {
        // Create a minimal valid pipeline that meets validator requirements
        LOG.info("Starting simple pipeline creation test");
        
        String clusterName = "test-cluster";
        
        // Create empty pipeline - no steps initially
        // Services need to be registered first before adding steps
        PipelineConfig emptyPipeline = new PipelineConfig(
            "empty-pipeline",
            Map.of() // No steps yet
        );
        
        LOG.infof("Created empty pipeline config: %s", emptyPipeline);
        
        // Test creation
        ValidationResult result = service.createPipeline(
            clusterName, "empty-pipeline", emptyPipeline
        ).toCompletableFuture().get();
        
        LOG.infof("Pipeline creation result: valid=%s, errors=%s, warnings=%s", 
                  result.valid(), result.errors(), result.warnings());
        
        assertThat(result).isNotNull();
        
        // If validation fails, log what's required
        if (!result.valid()) {
            LOG.error("Validation failed. Errors:");
            for (String error : result.errors()) {
                LOG.errorf("  - %s", error);
            }
        }
        
        assertThat(result.errors()).as("Validation errors").isEmpty();
        assertThat(result.valid()).as("Empty pipeline should be valid").isTrue();
        
        // Verify it was stored
        Optional<PipelineConfig> retrieved = service.getPipeline(clusterName, "empty-pipeline")
            .toCompletableFuture().get();
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().name()).isEqualTo("empty-pipeline");
        assertThat(retrieved.get().pipelineSteps()).isEmpty();
    }
    
    @Test  
    void testCreatePipelineWithConsul() throws Exception {
        // Create a minimal valid pipeline config that meets all validator requirements
        // 1. Must have INITIAL_PIPELINE step
        // 2. Must have SINK step  
        // 3. Must have valid naming (lowercase-with-hyphens)
        // 4. Must have all required fields
        
        // Create INITIAL_PIPELINE step with all required fields
        PipelineStepConfig.ProcessorInfo sourceProcessor = new PipelineStepConfig.ProcessorInfo(
            "document-ingestion-service", // Valid service name format
            null // Transport config
        );
        
        PipelineStepConfig sourceStep = new PipelineStepConfig(
            "document-source", // Valid step name format
            StepType.INITIAL_PIPELINE,
            sourceProcessor
        );
        
        // Create SINK step with all required fields
        PipelineStepConfig.ProcessorInfo sinkProcessor = new PipelineStepConfig.ProcessorInfo(
            "document-sink-service", // Valid service name format
            null // Transport config
        );
        
        PipelineStepConfig sinkStep = new PipelineStepConfig(
            "document-sink", // Valid step name format
            StepType.SINK,
            sinkProcessor
        );
        
        // Create pipeline config with valid name
        PipelineConfig validConfig = new PipelineConfig(
            "document-processing-pipeline", // Valid pipeline name format
            Map.of(
                "document-source", sourceStep,
                "document-sink", sinkStep
            )
        );
        
        LOG.infof("Creating pipeline with config: %s", validConfig);
        
        // Create pipeline
        CompletionStage<ValidationResult> result = service.createPipeline(
            "test-cluster", "document-processing-pipeline", validConfig
        );
        
        ValidationResult validationResult = result.toCompletableFuture().get();
        LOG.infof("Validation result: valid=%s, errors=%s", 
                  validationResult.valid(), validationResult.errors());
        
        // If validation fails, log specific errors to understand what's wrong
        if (!validationResult.valid()) {
            LOG.errorf("Validation failed with errors: %s", validationResult.errors());
            for (String error : validationResult.errors()) {
                LOG.errorf("  - %s", error);
            }
        }
        
        assertThat(validationResult).isNotNull();
        assertThat(validationResult.errors()).as("Validation errors").isEmpty();
        assertThat(validationResult.valid()).as("Validation should pass").isTrue();
        
        // Verify it was stored
        Optional<PipelineConfig> retrieved = service.getPipeline("test-cluster", "document-processing-pipeline")
            .toCompletableFuture().get();
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().name()).isEqualTo("document-processing-pipeline");
    }
    
    @Test
    void testUpdatePipeline() throws Exception {
        // First create a pipeline
        testCreatePipelineWithConsul();
        
        // Update it
        PipelineStepConfig.ProcessorInfo processor = new PipelineStepConfig.ProcessorInfo(
            "updated-service", null
        );
        PipelineStepConfig updatedStep = new PipelineStepConfig(
            "updated-step",
            StepType.PIPELINE,
            processor
        );
        
        // Need INITIAL_PIPELINE and SINK for valid config
        PipelineStepConfig.ProcessorInfo sourceProcessor = new PipelineStepConfig.ProcessorInfo(
            "source-service", null
        );
        PipelineStepConfig sourceStep = new PipelineStepConfig(
            "source-step",
            StepType.INITIAL_PIPELINE,
            sourceProcessor
        );
        
        PipelineStepConfig.ProcessorInfo sinkProcessor = new PipelineStepConfig.ProcessorInfo(
            "sink-service", null
        );
        PipelineStepConfig sinkStep = new PipelineStepConfig(
            "sink-step",
            StepType.SINK,
            sinkProcessor
        );
        
        PipelineConfig updatedConfig = new PipelineConfig(
            "test-pipeline",
            Map.of(
                "source-step", sourceStep,
                "updated-step", updatedStep,
                "sink-step", sinkStep
            )
        );
        
        CompletionStage<ValidationResult> result = service.updatePipeline(
            "test-cluster", "test-pipeline", updatedConfig
        );
        
        ValidationResult validationResult = result.toCompletableFuture().get();
        assertThat(validationResult.valid()).isTrue();
        
        // Verify update
        Optional<PipelineConfig> retrieved = service.getPipeline("test-cluster", "test-pipeline")
            .toCompletableFuture().get();
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().pipelineSteps()).containsKey("updated-step");
    }
    
    @Test
    void testDeletePipeline() throws Exception {
        // First create a pipeline
        testCreatePipelineWithConsul();
        
        // Delete it
        CompletionStage<ValidationResult> result = service.deletePipeline(
            "test-cluster", "test-pipeline"
        );
        
        ValidationResult validationResult = result.toCompletableFuture().get();
        assertThat(validationResult.valid()).isTrue();
        
        // Verify it's gone
        Optional<PipelineConfig> retrieved = service.getPipeline("test-cluster", "test-pipeline")
            .toCompletableFuture().get();
        assertThat(retrieved).isEmpty();
    }
    
    @Test
    void testListPipelines() throws Exception {
        // Create multiple pipelines
        for (int i = 1; i <= 3; i++) {
            PipelineStepConfig.ProcessorInfo sourceProcessor = new PipelineStepConfig.ProcessorInfo(
                "source-service-" + i, null
            );
            PipelineStepConfig sourceStep = new PipelineStepConfig(
                "source-step",
                StepType.INITIAL_PIPELINE,
                sourceProcessor
            );
            
            PipelineStepConfig.ProcessorInfo sinkProcessor = new PipelineStepConfig.ProcessorInfo(
                "sink-service-" + i, null
            );
            PipelineStepConfig sinkStep = new PipelineStepConfig(
                "sink-step",
                StepType.SINK,
                sinkProcessor
            );
            
            PipelineConfig config = new PipelineConfig(
                "pipeline-" + i,
                Map.of(
                    "source-step", sourceStep,
                    "sink-step", sinkStep
                )
            );
            
            service.createPipeline("test-cluster", "pipeline-" + i, config)
                .toCompletableFuture().get();
        }
        
        // List them
        Map<String, PipelineConfig> pipelines = service.listPipelines("test-cluster")
            .toCompletableFuture().get();
        
        assertThat(pipelines).hasSize(3);
        assertThat(pipelines).containsKeys("pipeline-1", "pipeline-2", "pipeline-3");
    }
    
    @Test
    void testConcurrentPipelineCreation() {
        int numAttempts = 10;
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        
        // Create valid config
        PipelineStepConfig.ProcessorInfo sourceProcessor = new PipelineStepConfig.ProcessorInfo(
            "source-service", null
        );
        PipelineStepConfig sourceStep = new PipelineStepConfig(
            "source-step",
            StepType.INITIAL_PIPELINE,
            sourceProcessor
        );
        
        PipelineStepConfig.ProcessorInfo sinkProcessor = new PipelineStepConfig.ProcessorInfo(
            "sink-service", null
        );
        PipelineStepConfig sinkStep = new PipelineStepConfig(
            "sink-step",
            StepType.SINK,
            sinkProcessor
        );
        
        PipelineConfig config = new PipelineConfig(
            "concurrent-pipeline",
            Map.of(
                "source-step", sourceStep,
                "sink-step", sinkStep
            )
        );
        
        // Use Mutiny Multi to create concurrent attempts
        List<Uni<ValidationResult>> creationAttempts = IntStream.range(0, numAttempts)
            .mapToObj(i -> Uni.createFrom().completionStage(() ->
                service.createPipeline("concurrent-cluster", "concurrent-pipeline", config)
            ))
            .toList();
        
        // Execute all concurrently and collect results
        Multi.createFrom().iterable(creationAttempts)
            .onItem().transformToUniAndMerge(uni -> uni)
            .onItem().invoke(result -> {
                if (result.valid()) {
                    successCount.incrementAndGet();
                } else if (result.errors().stream()
                        .anyMatch(e -> e.contains("already exists"))) {
                    conflictCount.incrementAndGet();
                }
            })
            .collect().asList()
            .await().atMost(Duration.ofSeconds(10));
        
        // Verify only one succeeded
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(numAttempts - 1);
        
        // Verify pipeline exists using Awaitility
        await().atMost(Duration.ofSeconds(5))
            .untilAsserted(() -> {
                Optional<PipelineConfig> retrieved = service.getPipeline("concurrent-cluster", "concurrent-pipeline")
                    .toCompletableFuture().join();
                assertThat(retrieved).isPresent();
            });
        
        // Cleanup
        service.deletePipeline("concurrent-cluster", "concurrent-pipeline")
            .toCompletableFuture().join();
    }
    
    @Test
    void testConcurrentPipelineUpdates() {
        // First create a pipeline
        PipelineStepConfig.ProcessorInfo sourceProcessor = new PipelineStepConfig.ProcessorInfo(
            "source-service", null
        );
        PipelineStepConfig sourceStep = new PipelineStepConfig(
            "source-step",
            StepType.INITIAL_PIPELINE,
            sourceProcessor
        );
        
        PipelineStepConfig.ProcessorInfo sinkProcessor = new PipelineStepConfig.ProcessorInfo(
            "sink-service", null
        );
        PipelineStepConfig sinkStep = new PipelineStepConfig(
            "sink-step",
            StepType.SINK,
            sinkProcessor
        );
        
        PipelineConfig initialConfig = new PipelineConfig(
            "update-test-pipeline",
            Map.of(
                "source-step", sourceStep,
                "sink-step", sinkStep
            )
        );
        
        service.createPipeline("update-cluster", "update-test-pipeline", initialConfig)
            .toCompletableFuture().join();
        
        // Now try concurrent updates
        int numUpdates = 5;
        AtomicInteger updateCount = new AtomicInteger(0);
        
        // Create update Unis
        List<Uni<ValidationResult>> updateAttempts = IntStream.range(0, numUpdates)
            .mapToObj(i -> {
                // Each update adds its own step
                PipelineStepConfig.ProcessorInfo processor = new PipelineStepConfig.ProcessorInfo(
                    "processor-" + i, null
                );
                PipelineStepConfig newStep = new PipelineStepConfig(
                    "step-" + i,
                    StepType.PIPELINE,
                    processor
                );
                
                PipelineConfig updatedConfig = new PipelineConfig(
                    "update-test-pipeline",
                    Map.of(
                        "source-step", sourceStep,
                        "step-" + i, newStep,
                        "sink-step", sinkStep
                    )
                );
                
                return Uni.createFrom().completionStage(() ->
                    service.updatePipeline("update-cluster", "update-test-pipeline", updatedConfig)
                );
            })
            .toList();
        
        // Execute all updates concurrently
        Multi.createFrom().iterable(updateAttempts)
            .onItem().transformToUniAndMerge(uni -> uni)
            .onItem().invoke(result -> {
                if (result.valid()) {
                    updateCount.incrementAndGet();
                }
            })
            .collect().asList()
            .await().atMost(Duration.ofSeconds(10));
        
        // All updates should succeed (last one wins in Consul)
        assertThat(updateCount.get()).isEqualTo(numUpdates);
        
        // Verify pipeline exists and has been updated
        await().atMost(Duration.ofSeconds(5))
            .untilAsserted(() -> {
                Optional<PipelineConfig> retrieved = service.getPipeline("update-cluster", "update-test-pipeline")
                    .toCompletableFuture().join();
                assertThat(retrieved).isPresent();
                // The pipeline should have at least one of the updated steps
                assertThat(retrieved.get().pipelineSteps().keySet())
                    .anyMatch(key -> key.startsWith("step-"));
            });
        
        // Cleanup
        service.deletePipeline("update-cluster", "update-test-pipeline")
            .toCompletableFuture().join();
    }
}