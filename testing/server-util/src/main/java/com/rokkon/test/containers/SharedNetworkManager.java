package com.rokkon.test.containers;

import org.testcontainers.containers.Network;

/**
 * Manages a shared Docker network for test containers to enable inter-container communication.
 * This is a singleton to ensure all containers use the same network within a test run.
 */
public class SharedNetworkManager {
    
    private static volatile Network sharedNetwork;
    private static final Object lock = new Object();
    
    /**
     * Get or create the shared network for test containers.
     * This method is thread-safe and ensures only one network is created.
     * 
     * @return the shared network
     */
    public static Network getNetwork() {
        if (sharedNetwork == null) {
            synchronized (lock) {
                if (sharedNetwork == null) {
                    sharedNetwork = Network.newNetwork();
                    System.out.println("Created shared test network: " + sharedNetwork.getId());
                }
            }
        }
        return sharedNetwork;
    }
    
    /**
     * Set the shared network. This allows external resources (like ConsulTestResource)
     * to provide their network for sharing.
     * 
     * @param network the network to use as shared
     */
    public static void setSharedNetwork(Network network) {
        synchronized (lock) {
            if (sharedNetwork != null && sharedNetwork != network) {
                System.out.println("Warning: Replacing existing shared network");
            }
            sharedNetwork = network;
            if (network != null) {
                System.out.println("Set shared test network: " + network.getId());
            }
        }
    }
    
    /**
     * Get the current shared network without creating one.
     * 
     * @return the shared network, or null if not set
     */
    public static Network getSharedNetwork() {
        return sharedNetwork;
    }
    
    /**
     * Clean up the shared network. This should be called when all tests are complete.
     * In practice, Testcontainers' Ryuk will clean this up automatically.
     */
    public static void cleanup() {
        synchronized (lock) {
            if (sharedNetwork != null) {
                try {
                    sharedNetwork.close();
                    System.out.println("Closed shared test network");
                } catch (Exception e) {
                    System.err.println("Error closing shared network: " + e.getMessage());
                }
                sharedNetwork = null;
            }
        }
    }
}