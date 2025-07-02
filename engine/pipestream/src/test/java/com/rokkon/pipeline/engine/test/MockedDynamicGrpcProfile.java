package com.rokkon.pipeline.engine.test;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Test profile that mocks the DynamicGrpcClientFactory to avoid classloading issues.
 */
public class MockedDynamicGrpcProfile implements QuarkusTestProfile {
    
    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> config = new HashMap<>();
        
        // Disable Consul
        config.put("quarkus.consul-config.enabled", "false");
        config.put("quarkus.consul.enabled", "false");
        
        // Provide required configuration
        config.put("rokkon.cluster.name", "test-cluster");
        config.put("rokkon.engine.name", "test-engine");
        
        // Disable features that might cause issues
        config.put("quarkus.arc.exclude-types", "com.rokkon.pipeline.engine.grpc.DynamicGrpcClientFactory");
        
        return config;
    }
    
    @Override
    public Set<Class<?>> getEnabledAlternatives() {
        // We could provide an alternative implementation here if needed
        return Set.of();
    }
    
    @Override
    public String getConfigProfile() {
        return "test-mocked-grpc";
    }
}