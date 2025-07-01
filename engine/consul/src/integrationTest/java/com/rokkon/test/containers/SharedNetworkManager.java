package com.rokkon.test.containers;

import org.testcontainers.containers.Network;

/**
 * Manages shared Docker networks for integration tests.
 * This is a stub implementation to fix compilation.
 * TODO: Move to testing:util main sources or create proper implementation.
 */
public class SharedNetworkManager {
    
    private static Network sharedNetwork;
    
    public static synchronized Network getOrCreateNetwork() {
        if (sharedNetwork == null) {
            sharedNetwork = Network.newNetwork();
        }
        return sharedNetwork;
    }
}