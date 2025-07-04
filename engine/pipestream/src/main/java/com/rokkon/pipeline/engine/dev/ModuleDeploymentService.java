package com.rokkon.pipeline.engine.dev;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.CreateNetworkResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.ConflictException;
import com.github.dockerjava.api.model.*;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import io.smallrye.mutiny.Uni;
import java.time.Duration;
import com.rokkon.pipeline.engine.api.ModuleDeploymentSSE;
import io.quarkus.arc.Arc;

import java.util.*;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Service responsible for deploying pipeline modules with their sidecars in dev mode.
 * Each module gets:
 * - A dedicated network
 * - A Consul agent sidecar
 * - An OTEL collector sidecar
 * - The module container itself
 */
@ApplicationScoped
@IfBuildProfile("dev")
public class ModuleDeploymentService {
    
    private static final Logger LOG = Logger.getLogger(ModuleDeploymentService.class);
    
    @Inject
    DockerClient dockerClient;
    
    @Inject
    DockerAvailabilityChecker dockerChecker;
    
    @Inject
    HostIPDetector hostIPDetector;
    
    @ConfigProperty(name = "consul.server.port", defaultValue = "38500")
    int consulServerPort;
    
    @ConfigProperty(name = "consul.version", defaultValue = "1.21")
    String consulVersion;
    
    @ConfigProperty(name = "otel.collector.image", defaultValue = "otel/opentelemetry-collector-contrib:latest")
    String otelCollectorImage;
    
    @ConfigProperty(name = "grafana.endpoint", defaultValue = "")
    Optional<String> grafanaEndpoint;
    
    @ConfigProperty(name = "quarkus.otel.exporter.otlp.endpoint", defaultValue = "")
    Optional<String> otelEndpoint;
    
    /**
     * Deploys a module with its sidecars
     */
    public ModuleDeploymentResult deployModule(PipelineModule module) {
        LOG.infof("Starting deployment of module: %s", module.getModuleName());
        
        // Notify via SSE
        notifyDeploymentStarted(module.getModuleName());
        
        if (!dockerChecker.isDockerAvailable()) {
            notifyDeploymentFailed(module.getModuleName(), "Docker is not available");
            return new ModuleDeploymentResult(false, "Docker is not available");
        }
        
        // Clean up any existing containers first
        LOG.infof("Cleaning up any existing containers for module: %s", module.getModuleName());
        notifyDeploymentProgress(module.getModuleName(), "cleaning", "Cleaning up existing containers...");
        cleanupModule(module);
        
        try {
            // 1. Use bridge network instead of creating custom network
            String networkName = "bridge";  // Use default Docker bridge network
            LOG.infof("Using default bridge network for module %s", module.getModuleName());
            
            // 2. Start Consul agent sidecar
            notifyDeploymentProgress(module.getModuleName(), "consul", "Starting Consul agent sidecar...");
            String consulAgentId = startConsulAgent(module, networkName);
            
            // 3. Skip OTEL collector sidecar - modules will send directly to LGTM stack
            // String otelCollectorId = startOTELCollector(module, networkName);
            
            // 4. Start the module container
            notifyDeploymentProgress(module.getModuleName(), "module", "Starting module container...");
            String moduleId = startModuleContainer(module, networkName);
            
            // 5. Start registration sidecar
            notifyDeploymentProgress(module.getModuleName(), "registration", "Starting registration sidecar...");
            String registrationId = startRegistrationSidecar(module, networkName);
            
            LOG.infof("Successfully deployed module %s with sidecars", module.getModuleName());
            notifyDeploymentCompleted(module.getModuleName(), true, "Module deployed successfully");
            return new ModuleDeploymentResult(true, 
                "Module deployed successfully", 
                moduleId, 
                consulAgentId, 
                registrationId,  // Registration sidecar ID
                networkName);
                
        } catch (Exception e) {
            LOG.errorf("Failed to deploy module %s: %s", module.getModuleName(), e.getMessage(), e);
            // Cleanup on failure
            cleanupModule(module);
            notifyDeploymentFailed(module.getModuleName(), "Deployment failed: " + e.getMessage());
            return new ModuleDeploymentResult(false, "Deployment failed: " + e.getMessage());
        }
    }
    
    /**
     * Creates a dedicated network for the module
     */
    private String createModuleNetwork(PipelineModule module) {
        String networkName = module.getModuleName() + "-network";
        
        try {
            // Check if network already exists
            List<Network> networks = dockerClient.listNetworksCmd()
                .exec().stream()
                .filter(n -> networkName.equals(n.getName()))
                .collect(Collectors.toList());
                
            if (!networks.isEmpty()) {
                LOG.infof("Network %s already exists, reusing it", networkName);
                return networkName;
            }
            
            // Create new network
            CreateNetworkResponse response = dockerClient.createNetworkCmd()
                .withName(networkName)
                .withDriver("bridge")
                .withLabels(Map.of(
                    "pipeline.module", module.getModuleName(),
                    "pipeline.type", "module-network"
                ))
                .exec();
                
            LOG.infof("Created network %s with id %s", networkName, response.getId());
            
            // Wait a moment for network to be fully created
            Thread.sleep(500);
            
            return networkName;
            
        } catch (ConflictException e) {
            // Network already exists
            LOG.infof("Network %s already exists (conflict)", networkName);
            return networkName;
        } catch (Exception e) {
            LOG.errorf("Failed to create network %s: %s", networkName, e.getMessage());
            throw new RuntimeException("Failed to create network: " + e.getMessage(), e);
        }
    }
    
    /**
     * Starts the Consul agent sidecar
     */
    private String startConsulAgent(PipelineModule module, String networkName) {
        String containerName = module.getSidecarName();
        String hostIP = hostIPDetector.detectHostIP();
        
        // Remove existing container if it exists
        removeContainerIfExists(containerName);
        
        // No need to verify bridge network - it always exists
        
        // Create the Consul agent container with port bindings for the module
        ExposedPort modulePort = ExposedPort.tcp(module.getUnifiedPort());
        Ports portBindings = new Ports();
        portBindings.bind(modulePort, Ports.Binding.bindPort(module.getUnifiedPort()));
        
        CreateContainerResponse container = dockerClient.createContainerCmd("hashicorp/consul:" + consulVersion)
            .withName(containerName)
            .withExposedPorts(modulePort)
            .withHostConfig(HostConfig.newHostConfig()
                .withNetworkMode(networkName)
                .withPortBindings(portBindings)
            )
            .withCmd(
                "agent",
                "-node=" + module.getModuleName() + "-sidecar",
                "-client=0.0.0.0",
                "-bind=0.0.0.0",
                "-retry-join=" + hostIP + ":" + consulServerPort,
                "-log-level=info"
            )
            .withLabels(Map.of(
                "pipeline.module", module.getModuleName(),
                "pipeline.type", "consul-sidecar"
            ))
            .exec();
            
        // Start the container
        dockerClient.startContainerCmd(container.getId()).exec();
        LOG.infof("Started Consul agent sidecar: %s", containerName);
        
        // Wait for container to be running
        if (!waitForContainerRunning(container.getId(), containerName, Duration.ofSeconds(5))) {
            throw new RuntimeException("Consul container failed to start within timeout");
        }
        
        return container.getId();
    }
    
    /**
     * Starts the OTEL collector sidecar
     */
    private String startOTELCollector(PipelineModule module, String networkName) {
        String containerName = module.getModuleName() + "-otel-collector";
        String hostIP = hostIPDetector.detectHostIP();
        
        // Remove existing container if it exists
        removeContainerIfExists(containerName);
        
        // Determine LGTM endpoint (from Grafana DevServices or default)
        String lgtmEndpoint = grafanaEndpoint.orElse("http://" + hostIP + ":4317");
        
        // Create volume mount for config file
        Mount configMount = new Mount()
            .withType(MountType.BIND)
            .withSource(getClass().getResource("/otel-collector-config.yaml").getPath())
            .withTarget("/etc/otel-collector/config.yaml")
            .withReadOnly(true);
        
        // Create the OTEL collector container
        CreateContainerResponse container = dockerClient.createContainerCmd(otelCollectorImage)
            .withName(containerName)
            .withHostConfig(HostConfig.newHostConfig()
                .withNetworkMode(networkName)
                .withPortBindings(Arrays.asList(
                    PortBinding.parse("4317:4317"),  // OTLP gRPC
                    PortBinding.parse("4318:4318")   // OTLP HTTP
                ))
                .withMounts(List.of(configMount))
                .withExtraHosts("host.docker.internal:host-gateway")  // For accessing host services
            )
            .withCmd("--config=/etc/otel-collector/config.yaml")
            .withEnv(List.of(
                "LGTM_ENDPOINT=" + lgtmEndpoint
            ))
            .withLabels(Map.of(
                "pipeline.module", module.getModuleName(),
                "pipeline.type", "otel-sidecar"
            ))
            .exec();
            
        // Start the container
        dockerClient.startContainerCmd(container.getId()).exec();
        LOG.infof("Started OTEL collector sidecar: %s", containerName);
        
        return container.getId();
    }
    
    /**
     * Starts the module container
     */
    private String startModuleContainer(PipelineModule module, String networkName) {
        String containerName = module.getContainerName();
        String hostIP = hostIPDetector.detectHostIP();
        
        // Remove existing container if it exists
        removeContainerIfExists(containerName);
        
        // Calculate memory limits
        String memory = module.getDefaultMemory();
        long memoryBytes = parseMemoryString(memory);
        long memoryReservation = memoryBytes / 2;  // Reserve half
        
        // Create the module container - shares network with Consul sidecar
        CreateContainerResponse container = dockerClient.createContainerCmd(module.getDockerImage())
            .withName(containerName)
            .withHostConfig(HostConfig.newHostConfig()
                .withNetworkMode("container:" + module.getSidecarName())  // Share network with Consul sidecar
                .withMemory(memoryBytes)
                .withMemoryReservation(memoryReservation)
            )
            .withEnv(List.of(
                "MODULE_NAME=" + module.getModuleName() + "-module",
                "MODULE_HOST=0.0.0.0",
                "MODULE_PORT=" + module.getUnifiedPort(),
                "ENGINE_HOST=" + hostIP,
                "ENGINE_PORT=39001",  // Engine unified port
                "CONSUL_HOST=localhost",  // Via shared network namespace
                "CONSUL_PORT=8500",  // Consul HTTP API port
                "REGISTRATION_HOST=" + hostIP,  // What the engine should use to reach this module
                "REGISTRATION_PORT=" + module.getUnifiedPort(),  // Module's exposed port
                "QUARKUS_HTTP_PORT=" + module.getUnifiedPort(),
                "QUARKUS_PROFILE=dev",
                "OTEL_EXPORTER_OTLP_ENDPOINT=" + otelEndpoint.orElse("http://" + hostIP + ":4317"),
                "OTEL_SERVICE_NAME=" + module.getModuleName() + "-module",
                "JAVA_OPTS=-Xmx" + memory + " -Xms" + (parseMemoryString(memory) / 4 / 1024 / 1024) + "m",
                "AUTO_REGISTER=false",  // Disable auto registration - use sidecar instead
                "SHUTDOWN_ON_REGISTRATION_FAILURE=false"  // Keep module running even if registration fails
            ))
            .withLabels(Map.of(
                "pipeline.module", module.getModuleName(),
                "pipeline.type", "module"
            ))
            .exec();
            
        // Start the container
        dockerClient.startContainerCmd(container.getId()).exec();
        LOG.infof("Started module container: %s on port %d", containerName, module.getUnifiedPort());
        
        return container.getId();
    }
    
    /**
     * Starts the registration sidecar
     */
    private String startRegistrationSidecar(PipelineModule module, String networkName) {
        String containerName = module.getModuleName() + "-registrar";
        String hostIP = hostIPDetector.detectHostIP();
        
        // Remove existing container if it exists
        removeContainerIfExists(containerName);
        
        // Create a registration script
        String registrationScript = String.format("""
            #!/bin/sh
            echo 'Waiting for module to be ready...'
            sleep 15
            
            echo 'Attempting to register module %s with engine...'
            
            # Try to register the module
            if java -jar /deployments/pipeline-cli.jar register \
                --module-host=localhost \
                --module-port=%d \
                --engine-host=%s \
                --engine-port=39001 \
                --registration-host=%s \
                --registration-port=%d; then
                echo 'Module registered successfully!'
            else
                echo 'Registration failed, but continuing...'
            fi
            
            # Keep container alive for monitoring
            echo 'Registration sidecar complete. Sleeping...'
            sleep infinity
            """,
            module.getModuleName(),
            module.getUnifiedPort(),
            hostIP,
            hostIP,
            module.getUnifiedPort()
        );
        
        // Create the registration sidecar using the same image as the module (it has the CLI)
        CreateContainerResponse container = dockerClient.createContainerCmd(module.getDockerImage())
            .withName(containerName)
            .withHostConfig(HostConfig.newHostConfig()
                .withNetworkMode("container:" + module.getSidecarName())  // Share network with Consul sidecar
            )
            .withEntrypoint("/bin/sh")  // Override the default entrypoint
            .withCmd("-c", registrationScript)
            .withLabels(Map.of(
                "pipeline.module", module.getModuleName(),
                "pipeline.type", "registration-sidecar"
            ))
            .exec();
            
        // Start the container
        dockerClient.startContainerCmd(container.getId()).exec();
        LOG.infof("Started registration sidecar: %s", containerName);
        
        return container.getId();
    }
    
    /**
     * Removes a container if it exists
     */
    private void removeContainerIfExists(String containerName) {
        try {
            List<Container> containers = dockerClient.listContainersCmd()
                .withShowAll(true)
                .withNameFilter(List.of(containerName))
                .exec();
                
            for (Container container : containers) {
                LOG.infof("Removing existing container: %s", containerName);
                dockerClient.removeContainerCmd(container.getId())
                    .withForce(true)
                    .exec();
            }
        } catch (Exception e) {
            LOG.debugf("Failed to remove container %s: %s", containerName, e.getMessage());
        }
    }
    
    
    /**
     * Parses memory string (e.g., "1G", "512M") to bytes
     */
    private long parseMemoryString(String memory) {
        String value = memory.substring(0, memory.length() - 1);
        char unit = memory.charAt(memory.length() - 1);
        
        long bytes = Long.parseLong(value);
        return switch (unit) {
            case 'G' -> bytes * 1024 * 1024 * 1024;
            case 'M' -> bytes * 1024 * 1024;
            case 'K' -> bytes * 1024;
            default -> bytes;
        };
    }
    
    /**
     * Stops a deployed module and its sidecars
     */
    public void stopModule(PipelineModule module) {
        LOG.infof("Stopping module: %s", module.getModuleName());
        cleanupModule(module);
        
        // Notify via SSE
        try {
            var sseInstance = Arc.container().instance(ModuleDeploymentSSE.class);
            if (sseInstance.isAvailable()) {
                sseInstance.get().notifyModuleUndeployed(module.getModuleName());
            }
        } catch (Exception e) {
            LOG.debugf("Failed to send SSE notification: %s", e.getMessage());
        }
    }
    
    /**
     * Cleans up module resources
     */
    private void cleanupModule(PipelineModule module) {
        // Stop and remove containers
        removeContainerIfExists(module.getContainerName());
        removeContainerIfExists(module.getSidecarName());
        removeContainerIfExists(module.getModuleName() + "-registrar");  // Registration sidecar
        
        // Remove network only if it's a module-specific network
        String networkName = module.getModuleName() + "-network";
        try {
            // Check if this is a module-specific network we created
            List<Network> networks = dockerClient.listNetworksCmd()
                .exec().stream()
                .filter(n -> networkName.equals(n.getName()))
                .collect(Collectors.toList());
                
            if (!networks.isEmpty()) {
                Network network = networks.get(0);
                // Only remove if it has our labels
                if (network.getLabels() != null && 
                    "module-network".equals(network.getLabels().get("pipeline.type"))) {
                    dockerClient.removeNetworkCmd(networkName).exec();
                    LOG.infof("Removed module-specific network: %s", networkName);
                } else {
                    LOG.debugf("Network %s is not a module-specific network, skipping removal", networkName);
                }
            }
        } catch (Exception e) {
            LOG.debugf("Failed to remove network: %s", e.getMessage());
        }
    }
    
    /**
     * Wait for a container to be in running state
     */
    private boolean waitForContainerRunning(String containerId, String containerName, Duration timeout) {
        long startTime = System.currentTimeMillis();
        long timeoutMillis = timeout.toMillis();
        
        while (System.currentTimeMillis() - startTime < timeoutMillis) {
            try {
                InspectContainerResponse containerInfo = dockerClient.inspectContainerCmd(containerId).exec();
                if (containerInfo.getState() != null && Boolean.TRUE.equals(containerInfo.getState().getRunning())) {
                    LOG.infof("Container %s is running", containerName);
                    return true;
                }
                
                // Use LockSupport for more efficient waiting
                java.util.concurrent.locks.LockSupport.parkNanos(Duration.ofMillis(100).toNanos());
            } catch (Exception e) {
                LOG.debugf("Waiting for container to start: %s", e.getMessage());
            }
        }
        
        LOG.errorf("Container %s failed to start within %s", containerName, timeout);
        return false;
    }
    
    /**
     * Gets the status of a deployed module
     */
    public ModuleStatus getModuleStatus(PipelineModule module) {
        boolean moduleRunning = isContainerRunning(module.getContainerName());
        boolean consulRunning = isContainerRunning(module.getSidecarName());
        
        if (moduleRunning && consulRunning) {
            return ModuleStatus.RUNNING;
        } else if (moduleRunning || consulRunning) {
            return ModuleStatus.PARTIAL;
        } else {
            return ModuleStatus.STOPPED;
        }
    }
    
    /**
     * Checks if a container is running
     */
    private boolean isContainerRunning(String containerName) {
        try {
            List<Container> containers = dockerClient.listContainersCmd()
                .withShowAll(false)  // Only running
                .withNameFilter(List.of(containerName))
                .exec();
            return !containers.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Result of module deployment
     */
    public record ModuleDeploymentResult(
        boolean success,
        String message,
        String moduleContainerId,
        String consulContainerId,
        String otelContainerId,
        String networkName
    ) {
        public ModuleDeploymentResult(boolean success, String message) {
            this(success, message, null, null, null, null);
        }
    }
    
    /**
     * Module status enum
     */
    public enum ModuleStatus {
        RUNNING,
        PARTIAL,
        STOPPED
    }
    
    // SSE notification helpers
    private void notifyDeploymentStarted(String moduleName) {
        try {
            var sseInstance = Arc.container().instance(ModuleDeploymentSSE.class);
            if (sseInstance.isAvailable()) {
                sseInstance.get().notifyDeploymentStarted(moduleName);
            }
        } catch (Exception e) {
            LOG.debugf("Failed to send SSE notification: %s", e.getMessage());
        }
    }
    
    private void notifyDeploymentProgress(String moduleName, String status, String message) {
        try {
            var sseInstance = Arc.container().instance(ModuleDeploymentSSE.class);
            if (sseInstance.isAvailable()) {
                sseInstance.get().notifyDeploymentProgress(moduleName, status, message);
            }
        } catch (Exception e) {
            LOG.debugf("Failed to send SSE notification: %s", e.getMessage());
        }
    }
    
    private void notifyDeploymentCompleted(String moduleName, boolean success, String message) {
        try {
            var sseInstance = Arc.container().instance(ModuleDeploymentSSE.class);
            if (sseInstance.isAvailable()) {
                sseInstance.get().notifyDeploymentCompleted(moduleName, success, message);
            }
        } catch (Exception e) {
            LOG.debugf("Failed to send SSE notification: %s", e.getMessage());
        }
    }
    
    private void notifyDeploymentFailed(String moduleName, String message) {
        notifyDeploymentCompleted(moduleName, false, message);
    }
}