package com.rokkon.test.containers;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

/**
 * Quarkus test resource that starts the Rokkon Engine container for testing.
 * This allows testing the engine as it would run in production (in a container)
 * with proper networking to connect to Consul and modules.
 */
public class EngineContainerResource implements QuarkusTestResourceLifecycleManager {
    
    private static final int ENGINE_HTTP_PORT = 8080;
    private static final int ENGINE_GRPC_PORT = 49000;
    private static final String DEFAULT_ENGINE_IMAGE = "nas.rokkon.com:5000/rokkon/rokkon-engine:latest";
    
    private final String imageName;
    private GenericContainer<?> engineContainer;
    private Network sharedNetwork;
    
    /**
     * Create an engine container resource with default image.
     */
    public EngineContainerResource() {
        this(DEFAULT_ENGINE_IMAGE);
    }
    
    /**
     * Create an engine container resource with custom image.
     * 
     * @param imageName the Docker image name for the engine
     */
    public EngineContainerResource(String imageName) {
        this.imageName = imageName;
    }
    
    @Override
    public Map<String, String> start() {
        // Get or create the shared network
        sharedNetwork = SharedNetworkManager.getNetwork();
        System.out.println("Engine using shared network: " + sharedNetwork.getId());
        
        // Check if Consul is available in the network
        String consulHost = System.getProperty("consul.container.host", "consul");
        String consulPort = System.getProperty("consul.container.port", "8500");
        
        engineContainer = new GenericContainer<>(DockerImageName.parse(imageName))
                .withExposedPorts(ENGINE_HTTP_PORT, ENGINE_GRPC_PORT)
                .withNetwork(sharedNetwork)
                .withNetworkAliases("rokkon-engine", "engine")
                // Configure Consul connection
                .withEnv("CONSUL_HOST", consulHost)
                .withEnv("CONSUL_PORT", consulPort)
                // Configure engine ports
                .withEnv("QUARKUS_HTTP_PORT", String.valueOf(ENGINE_HTTP_PORT))
                .withEnv("QUARKUS_HTTP_HOST", "0.0.0.0")
                .withEnv("QUARKUS_GRPC_SERVER_PORT", String.valueOf(ENGINE_GRPC_PORT))
                .withEnv("QUARKUS_GRPC_SERVER_HOST", "0.0.0.0")
                // Engine specific config
                .withEnv("ENGINE_HOST", "engine")
                // Wait for health endpoint
                .waitingFor(Wait.forHttp("/q/health").forPort(ENGINE_HTTP_PORT).forStatusCode(200))
                .withLogConsumer(outputFrame -> System.out.print("[engine] " + outputFrame.getUtf8String()));
        
        configureContainer(engineContainer);
        
        engineContainer.start();
        
        Integer externalHttpPort = engineContainer.getMappedPort(ENGINE_HTTP_PORT);
        Integer externalGrpcPort = engineContainer.getMappedPort(ENGINE_GRPC_PORT);
        
        System.out.println("\n=== Rokkon Engine Container Started ===");
        System.out.println("Container ID: " + engineContainer.getContainerId());
        System.out.println("Port Mapping:");
        System.out.println("  HTTP/Dashboard: " + ENGINE_HTTP_PORT + " -> " + externalHttpPort);
        System.out.println("  gRPC: " + ENGINE_GRPC_PORT + " -> " + externalGrpcPort);
        System.out.println("Network: " + sharedNetwork.getId());
        System.out.println("Network Aliases: rokkon-engine, engine");
        System.out.println("Dashboard URL: http://localhost:" + externalHttpPort);
        System.out.println("======================================\n");
        
        // Return configuration properties for tests
        Map<String, String> config = new java.util.HashMap<>();
        config.put("rokkon.engine.host", "localhost");
        config.put("rokkon.engine.http.port", String.valueOf(externalHttpPort));
        config.put("rokkon.engine.grpc.port", String.valueOf(externalGrpcPort));
        config.put("rokkon.engine.container.id", engineContainer.getContainerId());
        config.put("rokkon.engine.container.name", engineContainer.getContainerName());
        config.put("rokkon.engine.container.image", imageName);
        
        // For container-to-container communication
        config.put("rokkon.engine.internal.host", "engine");
        config.put("rokkon.engine.internal.http.port", String.valueOf(ENGINE_HTTP_PORT));
        config.put("rokkon.engine.internal.grpc.port", String.valueOf(ENGINE_GRPC_PORT));
        
        // Dashboard access
        config.put("rokkon.engine.dashboard.url", "http://localhost:" + externalHttpPort);
        
        return config;
    }
    
    @Override
    public void stop() {
        if (engineContainer != null && engineContainer.isRunning()) {
            engineContainer.stop();
            System.out.println("Rokkon Engine container stopped");
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
}