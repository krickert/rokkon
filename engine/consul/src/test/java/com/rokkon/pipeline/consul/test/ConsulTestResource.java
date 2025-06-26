package com.rokkon.pipeline.consul.test;

import io.quarkus.test.common.DevServicesContext;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.consul.ConsulContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;
import java.util.Optional;

/**
 * Testcontainers resource that starts Consul for integration tests.
 * Implements DevServicesContext.ContextAware to participate in Quarkus network sharing.
 * Uses a singleton pattern to share a single Consul container across all tests.
 */
public class ConsulTestResource implements QuarkusTestResourceLifecycleManager, DevServicesContext.ContextAware {
    
    private static final String CONSUL_IMAGE = "hashicorp/consul:1.18";
    private ConsulContainer consul;
    private Optional<String> containerNetworkId = Optional.empty();

    @Override
    public Map<String, String> start() {
        // Each test suite will get its own Consul container
        consul = new ConsulContainer(DockerImageName.parse(CONSUL_IMAGE));

        // If Quarkus provides a shared network, use it.
        containerNetworkId.ifPresent(consul::withNetworkMode);

        consul.start();

        return Map.of(
            "consul.host", consul.getHost(),
            "consul.port", String.valueOf(consul.getMappedPort(8500))
        );
    }

    @Override
    public void stop() {
        if (consul != null) {
            consul.stop();
        }
    }

    @Override
    public void setIntegrationTestContext(DevServicesContext context) {
        this.containerNetworkId = context.containerNetworkId();
    }
}