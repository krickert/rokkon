package com.rokkon.integration;

import com.rokkon.test.containers.ModuleContainerResource;
import com.rokkon.test.containers.SharedNetworkManager;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

/**
 * Module container resource with a Consul sidecar agent.
 * This implements the sidecar pattern where each service has its own local Consul agent.
 */
public class SidecarModuleContainerResource extends ModuleContainerResource {
    
    private final String consulServerAddress;
    private final String moduleName;
    private GenericContainer<?> consulSidecar;
    
    public SidecarModuleContainerResource(String imageName, String moduleName, String consulServerAddress) {
        super(imageName);
        this.moduleName = moduleName;
        this.consulServerAddress = consulServerAddress;
    }
    
    @Override
    public Map<String, String> start() {
        // Get the shared network
        Network network = SharedNetworkManager.getNetwork();
        
        // Start Consul sidecar for the module
        String sidecarAlias = moduleName + "-consul";
        consulSidecar = new GenericContainer<>(DockerImageName.parse("hashicorp/consul:latest"))
                .withNetwork(network)
                .withNetworkAliases(sidecarAlias)
                .withCommand("agent", "-retry-join=" + consulServerAddress, 
                           "-client=0.0.0.0", 
                           "-datacenter=dc1", 
                           "-node=" + moduleName + "-consul-agent",
                           "-log-level=info",
                           "-retry-interval=5s",
                           "-retry-max=30")
                .withLogConsumer(frame -> System.out.print("[" + moduleName.toUpperCase() + "-CONSUL] " + frame.getUtf8String()));
        
        consulSidecar.start();
        System.out.println(moduleName + " Consul sidecar started");
        
        // Start the module with its sidecar
        Map<String, String> config = super.start();
        
        // The module should connect to its local sidecar, not the server
        config.put(moduleName + ".consul.sidecar.host", sidecarAlias);
        config.put(moduleName + ".consul.sidecar.port", "8500");
        
        return config;
    }
    
    @Override
    protected void configureContainer(GenericContainer<?> container) {
        // Configure the module to use its local Consul sidecar
        String sidecarAlias = moduleName + "-consul";
        container.withEnv("CONSUL_HOST", sidecarAlias)
                 .withEnv("CONSUL_PORT", "8500")
                 // Configure engine connection for the module
                 .withEnv("ENGINE_HOST", "engine")  // Use the engine's network alias
                 .withEnv("ENGINE_PORT", "49000")  // Engine's gRPC port
                 .withEnv("ROKKON_ENGINE_ADDRESS", "engine")  // Alternative env var
                 .withEnv("ROKKON_CONSUL_ADDRESS", sidecarAlias);  // Alternative env var
    }
    
    @Override
    public void stop() {
        super.stop();
        if (consulSidecar != null && consulSidecar.isRunning()) {
            consulSidecar.stop();
            System.out.println(moduleName + " Consul sidecar stopped");
        }
    }
}