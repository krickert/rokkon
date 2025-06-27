package com.rokkon.pipeline.consul.test;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.consul.ConsulContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

/**
 * Manages the lifecycle of a Consul container for tests.
 * This ensures Consul is started before Quarkus attempts to read configuration from it.
 */
public class ConsulTestResource implements QuarkusTestResourceLifecycleManager {

    private static final DockerImageName CONSUL_IMAGE = DockerImageName.parse("hashicorp/consul:1.21.2");
    private ConsulContainer consulContainer;

    @Override
    public Map<String, String> start() {
        consulContainer = new ConsulContainer(CONSUL_IMAGE)
                .withConsulCommand("agent -dev -client=0.0.0.0");
        
        consulContainer.start();
        
        // Return configuration that will be injected into the test environment
        return Map.of(
            "consul.host", consulContainer.getHost(),
            "consul.port", String.valueOf(consulContainer.getMappedPort(8500)),
            "quarkus.consul-config.agent.host", consulContainer.getHost(),
            "quarkus.consul-config.agent.port", String.valueOf(consulContainer.getMappedPort(8500))
        );
    }

    @Override
    public void stop() {
        if (consulContainer != null && consulContainer.isRunning()) {
            consulContainer.stop();
        }
    }
}