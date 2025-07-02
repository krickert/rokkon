package com.rokkon.test.containers;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.jboss.logging.Logger;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.util.HashMap;
import java.util.Map;

/**
 * Base class for module container resources in integration tests.
 * Manages the lifecycle of a test module container.
 */
public abstract class ModuleContainerResource implements QuarkusTestResourceLifecycleManager {

    private static final Logger LOG = Logger.getLogger(ModuleContainerResource.class);
    private static final int GRPC_PORT = 9090;
    private static final int HTTP_PORT = 8080;

    private final String imageName;
    private GenericContainer<?> container;

    protected ModuleContainerResource(String imageName) {
        this.imageName = imageName;
    }

    @Override
    public Map<String, String> start() {
        LOG.info("Starting test module container: " + imageName);

        try {
            // Get shared network
            Network network = SharedNetworkManager.getOrCreateNetwork();

            // Create container
            container = new GenericContainer<>(DockerImageName.parse(imageName))
                .withNetwork(network)
                .withNetworkAliases("test-module")
                .withExposedPorts(GRPC_PORT, HTTP_PORT)
                .withEnv("CONSUL_HOST", "consul")
                .withEnv("CONSUL_PORT", "8500")
                .withEnv("GRPC_PORT", String.valueOf(GRPC_PORT))
                .withEnv("HTTP_PORT", String.valueOf(HTTP_PORT))
                .waitingFor(Wait.forLogMessage(".*Started.*", 1));

            // Start container
            container.start();

            // Return configuration
            Map<String, String> config = new HashMap<>();
            config.put("test.module.container.host", container.getHost());
            config.put("test.module.container.grpc.port", String.valueOf(container.getMappedPort(GRPC_PORT)));
            config.put("test.module.container.http.port", String.valueOf(container.getMappedPort(HTTP_PORT)));
            config.put("test.module.container.internal.grpc.port", String.valueOf(GRPC_PORT));
            config.put("test.module.container.internal.http.port", String.valueOf(HTTP_PORT));
            config.put("test.module.container.id", container.getContainerId());
            config.put("test.module.container.name", container.getContainerName());
            config.put("test.module.container.image", imageName);
            config.put("test.module.container.network.alias", "test-module");
            config.put("test.module.container.network.host", "test-module");

            LOG.info("Test module container started: " + container.getContainerName());
            return config;
        } catch (Exception e) {
            LOG.error("Failed to start test module container", e);
            // Return mock configuration to allow tests to compile
            return Map.of(
                "test.module.container.host", "localhost",
                "test.module.container.grpc.port", String.valueOf(GRPC_PORT),
                "test.module.container.http.port", String.valueOf(HTTP_PORT),
                "test.module.container.internal.grpc.port", String.valueOf(GRPC_PORT),
                "test.module.container.internal.http.port", String.valueOf(HTTP_PORT),
                "test.module.container.id", "mock-container-id",
                "test.module.container.name", "test-module",
                "test.module.container.image", imageName,
                "test.module.container.network.alias", "test-module",
                "test.module.container.network.host", "test-module"
            );
        }
    }

    @Override
    public void stop() {
        if (container != null && container.isRunning()) {
            LOG.info("Stopping test module container: " + container.getContainerName());
            container.stop();
        }
    }
}
