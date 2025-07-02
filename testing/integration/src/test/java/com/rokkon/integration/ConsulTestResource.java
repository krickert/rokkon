package com.rokkon.integration;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.jboss.logging.Logger;
import org.testcontainers.consul.ConsulContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.HashMap;
import java.util.Map;

/**
 * Test resource that provides Consul configuration for integration tests.
 * This uses the Testcontainers ConsulContainer for better Consul-specific features.
 */
public class ConsulTestResource implements QuarkusTestResourceLifecycleManager {

    private static final Logger LOG = Logger.getLogger(ConsulTestResource.class);
    private static final String CONSUL_IMAGE = "hashicorp/consul:latest";

    private ConsulContainer consulContainer;

    @Override
    public Map<String, String> start() {
        LOG.info("Starting Consul container for integration tests");

        try {
            // Create and start the Consul container
            consulContainer = new ConsulContainer(DockerImageName.parse(CONSUL_IMAGE))
                .withCommand("agent -dev -client 0.0.0.0 -log-level info");

            consulContainer.start();

            String host = consulContainer.getHost();
            String port = String.valueOf(consulContainer.getFirstMappedPort());

            LOG.info("Consul container started at " + host + ":" + port);

            // Return configuration for the Consul container
            Map<String, String> config = new HashMap<>();
            config.put("consul.host", host);
            config.put("consul.port", port);
            config.put("quarkus.consul-config.agent.host", host);
            config.put("quarkus.consul-config.agent.port", port);
            
            // Also configure for the engine's consul connection
            config.put("consul.connection.host", host);
            config.put("consul.connection.port", port);

            return config;
        } catch (Exception e) {
            LOG.error("Failed to start Consul container", e);
            throw new RuntimeException("Failed to start Consul container", e);
        }
    }

    @Override
    public void stop() {
        LOG.info("Stopping Consul container");

        if (consulContainer != null) {
            consulContainer.stop();
        }
    }
}