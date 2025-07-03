package com.rokkon.pipeline.engine.dev;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Duration;

/**
 * Manages Docker client lifecycle in dev mode, handling reconnection after
 * Quarkus dev mode reload when connection pools are shut down.
 */
@ApplicationScoped
@IfBuildProfile("dev")
public class DevModeDockerClientManager {
    
    private static final Logger LOG = Logger.getLogger(DevModeDockerClientManager.class);
    
    @ConfigProperty(name = "quarkus.docker-client.docker-host", defaultValue = "unix:///var/run/docker.sock")
    String dockerHost;
    
    @ConfigProperty(name = "quarkus.docker-client.connection-timeout", defaultValue = "30s")
    String connectionTimeout;
    
    @ConfigProperty(name = "quarkus.docker-client.read-timeout", defaultValue = "30s")
    String readTimeout;
    
    private volatile DockerClient currentClient;
    private volatile boolean isShutdown = false;
    
    /**
     * Gets the current Docker client, creating a new one if necessary.
     * This handles the case where the connection pool has been shut down.
     */
    public synchronized DockerClient getDockerClient() {
        if (currentClient == null || !isClientHealthy()) {
            createNewClient();
        }
        return currentClient;
    }
    
    /**
     * Checks if the current client is healthy by attempting a ping.
     */
    private boolean isClientHealthy() {
        if (currentClient == null || isShutdown) {
            return false;
        }
        
        try {
            currentClient.pingCmd().exec();
            return true;
        } catch (IllegalStateException e) {
            if (e.getMessage() != null && e.getMessage().contains("Connection pool shut down")) {
                LOG.debug("Docker client connection pool shut down, will recreate");
                return false;
            }
            throw e;
        } catch (Exception e) {
            LOG.debug("Docker client health check failed: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Creates a new Docker client instance.
     */
    private void createNewClient() {
        LOG.info("Creating new Docker client for dev mode");
        
        // Close old client if exists
        if (currentClient != null) {
            try {
                currentClient.close();
            } catch (Exception e) {
                LOG.debug("Error closing old Docker client: " + e.getMessage());
            }
        }
        
        try {
            DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(dockerHost)
                .build();
            
            Duration connectTimeout = Duration.parse("PT" + connectionTimeout.toUpperCase());
            Duration responseTimeout = Duration.parse("PT" + readTimeout.toUpperCase());
            
            DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .connectionTimeout(connectTimeout)
                .responseTimeout(responseTimeout)
                .build();
            
            currentClient = DockerClientImpl.getInstance(config, httpClient);
            
            // Test the connection
            currentClient.pingCmd().exec();
            LOG.info("✅ Docker client created successfully");
            isShutdown = false;
            
        } catch (Exception e) {
            LOG.error("Failed to create Docker client", e);
            throw new RuntimeException("Failed to create Docker client: " + e.getMessage(), e);
        }
    }
    
    @PreDestroy
    void cleanup() {
        LOG.debug("DevModeDockerClientManager cleanup called");
        isShutdown = true;
        if (currentClient != null) {
            try {
                currentClient.close();
            } catch (Exception e) {
                LOG.debug("Error during Docker client cleanup: " + e.getMessage());
            }
            currentClient = null;
        }
    }
}