package com.rokkon.test.containers;

/**
 * Test module container resource for integration testing.
 * This starts the test-module Docker container with the standard ports.
 */
public class TestModuleContainerResource extends ModuleContainerResource {
    
    /**
     * Create a test module container resource with the current snapshot version.
     */
    public TestModuleContainerResource() {
        super("rokkon/test-module:1.0.0-SNAPSHOT");
    }
}