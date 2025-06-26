package com.rokkon.pipeline.engine.test;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

public class ConsulTestResource implements QuarkusTestResourceLifecycleManager {

    private GenericContainer<?> consulContainer;

    @Override
    public Map<String, String> start() {
        consulContainer = new GenericContainer<>(DockerImageName.parse("hashicorp/consul:1.21.2"))
                .withExposedPorts(8500);
        consulContainer.start();

        // Provide the Consul agent host and port to Quarkus
        return Map.of(
                "quarkus.consul-config.agent.host", consulContainer.getHost(),
                "quarkus.consul-config.agent.port", consulContainer.getMappedPort(8500).toString()
        );
    }

    @Override
    public void stop() {
        if (consulContainer != null) {
            consulContainer.stop();
        }
    }
}
