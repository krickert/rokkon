package com.rokkon.test.containers;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;

import java.util.Map;

/**
 * Generic Quarkus test resource that starts a module container for testing.
 * This allows testing modules as they would run in production (in a container)
 * while still being part of the Quarkus test lifecycle.
 * 
 * This class can be extended to create specific module container resources
 * or used directly with configuration.
 */
public class ModuleContainerResource implements QuarkusTestResourceLifecycleManager {
    
    protected static final int DEFAULT_GRPC_PORT = 9090;
    protected static final int DEFAULT_HTTP_PORT = 8080;
    
    private final String imageName;
    private final int internalGrpcPort;
    private final int internalHttpPort;
    
    private GenericContainer<?> moduleContainer;
    private Network sharedNetwork;
    
    /**
     * Create a module container resource with default ports.
     * 
     * @param imageName the Docker image name (e.g., "rokkon/test-module:1.0.0-SNAPSHOT")
     */
    public ModuleContainerResource(String imageName) {
        this(imageName, DEFAULT_GRPC_PORT, DEFAULT_HTTP_PORT);
    }
    
    /**
     * Create a module container resource with custom ports.
     * 
     * @param imageName the Docker image name
     * @param internalGrpcPort the internal gRPC port
     * @param internalHttpPort the internal HTTP port
     */
    public ModuleContainerResource(String imageName, int internalGrpcPort, int internalHttpPort) {
        this.imageName = imageName;
        this.internalGrpcPort = internalGrpcPort;
        this.internalHttpPort = internalHttpPort;
    }
    
    @Override
    public Map<String, String> start() {
        // Try to get shared network from system property (set by SharedNetworkConsulResource)
        String networkId = System.getProperty("test.shared.network.id");
        if (networkId != null) {
            // SharedNetworkConsulResource has created a network, try to use it
            try {
                // We can't easily get the Network object, so we'll use SharedNetworkManager as a fallback
                sharedNetwork = SharedNetworkManager.getSharedNetwork();
                if (sharedNetwork == null) {
                    System.out.println("Module creating new network (SharedNetworkManager had none)");
                    sharedNetwork = SharedNetworkManager.getNetwork();
                } else {
                    System.out.println("Module using existing shared network: " + sharedNetwork.getId());
                }
            } catch (Exception e) {
                System.out.println("Could not get shared network: " + e.getMessage());
                sharedNetwork = Network.newNetwork();
            }
        } else {
            // No shared network exists yet, create one
            sharedNetwork = SharedNetworkManager.getNetwork();
            System.out.println("Module created shared network: " + sharedNetwork.getId());
        }
        
        moduleContainer = new GenericContainer<>(imageName)
                .withExposedPorts(internalGrpcPort, internalHttpPort)
                // Add environment variables to ensure consistent ports
                .withEnv("QUARKUS_HTTP_PORT", String.valueOf(internalHttpPort))
                .withEnv("QUARKUS_GRPC_SERVER_PORT", String.valueOf(internalGrpcPort))
                .waitingFor(Wait.forHttp("/q/health").forPort(internalHttpPort).forStatusCode(200))
                .withLogConsumer(outputFrame -> System.out.print("[container] " + outputFrame.getUtf8String()));
        
        // Join the shared network if available
        if (sharedNetwork != null) {
            moduleContainer.withNetwork(sharedNetwork);
            // Extract module name for network alias
            String moduleName = extractModuleName(imageName).toLowerCase().replace(" ", "-");
            moduleContainer.withNetworkAliases(moduleName);
        }
        
        configureContainer(moduleContainer);
        
        moduleContainer.start();
        
        Integer externalGrpcPort = moduleContainer.getMappedPort(internalGrpcPort);
        Integer externalHttpPort = moduleContainer.getMappedPort(internalHttpPort);
        
        String moduleName = extractModuleName(imageName);
        String networkAlias = moduleName.toLowerCase().replace(" ", "-");
        
        System.out.println("\n=== " + moduleName + " Container Started ===");
        System.out.println("Container ID: " + moduleContainer.getContainerId());
        System.out.println("Port Mapping:");
        System.out.println("  gRPC: " + internalGrpcPort + " -> " + externalGrpcPort);
        System.out.println("  HTTP: " + internalHttpPort + " -> " + externalHttpPort);
        if (sharedNetwork != null) {
            System.out.println("Network: " + sharedNetwork.getId());
            System.out.println("Network Alias: " + networkAlias);
        }
        System.out.println("====================================\n");
        
        // Return configuration properties for the test
        Map<String, String> config = new java.util.HashMap<>();
        config.put("test.module.container.host", "localhost");
        config.put("test.module.container.grpc.port", String.valueOf(externalGrpcPort));
        config.put("test.module.container.http.port", String.valueOf(externalHttpPort));
        config.put("test.module.container.internal.grpc.port", String.valueOf(internalGrpcPort));
        config.put("test.module.container.internal.http.port", String.valueOf(internalHttpPort));
        config.put("test.module.container.id", moduleContainer.getContainerId());
        config.put("test.module.container.name", moduleContainer.getContainerName());
        config.put("test.module.container.image", imageName);
        
        // Add network-specific configuration if using shared network
        if (sharedNetwork != null) {
            config.put("test.module.container.network.alias", networkAlias);
            config.put("test.module.container.network.host", networkAlias);  // For container-to-container communication
        }
        
        return config;
    }
    
    @Override
    public void stop() {
        if (moduleContainer != null && moduleContainer.isRunning()) {
            moduleContainer.stop();
            System.out.println(extractModuleName(imageName) + " container stopped");
        }
    }
    
    /**
     * Override this method to add additional configuration to the container.
     * 
     * @param container the container to configure
     */
    protected void configureContainer(GenericContainer<?> container) {
        // Subclasses can override to add additional configuration
    }
    
    /**
     * Extract a readable module name from the Docker image name.
     * 
     * @param imageName the full Docker image name
     * @return a readable module name
     */
    private String extractModuleName(String imageName) {
        // Extract module name from image name
        // e.g., "rokkon/test-module:1.0.0-SNAPSHOT" -> "Test Module"
        String name = imageName;
        if (name.contains("/")) {
            name = name.substring(name.lastIndexOf("/") + 1);
        }
        if (name.contains(":")) {
            name = name.substring(0, name.indexOf(":"));
        }
        // Convert kebab-case to Title Case
        name = name.replace("-", " ");
        String[] words = name.split(" ");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)))
                      .append(word.substring(1).toLowerCase())
                      .append(" ");
            }
        }
        return result.toString().trim();
    }
}