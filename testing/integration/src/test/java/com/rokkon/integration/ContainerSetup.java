package com.rokkon.integration;

import com.rokkon.test.containers.EngineContainerResource;
import com.rokkon.test.containers.SharedNetworkManager;
import com.rokkon.test.containers.TestModuleContainerResource;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.consul.ConsulContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class ContainerSetup implements QuarkusTestResourceLifecycleManager {

    private static final Logger LOGGER = Logger.getLogger(ContainerSetup.class.getName());

    private Network network;
    private ConsulContainer consulContainer;
    private EngineContainerResource engineContainerResource;
    private TestModuleContainerResource testModuleContainerResource;

    @Override
    public Map<String, String> start() {
        // Use SharedNetworkManager to ensure all containers share the same network
        network = SharedNetworkManager.getNetwork();
        LOGGER.info("Using shared network: " + network.getId());

        // Start Consul container
        consulContainer = new ConsulContainer(DockerImageName.parse("hashicorp/consul:latest"))
                .withNetwork(network)
                .withNetworkAliases("consul")
                .withLogConsumer(frame -> LOGGER.info("[CONSUL] " + frame.getUtf8String()));
        consulContainer.start();
        
        // Set system properties for the engine and module containers to find Consul
        System.setProperty("consul.container.host", "consul");
        System.setProperty("consul.container.port", "8500");

        // Start Engine using existing EngineContainerResource
        engineContainerResource = new EngineContainerResource("rokkon/rokkon-engine:latest");
        Map<String, String> engineConfig = engineContainerResource.start();

        // Start Test Module using existing TestModuleContainerResource
        testModuleContainerResource = new TestModuleContainerResource();
        Map<String, String> moduleConfig = testModuleContainerResource.start();

        Map<String, String> config = new HashMap<>();
        config.putAll(engineConfig);
        config.putAll(moduleConfig);
        
        // Add Consul configuration
        config.put("consul.host", "localhost");
        config.put("consul.port", String.valueOf(consulContainer.getMappedPort(8500)));
        config.put("consul.url", consulContainer.getHost());

        return config;
    }

    @Override
    public void stop() {
        if (testModuleContainerResource != null) {
            testModuleContainerResource.stop();
        }
        if (engineContainerResource != null) {
            engineContainerResource.stop();
        }
        if (consulContainer != null) {
            consulContainer.stop();
        }
        if (network != null) {
            network.close();
        }
    }
}
