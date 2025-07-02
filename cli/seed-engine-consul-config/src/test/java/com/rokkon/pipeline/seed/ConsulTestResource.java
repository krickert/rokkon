package com.rokkon.pipeline.seed;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.consul.ConsulContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

/**
 * Testcontainers resource that starts Consul for integration tests.
 */
public class ConsulTestResource implements QuarkusTestResourceLifecycleManager {
    
    private static final String CONSUL_IMAGE = "hashicorp/consul:1.18";
    private ConsulContainer consul;
    
    @Override
    public Map<String, String> start() {
        consul = new ConsulContainer(DockerImageName.parse(CONSUL_IMAGE));
        consul.start();
        
        return Map.of(
            "consul.host", consul.getHost(),
            "consul.port", String.valueOf(consul.getMappedPort(8500)),
            "%test.consul.host", consul.getHost(),
            "%test.consul.port", String.valueOf(consul.getMappedPort(8500))
        );
    }
    
    @Override
    public void stop() {
        if (consul != null) {
            consul.stop();
        }
    }
}