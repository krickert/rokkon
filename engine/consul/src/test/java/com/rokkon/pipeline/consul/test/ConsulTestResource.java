package com.rokkon.pipeline.consul.test;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.jboss.logging.Logger;
import org.testcontainers.consul.ConsulContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;

import java.util.HashMap;
import java.util.Map;

/**
 * Test resource that provides Consul configuration for tests.
 * 
 * This implementation uses TestContainers to create a new Consul container
 * for testing, ensuring that tests don't interfere with any existing Consul instances.
 * It also seeds the Consul container with initial configuration data.
 */
public class ConsulTestResource implements QuarkusTestResourceLifecycleManager {

    private static final Logger LOG = Logger.getLogger(ConsulTestResource.class);
    private static final String CONSUL_IMAGE = "hashicorp/consul:1.18";

    private ConsulContainer consulContainer;
    private Network network;

    @Override
    public Map<String, String> start() {
        LOG.info("Starting Consul container for tests");

        try {
            // Create a network for the container
            network = Network.newNetwork();

            // Create and start the Consul container
            consulContainer = new ConsulContainer(DockerImageName.parse(CONSUL_IMAGE))
                .withNetwork(network)
                .withNetworkAliases("consul");

            // Seed Consul with initial configuration data
            consulContainer
                // Engine settings
                .withConsulCommand("kv put config/application/rokkon.engine.name rokkon-engine")
                .withConsulCommand("kv put config/application/rokkon.engine.grpc-port 8081")
                .withConsulCommand("kv put config/application/rokkon.engine.rest-port 8080")
                .withConsulCommand("kv put config/application/rokkon.engine.debug false")

                // Consul cleanup settings
                .withConsulCommand("kv put config/application/rokkon.consul.cleanup.enabled true")
                .withConsulCommand("kv put config/application/rokkon.consul.cleanup.interval PT5M")
                .withConsulCommand("kv put config/application/rokkon.consul.cleanup.zombie-threshold 2m")
                .withConsulCommand("kv put config/application/rokkon.consul.cleanup.cleanup-stale-whitelist true")

                // Consul health check settings
                .withConsulCommand("kv put config/application/rokkon.consul.health.check-interval 10s")
                .withConsulCommand("kv put config/application/rokkon.consul.health.deregister-after 1m")
                .withConsulCommand("kv put config/application/rokkon.consul.health.timeout 5s")

                // Module management settings
                .withConsulCommand("kv put config/application/rokkon.modules.auto-discover false")
                .withConsulCommand("kv put config/application/rokkon.modules.service-prefix module-")
                .withConsulCommand("kv put config/application/rokkon.modules.require-whitelist true")
                .withConsulCommand("kv put config/application/rokkon.modules.connection-timeout PT30S")
                .withConsulCommand("kv put config/application/rokkon.modules.max-instances-per-module 10")

                // Default cluster configuration
                .withConsulCommand("kv put config/application/rokkon.default-cluster.name default")
                .withConsulCommand("kv put config/application/rokkon.default-cluster.auto-create true")
                .withConsulCommand("kv put config/application/rokkon.default-cluster.description \"Default cluster for Rokkon pipelines\"");

            consulContainer.start();

            String host = consulContainer.getHost();
            String port = String.valueOf(consulContainer.getMappedPort(8500));

            LOG.info("Consul container started at " + host + ":" + port);
            LOG.info("Consul container has been seeded with initial configuration data");

            // Return configuration for the Consul container
            Map<String, String> config = new HashMap<>();
            config.put("consul.host", host);
            config.put("consul.port", port);
            config.put("quarkus.consul-config.agent.host", host);
            config.put("quarkus.consul-config.agent.port", port);

            // Set system properties for BasicConsulConnectionIT
            System.setProperty("consul.host", host);
            System.setProperty("consul.port", port);

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

        if (network != null) {
            try {
                network.close();
            } catch (Exception e) {
                LOG.warn("Failed to close network", e);
            }
        }

        // Clear system properties
        System.clearProperty("consul.host");
        System.clearProperty("consul.port");
    }
}
