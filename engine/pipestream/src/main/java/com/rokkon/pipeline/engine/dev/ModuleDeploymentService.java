package com.rokkon.pipeline.engine.dev;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.CreateNetworkResponse;
import com.github.dockerjava.api.exception.ConflictException;
import com.github.dockerjava.api.model.*;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.*;
import java.util.Arrays;

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
        
        if (!dockerChecker.isDockerAvailable()) {
            return new ModuleDeploymentResult(false, "Docker is not available");
        }
        
        try {
            // 1. Create dedicated network for the module
            String networkName = createModuleNetwork(module);
            
            // 2. Start Consul agent sidecar
            String consulAgentId = startConsulAgent(module, networkName);
            
            // 3. Skip OTEL collector sidecar - modules will send directly to LGTM stack
            // String otelCollectorId = startOTELCollector(module, networkName);
            
            // 4. Start the module container
            String moduleId = startModuleContainer(module, networkName);
            
            LOG.infof("Successfully deployed module %s with Consul sidecar", module.getModuleName());
            return new ModuleDeploymentResult(true, 
                "Module deployed successfully", 
                moduleId, 
                consulAgentId, 
                null,  // No OTEL sidecar
                networkName);
                
        } catch (Exception e) {
            LOG.errorf("Failed to deploy module %s", module.getModuleName(), e);
            // Cleanup on failure
            cleanupModule(module);
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
                .withNameFilter(networkName)
                .exec();
                
            if (!networks.isEmpty()) {
                LOG.infof("Network %s already exists", networkName);
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
            return networkName;
            
        } catch (ConflictException e) {
            // Network already exists
            LOG.infof("Network %s already exists", networkName);
            return networkName;
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
                "QUARKUS_HTTP_PORT=" + module.getUnifiedPort(),
                "QUARKUS_PROFILE=dev",
                "OTEL_EXPORTER_OTLP_ENDPOINT=" + otelEndpoint.orElse("http://" + hostIP + ":4317"),
                "OTEL_SERVICE_NAME=" + module.getModuleName() + "-module",
                "JAVA_OPTS=-Xmx" + memory + " -Xms" + (parseMemoryString(memory) / 4 / 1024 / 1024) + "m",
                "AUTO_REGISTER=false",  // Disable auto registration in entrypoint
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
    }
    
    /**
     * Cleans up module resources
     */
    private void cleanupModule(PipelineModule module) {
        // Stop and remove containers
        removeContainerIfExists(module.getContainerName());
        removeContainerIfExists(module.getSidecarName());
        // No OTEL collector to remove
        
        // Remove network
        try {
            String networkName = module.getModuleName() + "-network";
            dockerClient.removeNetworkCmd(networkName).exec();
            LOG.infof("Removed network: %s", networkName);
        } catch (Exception e) {
            LOG.debugf("Failed to remove network: %s", e.getMessage());
        }
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
}