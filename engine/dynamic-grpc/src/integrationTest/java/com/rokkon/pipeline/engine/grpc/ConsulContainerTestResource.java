package com.rokkon.pipeline.engine.grpc;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.consul.ConsulContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.HashMap;
import java.util.Map;

/**
 * Test resource that starts a Consul container for integration tests.
 * This is used by @QuarkusIntegrationTest to provide a real Consul instance.
 */
public class ConsulContainerTestResource implements QuarkusTestResourceLifecycleManager {
    
    private static final String CONSUL_IMAGE = "hashicorp/consul:latest";
    private ConsulContainer consul;
    
    @Override
    public Map<String, String> start() {
        consul = new ConsulContainer(DockerImageName.parse(CONSUL_IMAGE))
            .withCommand("agent -dev -client 0.0.0.0 -log-level debug");
        
        consul.start();
        
        Map<String, String> props = new HashMap<>();
        props.put("consul.host", consul.getHost());
        props.put("consul.port", String.valueOf(consul.getFirstMappedPort()));
        props.put("quarkus.consul-config.agent.host", consul.getHost());
        props.put("quarkus.consul-config.agent.port", String.valueOf(consul.getFirstMappedPort()));
        
        // Configure service discovery
        props.put("service.discovery.consul.host", consul.getHost());
        props.put("service.discovery.consul.port", String.valueOf(consul.getFirstMappedPort()));
        
        return props;
    }
    
    @Override
    public void stop() {
        if (consul != null) {
            consul.stop();
        }
    }
    
    @Override
    public void inject(TestInjector testInjector) {
        // Could inject the consul container if needed by tests
    }
}