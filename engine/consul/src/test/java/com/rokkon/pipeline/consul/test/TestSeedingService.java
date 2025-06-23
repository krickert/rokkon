package com.rokkon.pipeline.consul.test;

import io.smallrye.mutiny.Uni;

/**
 * Service for seeding test data in a methodical, reusable way.
 * Each method builds upon the previous ones, allowing tests to start
 * at any point in the setup process.
 */
public interface TestSeedingService {
    
    /**
     * Seeds up to step 0: Ensures Consul is running and accessible.
     * @return true if successful
     */
    Uni<Boolean> seedStep0_ConsulStarted();
    
    /**
     * Seeds up to step 1: Creates default and test clusters.
     * Depends on: step 0
     * @return true if successful
     */
    Uni<Boolean> seedStep1_ClustersCreated();
    
    /**
     * Seeds up to step 2: Ensures test-module container is accessible.
     * Depends on: steps 0-1
     * @return true if successful
     */
    Uni<Boolean> seedStep2_ContainerAccessible();
    
    /**
     * Seeds up to step 3: Registers the test-module with the engine.
     * Depends on: steps 0-2
     * @return true if successful
     */
    Uni<Boolean> seedStep3_ContainerRegistered();
    
    /**
     * Seeds up to step 4: Creates an empty pipeline.
     * Depends on: steps 0-3
     * @return true if successful
     */
    Uni<Boolean> seedStep4_EmptyPipelineCreated();
    
    /**
     * Seeds up to step 5: Adds first test-module step to pipeline.
     * Depends on: steps 0-4
     * @return true if successful
     */
    Uni<Boolean> seedStep5_FirstPipelineStepAdded();
    
    /**
     * Seeds up to step 6: Creates pipeline with two test-module steps.
     * Depends on: steps 0-5
     * @return true if successful
     */
    Uni<Boolean> seedStep6_TwoModulePipeline();
    
    /**
     * Seeds all steps up to the specified step number.
     * @param upToStep the step number to seed up to (0-6)
     * @return true if successful
     */
    Uni<Boolean> seedUpToStep(int upToStep);
    
    /**
     * Tears down all seeded data in reverse order.
     * @return true if successful
     */
    Uni<Boolean> teardownAll();
    
    /**
     * Tears down data from the specified step and above.
     * @param fromStep the step number to start tearing down from
     * @return true if successful
     */
    Uni<Boolean> teardownFromStep(int fromStep);
    
    /**
     * Gets the current seeded step level.
     * @return the highest successfully seeded step number, or -1 if none
     */
    int getCurrentStep();
}