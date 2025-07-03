package com.rokkon.pipeline.engine.dev;

import com.github.dockerjava.api.DockerClient;
import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Validates Docker availability in dev mode.
 * Only active when running with dev profile.
 */
@ApplicationScoped
@IfBuildProfile("dev")
public class DockerAvailabilityChecker {
    
    private static final Logger LOG = Logger.getLogger(DockerAvailabilityChecker.class);
    
    @Inject
    DevModeDockerClientManager dockerClientManager;
    
    private boolean dockerAvailable = false;
    
    void onStart(@Observes StartupEvent ev) {
        validateDockerAvailable();
    }
    
    private void validateDockerAvailable() {
        try {
            // Get Docker client from our manager which handles reconnection
            var dockerClient = dockerClientManager.getDockerClient();
            
            // Ping Docker daemon
            dockerClient.pingCmd().exec();
            dockerAvailable = true;
            LOG.info("✅ Docker is available and running");
            
            // Log Docker version info
            var version = dockerClient.versionCmd().exec();
            LOG.infof("Docker version: %s, API version: %s", 
                version.getVersion(), version.getApiVersion());
            
            // Log some basic info
            var info = dockerClient.infoCmd().exec();
            LOG.infof("Docker: %d containers, %d images", 
                info.getContainers(), info.getImages());
            
        } catch (Exception e) {
            dockerAvailable = false;
            // Don't fail startup, just warn
            LOG.warn("⚠️ Could not connect to Docker. Some dev features may not work: " + e.getMessage());
        }
    }
    
    public boolean isDockerAvailable() {
        // If Docker was previously unavailable, retry the connection
        // This handles the case where the connection pool was shut down during reload
        if (!dockerAvailable) {
            validateDockerAvailable();
        }
        return dockerAvailable;
    }
    
    /**
     * Force a reconnection attempt to Docker.
     * Useful after dev mode reload when connection pools may have been shut down.
     */
    public void reconnect() {
        LOG.debug("Attempting to reconnect to Docker...");
        validateDockerAvailable();
    }
}