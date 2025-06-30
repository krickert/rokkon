package com.rokkon.integration;

import com.rokkon.test.containers.EngineContainerResource;
import com.rokkon.test.containers.SharedNetworkManager;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

/**
 * Engine container resource with a Consul sidecar agent.
 * This implements the sidecar pattern where each service has its own local Consul agent.
 */
public class SidecarEngineContainerResource extends EngineContainerResource {
    
    private final String consulServerAddress;
    private GenericContainer<?> consulSidecar;
    
    public SidecarEngineContainerResource(String imageName, String consulServerAddress) {
        super(imageName);
        this.consulServerAddress = consulServerAddress;
    }
    
    @Override
    public Map<String, String> start() {
        // Get the shared network
        Network network = SharedNetworkManager.getNetwork();
        
        // Start Consul sidecar for the engine
        consulSidecar = new GenericContainer<>(DockerImageName.parse("hashicorp/consul:latest"))
                .withNetwork(network)
                .withNetworkAliases("engine-consul")
                .withCommand("agent", "-retry-join=" + consulServerAddress, 
                           "-client=0.0.0.0", 
                           "-datacenter=dc1", 
                           "-node=engine-consul-agent",
                           "-log-level=info",
                           "-retry-interval=5s",
                           "-retry-max=30")
                .withLogConsumer(frame -> System.out.print("[ENGINE-CONSUL] " + frame.getUtf8String()));
        
        consulSidecar.start();
        System.out.println("Engine Consul sidecar started");
        
        // Start the engine with its sidecar
        Map<String, String> config = super.start();
        
        // The engine should connect to its local sidecar, not the server
        // Override the Consul host to point to the sidecar
        config.put("consul.sidecar.host", "engine-consul");
        config.put("consul.sidecar.port", "8500");
        
        return config;
    }
    
    @Override
    protected void configureContainer(GenericContainer<?> container) {
        // Configure the engine to use its local Consul sidecar
        container.withEnv("CONSUL_HOST", "engine-consul")
                 .withEnv("CONSUL_PORT", "8500");
    }
    
    @Override
    public void stop() {
        super.stop();
        if (consulSidecar != null && consulSidecar.isRunning()) {
            consulSidecar.stop();
            System.out.println("Engine Consul sidecar stopped");
        }
    }
}