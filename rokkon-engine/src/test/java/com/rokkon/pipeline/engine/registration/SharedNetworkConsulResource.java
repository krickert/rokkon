package com.rokkon.pipeline.engine.registration;

import com.rokkon.pipeline.consul.test.ConsulTestResource;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.Network;

import java.util.Map;

/**
 * Test resource that ensures Consul uses the shared network.
 * This wraps ConsulTestResource to guarantee network initialization order.
 */
public class SharedNetworkConsulResource implements QuarkusTestResourceLifecycleManager {
    
    private static volatile Network sharedNetwork;
    private final ConsulTestResource consulResource = new ConsulTestResource();
    
    @Override
    public Map<String, String> start() {
        // Ensure shared network is created before Consul starts
        if (sharedNetwork == null) {
            synchronized (SharedNetworkConsulResource.class) {
                if (sharedNetwork == null) {
                    sharedNetwork = Network.newNetwork();
                    System.out.println("SharedNetworkConsulResource: Created network " + sharedNetwork.getId());
                    // Make it available to ModuleContainerResource via system property
                    System.setProperty("test.shared.network.id", sharedNetwork.getId());
                }
            }
        }
        
        // Start Consul with the shared network
        Map<String, String> config = new java.util.HashMap<>(consulResource.start());
        
        // Add our network ID to the config
        config.put("test.network.id", sharedNetwork.getId());
        
        return config;
    }
    
    @Override
    public void stop() {
        consulResource.stop();
    }
    
    public static Network getSharedNetwork() {
        return sharedNetwork;
    }
}