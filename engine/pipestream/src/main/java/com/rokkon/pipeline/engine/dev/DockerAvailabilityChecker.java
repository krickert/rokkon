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
    DockerClient dockerClient;
    
    private boolean dockerAvailable = false;
    
    void onStart(@Observes StartupEvent ev) {
        validateDockerAvailable();
    }
    
    private void validateDockerAvailable() {
        try {
            // Ping Docker daemon
            dockerClient.pingCmd().exec();
            dockerAvailable = true;
            LOG.info("✅ Docker is available and running");
            
            // Log Docker version info
            var version = dockerClient.versionCmd().exec();
            LOG.infof("Docker version: %s, API version: %s", 
                version.getVersion(), version.getApiVersion());
            
        } catch (Exception e) {
            dockerAvailable = false;
            LOG.error("❌ Docker is not available. Please ensure Docker is running.", e);
            LOG.error("Dev mode requires Docker to run infrastructure. Please start Docker and restart the application.");
        }
    }
    
    public boolean isDockerAvailable() {
        return dockerAvailable;
    }
}