package com.rokkon.pipeline.consul;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Collections;
import java.util.Map;

public class ConsulTestResource implements QuarkusTestResourceLifecycleManager {

    private GenericContainer<?> consulContainer;

    @Override
    public Map<String, String> start() {
        consulContainer = new GenericContainer<>(DockerImageName.parse("consul:latest"))
                .withExposedPorts(8500);
        consulContainer.start();

        System.setProperty("quarkus.consul-config.host", consulContainer.getHost());
        System.setProperty("quarkus.consul-config.port", String.valueOf(consulContainer.getMappedPort(8500)));

        return Collections.emptyMap();
    }

    @Override
    public void stop() {
        if (consulContainer != null) {
            consulContainer.stop();
        }
    }
}
