package com.rokkon.integration;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Simple test to verify engine's gRPC connectivity.
 */
@QuarkusTest
@QuarkusTestResource(ConsulSidecarSetup.class)
public class EngineGrpcConnectivityTest {
    
    @Inject
    @ConfigProperty(name = "rokkon.engine.grpc.port")
    String engineGrpcPort;
    
    @Inject
    @ConfigProperty(name = "rokkon.engine.host")
    String engineHost;
    
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    public void testEngineGrpcPortIsAccessible() {
        System.out.println("Testing engine gRPC connectivity...");
        System.out.println("Engine host: " + engineHost);
        System.out.println("Engine gRPC port: " + engineGrpcPort);
        
        // Try to connect to the engine's gRPC port
        boolean connected = false;
        try (Socket socket = new Socket(engineHost, Integer.parseInt(engineGrpcPort))) {
            connected = socket.isConnected();
            System.out.println("Successfully connected to engine gRPC port!");
        } catch (IOException e) {
            System.err.println("Failed to connect to engine gRPC port: " + e.getMessage());
        }
        
        assertThat(connected).isTrue().as("Should be able to connect to engine gRPC port");
    }
}