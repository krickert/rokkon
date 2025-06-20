package com.rokkon.pipeline.engine.test;

import com.rokkon.pipeline.consul.test.ConsulTestResource;
import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Test profile for the DummyProcessorTest.
 * Configures the environment with a random cluster name and Consul test container.
 */
public class DummyProcessorTestProfile implements QuarkusTestProfile {
    
    private static final String CLUSTER_NAME = "test-cluster-" + UUID.randomUUID();
    
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
            "engine.cluster.name", CLUSTER_NAME,
            "quarkus.grpc.server.port", "9000",
            "quarkus.grpc.server.host", "0.0.0.0",
            "quarkus.grpc.server.enable-reflection-service", "true",
            "quarkus.grpc.clients.pipeStepProcessor.host", "localhost",
            "quarkus.grpc.clients.pipeStepProcessor.port", "9000",
            "quarkus.grpc.clients.pipeStepProcessor.use-quarkus-grpc-client", "false"
        );
    }
    
    @Override
    public List<TestResourceEntry> testResources() {
        return List.of(
            new TestResourceEntry(ConsulTestResource.class)
        );
    }
    
    /**
     * Get the cluster name used in this profile.
     * This allows tests to reference the same cluster name.
     */
    public static String getClusterName() {
        return CLUSTER_NAME;
    }
}