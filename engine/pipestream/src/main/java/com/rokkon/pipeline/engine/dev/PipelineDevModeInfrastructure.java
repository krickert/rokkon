package com.rokkon.pipeline.engine.dev;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.Container;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the development mode infrastructure including Docker containers and Consul sidecars.
 * This class provides the foundation for starting/stopping modules and infrastructure in dev mode.
 */
@ApplicationScoped
@IfBuildProfile("dev")
public class PipelineDevModeInfrastructure {
    
    private static final Logger LOG = Logger.getLogger(PipelineDevModeInfrastructure.class);
    private static final String PROJECT_NAME = "pipeline-dev";
    private static final String LABEL_PROJECT = "com.docker.compose.project";
    
    @Inject
    DockerClient dockerClient;
    
    @Inject
    HostIPDetector hostIPDetector;
    
    @Inject
    DockerAvailabilityChecker dockerChecker;
    
    @ConfigProperty(name = "pipeline.dev.auto-start", defaultValue = "true")
    boolean autoStart;
    
    @ConfigProperty(name = "pipeline.dev.compose-project-name", defaultValue = "pipeline-dev")
    String composeProjectName;
    
    // Track running containers
    private final Map<String, String> runningContainers = new ConcurrentHashMap<>();
    
    @PostConstruct
    void init() {
        if (!dockerChecker.isDockerAvailable()) {
            LOG.error("Docker is not available. Dev mode infrastructure cannot be initialized.");
            return;
        }
        
        // Detect host IP early
        String hostIP = hostIPDetector.detectHostIP();
        LOG.infof("Host IP detected for Docker containers: %s", hostIP);
        
        // Log current dev mode configuration
        LOG.infof("Dev mode infrastructure initialized - Auto-start: %s, Project: %s", 
                  autoStart, composeProjectName);
    }
    
    /**
     * Checks if a container is running by name
     */
    public boolean isContainerRunning(String containerName) {
        try {
            List<Container> containers = dockerClient.listContainersCmd()
                .withShowAll(false) // Only running containers
                .exec();
                
            return containers.stream()
                .anyMatch(c -> {
                    // Check both container name and compose service name
                    String[] names = c.getNames();
                    for (String name : names) {
                        if (name.contains(containerName)) {
                            return true;
                        }
                    }
                    return false;
                });
        } catch (Exception e) {
            LOG.error("Failed to check container status", e);
            return false;
        }
    }
    
    /**
     * Gets all containers belonging to the dev project
     */
    public List<Container> getProjectContainers() {
        try {
            return dockerClient.listContainersCmd()
                .withShowAll(true)
                .withLabelFilter(Map.of(LABEL_PROJECT, composeProjectName))
                .exec();
        } catch (Exception e) {
            LOG.error("Failed to list project containers", e);
            return List.of();
        }
    }
    
    /**
     * Checks if a specific module is running
     */
    public boolean isModuleRunning(PipelineModule module) {
        return isContainerRunning(module.getContainerName()) && 
               isContainerRunning(module.getSidecarName());
    }
    
    /**
     * Gets container health status
     */
    public Optional<String> getContainerHealth(String containerName) {
        try {
            List<Container> containers = dockerClient.listContainersCmd()
                .withShowAll(false)
                .exec();
                
            Optional<Container> container = containers.stream()
                .filter(c -> {
                    for (String name : c.getNames()) {
                        if (name.contains(containerName)) {
                            return true;
                        }
                    }
                    return false;
                })
                .findFirst();
                
            if (container.isPresent()) {
                String containerId = container.get().getId();
                InspectContainerResponse info = dockerClient.inspectContainerCmd(containerId).exec();
                
                if (info.getState() != null) {
                    // For now, just return running/stopped status
                    // Full health check would require more complex handling
                    return Optional.of(info.getState().getRunning() ? "running" : "stopped");
                }
                return Optional.of("unknown");
            }
        } catch (Exception e) {
            LOG.debug("Failed to get container health", e);
        }
        return Optional.empty();
    }
    
    /**
     * Gets memory usage statistics for a container
     */
    public Optional<MemoryStats> getContainerMemoryStats(String containerName) {
        try {
            List<Container> containers = dockerClient.listContainersCmd()
                .withShowAll(false)
                .exec();
                
            Optional<Container> container = containers.stream()
                .filter(c -> {
                    for (String name : c.getNames()) {
                        if (name.contains(containerName)) {
                            return true;
                        }
                    }
                    return false;
                })
                .findFirst();
                
            if (container.isPresent()) {
                // Note: Full stats would require stats streaming API
                // For now, return basic info from container inspection
                String containerId = container.get().getId();
                InspectContainerResponse info = dockerClient.inspectContainerCmd(containerId).exec();
                
                if (info.getHostConfig() != null && info.getHostConfig().getMemory() != null) {
                    long limit = info.getHostConfig().getMemory();
                    return Optional.of(new MemoryStats(0L, limit)); // Usage requires stats API
                }
            }
        } catch (Exception e) {
            LOG.debug("Failed to get memory stats", e);
        }
        return Optional.empty();
    }
    
    /**
     * Simple memory statistics record
     */
    public record MemoryStats(long usage, long limit) {
        public double getUsagePercentage() {
            return limit > 0 ? (double) usage / limit * 100 : 0;
        }
    }
    
    /**
     * Gets the detected host IP
     */
    public String getHostIP() {
        return hostIPDetector.detectHostIP();
    }
}