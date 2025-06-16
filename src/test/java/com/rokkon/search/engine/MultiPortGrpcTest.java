package com.rokkon.search.engine;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/**
 * Test to verify if Quarkus can handle multiple gRPC clients 
 * connecting to the same service interface on different ports.
 * 
 * Scenario: Engine acts as a proxy/gateway to multiple modules:
 * - Chunker module on localhost:50051 
 * - Embedder module on localhost:50052
 * Both implement PipeStepProcessor interface
 */
@QuarkusTest
class MultiPortGrpcTest {
    
    @Test
    void testMultipleClientConfiguration() {
        // This test will verify the configuration works
        // We'll configure two gRPC clients pointing to same interface, different ports
        System.out.println("Testing multiple gRPC client configuration...");
    }
}