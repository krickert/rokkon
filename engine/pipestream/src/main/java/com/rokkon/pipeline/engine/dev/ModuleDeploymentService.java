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
import com.rokkon.pipeline.commons.model.GlobalModuleRegistryService;
import com.rokkon.pipeline.engine.events.ModuleLifecycleEvent;
import jakarta.enterprise.event.Event;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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
    
    @Inject
    GlobalModuleRegistryService moduleRegistry;
    
    @Inject
    Event<ModuleLifecycleEvent> moduleLifecycleEvent;
    
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
    
    // Track instance numbers for each module
    private final Map<String, Integer> moduleInstanceCounts = new HashMap<>();
    
    // Track modules currently being deployed to prevent duplicates
    private final Set<String> deployingModules = new ConcurrentHashMap<String, Boolean>().keySet(Boolean.TRUE);
    
    // Port range for modules
    private static final int BASE_MODULE_PORT = 39100;
    private static final int MAX_MODULE_PORT = 39999;
    
    /**
     * Find the next available port starting from BASE_MODULE_PORT
     */
    private int findNextAvailablePort() {
        // Scan running containers to find ports in use
        Set<Integer> portsInUse = new HashSet<>();
        try {
            List<Container> containers = dockerClient.listContainersCmd().exec();
            for (Container container : containers) {
                for (ContainerPort port : container.getPorts()) {
                    if (port.getPublicPort() != null && port.getPublicPort() >= BASE_MODULE_PORT && port.getPublicPort() <= MAX_MODULE_PORT) {
                        portsInUse.add(port.getPublicPort().intValue());
                    }
                }
            }
        } catch (Exception e) {
            LOG.warnf("Failed to scan container ports: %s", e.getMessage());
        }
        
        // Find the first available port
        for (int port = BASE_MODULE_PORT; port <= MAX_MODULE_PORT; port++) {
            if (!portsInUse.contains(port)) {
                LOG.debugf("Found available port: %d", port);
                return port;
            }
        }
        
        throw new RuntimeException("No available ports in range " + BASE_MODULE_PORT + "-" + MAX_MODULE_PORT);
    }
    
    /**
     * Deploys a module with its sidecars
     */
    public ModuleDeploymentResult deployModule(PipelineModule module) {
        // For first deployment, use instance 1
        return deployModuleInstance(module, 1);
    }
    
    /**
     * Deploys an additional instance of a module
     */
    public ModuleDeploymentResult deployAdditionalInstance(PipelineModule module) {
        // First check how many instances are actually running
        int runningInstances = countRunningInstances(module);
        moduleInstanceCounts.put(module.getModuleName(), runningInstances);
        
        // Get current instance count
        int currentCount = moduleInstanceCounts.getOrDefault(module.getModuleName(), 0);
        if (currentCount >= 10) {
            return new ModuleDeploymentResult(false, 
                "Maximum instances (10) reached for module: " + module.getModuleName());
        }
        
        int nextInstance = currentCount + 1;
        return deployModuleInstance(module, nextInstance);
    }
    
    /**
     * Count how many instances of a module are currently running
     */
    private int countRunningInstances(PipelineModule module) {
        int count = 0;
        for (int i = 1; i <= 10; i++) {
            String containerName = i == 1 ? module.getContainerName() : module.getContainerName() + "-" + i;
            if (isContainerRunning(containerName)) {
                count = i;
            }
        }
        return count;
    }
    
    /**
     * Internal method to deploy a specific module instance
     */
    private ModuleDeploymentResult deployModuleInstance(PipelineModule module, int instanceNumber) {
        String deploymentKey = module.getModuleName() + "-" + instanceNumber;
        
        // Check if already deploying
        if (!deployingModules.add(deploymentKey)) {
            LOG.warnf("Deployment already in progress for module: %s (instance %d)", module.getModuleName(), instanceNumber);
            return new ModuleDeploymentResult(false, "Deployment already in progress");
        }
        
        try {
            LOG.infof("Starting deployment of module: %s (instance %d)", module.getModuleName(), instanceNumber);
            
            // Notify via SSE
            notifyDeploymentStarted(module.getModuleName());
        
        // Fire module lifecycle event
        moduleLifecycleEvent.fire(new ModuleLifecycleEvent(
            module.getModuleName(), 
            ModuleLifecycleEvent.EventType.DEPLOYING, 
            "Starting module deployment (instance " + instanceNumber + ")"
        ));
        
        if (!dockerChecker.isDockerAvailable()) {
            String errorMsg = "Docker is not available";
            notifyDeploymentFailed(module.getModuleName(), errorMsg);
            moduleLifecycleEvent.fire(new ModuleLifecycleEvent(
                module.getModuleName(),
                ModuleLifecycleEvent.EventType.DEPLOYMENT_FAILED,
                errorMsg
            ));
            return new ModuleDeploymentResult(false, "Docker is not available");
        }
        
        // For additional instances, only clean up the specific instance we're deploying
        if (instanceNumber > 1) {
            LOG.infof("Cleaning up any existing containers for module %s instance %d", module.getModuleName(), instanceNumber);
            notifyDeploymentProgress(module.getModuleName(), "cleaning", "Preparing instance " + instanceNumber + "...");
            
            // Clean up only this specific instance's containers
            removeContainerIfExists(module.getModuleName() + "-registrar-" + instanceNumber);
            removeContainerIfExists(module.getContainerName() + "-" + instanceNumber);
            removeContainerIfExists(module.getSidecarName() + "-" + instanceNumber);
        } else {
            // For instance 1, do the full cleanup as before
            LOG.infof("Cleaning up any existing containers for module: %s", module.getModuleName());
            notifyDeploymentProgress(module.getModuleName(), "cleaning", "Cleaning up existing containers...");
            
            // Clean up only instance 1 containers
            removeContainerIfExists(module.getModuleName() + "-registrar");
            removeContainerIfExists(module.getContainerName());
            removeContainerIfExists(module.getSidecarName());
        }
        
        try {
            // 1. Allocate a unique port for this instance
            int allocatedPort = findNextAvailablePort();
            LOG.infof("Allocated port %d for module %s instance %d", allocatedPort, module.getModuleName(), instanceNumber);
            
            // 2. Use bridge network instead of creating custom network
            String networkName = "bridge";  // Use default Docker bridge network
            LOG.infof("Using default bridge network for module %s", module.getModuleName());
            
            // 3. Start Consul agent sidecar
            notifyDeploymentProgress(module.getModuleName(), "consul", "Starting Consul agent sidecar...");
            String consulAgentId = startConsulAgent(module, networkName, instanceNumber, allocatedPort);
            
            // 3. Skip OTEL collector sidecar - modules will send directly to LGTM stack
            // String otelCollectorId = startOTELCollector(module, networkName);
            
            // 4. Start the module container
            notifyDeploymentProgress(module.getModuleName(), "module", "Starting module container...");
            String moduleId = startModuleContainer(module, networkName, instanceNumber);
            
            // All instances use host IP since they all get unique host ports now
            String hostIP = hostIPDetector.detectHostIP();
            LOG.infof("Module instance %d will register with host IP %s and port %d", 
                instanceNumber, hostIP, allocatedPort);
            
            // 5. Start registration sidecar
            notifyDeploymentProgress(module.getModuleName(), "registration", "Starting registration sidecar...");
            String registrationId = startRegistrationSidecar(module, networkName, instanceNumber, hostIP, allocatedPort);
            
            LOG.infof("Successfully deployed module %s with sidecars", module.getModuleName());
            notifyDeploymentCompleted(module.getModuleName(), true, "Module deployed successfully");
            
            // Fire module deployed event
            moduleLifecycleEvent.fire(new ModuleLifecycleEvent(
                module.getModuleName(),
                ModuleLifecycleEvent.EventType.DEPLOYED,
                "Module deployed successfully"
            ));
            // Update instance count
            moduleInstanceCounts.put(module.getModuleName(), instanceNumber);
            
            return new ModuleDeploymentResult(true, 
                "Module deployed successfully (instance " + instanceNumber + ")", 
                moduleId, 
                consulAgentId, 
                registrationId,  // Registration sidecar ID
                networkName,
                instanceNumber,
                allocatedPort);
                
        } catch (Exception e) {
            LOG.errorf("Failed to deploy module %s: %s", module.getModuleName(), e.getMessage(), e);
            // Cleanup on failure
            cleanupModule(module);
            notifyDeploymentFailed(module.getModuleName(), "Deployment failed: " + e.getMessage());
            
            // Fire deployment failed event
            moduleLifecycleEvent.fire(new ModuleLifecycleEvent(
                module.getModuleName(),
                ModuleLifecycleEvent.EventType.DEPLOYMENT_FAILED,
                "Deployment failed: " + e.getMessage(),
                null,
                e
            ));
            return new ModuleDeploymentResult(false, "Deployment failed: " + e.getMessage());
        }
        } finally {
            // Always remove from deploying set
            deployingModules.remove(deploymentKey);
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
    private String startConsulAgent(PipelineModule module, String networkName, int instanceNumber, int hostPort) {
        String containerName = instanceNumber == 1 
            ? module.getSidecarName() 
            : module.getSidecarName() + "-" + instanceNumber;
        String hostIP = hostIPDetector.detectHostIP();
        
        // Remove existing container if it exists
        removeContainerIfExists(containerName);
        
        // No need to verify bridge network - it always exists
        
        // Create the Consul agent container with port bindings for the module
        // All instances get a unique host port binding
        ExposedPort modulePort = ExposedPort.tcp(module.getUnifiedPort());
        Ports portBindings = new Ports();
        
        // Bind the module's internal port to the allocated host port
        portBindings.bind(modulePort, Ports.Binding.bindPort(hostPort));
        
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
                "pipeline.module.name", module.getModuleName(),
                "pipeline.type", "consul-sidecar",
                "pipeline.sidecar.for", module.getModuleName(),
                "pipeline.instance", String.valueOf(instanceNumber)
            ))
            .exec();
            
        // Start the container
        dockerClient.startContainerCmd(container.getId()).exec();
        LOG.infof("Started Consul agent sidecar: %s", containerName);
        
        // Wait for container to be running
        if (!waitForContainerRunning(container.getId(), containerName, Duration.ofSeconds(10))) {
            throw new RuntimeException("Consul container failed to start within timeout");
        }
        
        // Additional wait to ensure container is fully ready for network sharing
        try {
            Thread.sleep(1000);  // Give Docker a moment to fully initialize the container
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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
    private String startModuleContainer(PipelineModule module, String networkName, int instanceNumber) {
        String containerName = instanceNumber == 1 
            ? module.getContainerName() 
            : module.getContainerName() + "-" + instanceNumber;
        String hostIP = hostIPDetector.detectHostIP();
        
        // Remove existing container if it exists
        removeContainerIfExists(containerName);
        
        // Calculate memory limits
        String memory = module.getDefaultMemory();
        long memoryBytes = parseMemoryString(memory);
        long memoryReservation = memoryBytes / 2;  // Reserve half
        
        // Create the module container - shares network with Consul sidecar
        CreateContainerResponse container = null;
        int retries = 3;
        Exception lastException = null;
        
        for (int i = 0; i < retries; i++) {
            try {
                container = dockerClient.createContainerCmd(module.getDockerImage())
                    .withName(containerName)
                    .withHostConfig(HostConfig.newHostConfig()
                        .withNetworkMode("container:" + (instanceNumber == 1 
                            ? module.getSidecarName() 
                            : module.getSidecarName() + "-" + instanceNumber))  // Share network with Consul sidecar
                        .withMemory(memoryBytes)
                        .withMemoryReservation(memoryReservation)
                    )
                    .withEnv(List.of(
                        "MODULE_NAME=" + module.getModuleName() + "-module" + (instanceNumber > 1 ? "-" + instanceNumber : ""),
                        "MODULE_HOST=0.0.0.0",
                        "MODULE_PORT=" + module.getUnifiedPort(),
                        "ENGINE_HOST=" + hostIP,
                        "ENGINE_PORT=39001",  // Engine unified port
                        "CONSUL_HOST=localhost",  // Via shared network namespace
                        "CONSUL_PORT=8500",  // Consul HTTP API port
                        // Registration is handled by the sidecar, not needed here
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
                        "pipeline.module.name", module.getModuleName(),
                        "pipeline.type", "module",
                        "pipeline.grpc", "true",
                        "pipeline.port", String.valueOf(module.getUnifiedPort()),
                        "pipeline.instance", String.valueOf(instanceNumber)
                    ))
                    .exec();
                break;  // Success!
            } catch (Exception e) {
                lastException = e;
                if (e.getMessage() != null && e.getMessage().contains("cannot join network namespace")) {
                    LOG.warnf("Consul container not ready for network sharing (attempt %d/%d), waiting...", i + 1, retries);
                    try {
                        Thread.sleep(2000);  // Wait 2 seconds before retry
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted while waiting for retry", ie);
                    }
                } else {
                    throw e;  // Different error, don't retry
                }
            }
        }
        
        if (container == null) {
            throw new RuntimeException("Failed to create module container after " + retries + " attempts", lastException);
        }
            
        // Start the container
        dockerClient.startContainerCmd(container.getId()).exec();
        LOG.infof("Started module container: %s on port %d", containerName, module.getUnifiedPort());
        
        return container.getId();
    }
    
    /**
     * Starts the registration sidecar
     */
    private String startRegistrationSidecar(PipelineModule module, String networkName, int instanceNumber, String registrationHost, int allocatedPort) {
        String containerName = instanceNumber == 1 
            ? module.getModuleName() + "-registrar"
            : module.getModuleName() + "-registrar-" + instanceNumber;
        String hostIP = hostIPDetector.detectHostIP();
        
        // Remove existing container if it exists
        removeContainerIfExists(containerName);
        
        // Create a registration script using the provided registration host and allocated port
        String moduleName = module.getModuleName() + "-module" + (instanceNumber > 1 ? "-" + instanceNumber : "");
        String registrationScript = String.format("""
            #!/bin/sh
            echo 'Waiting for module to be ready...'
            sleep 15
            
            echo 'Attempting to register module %s (instance %d) with engine...'
            echo 'Registration host: %s'
            echo 'Registration port: %d'
            
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
            moduleName,
            instanceNumber,
            registrationHost,
            allocatedPort,
            module.getUnifiedPort(),
            hostIP,
            registrationHost,
            allocatedPort
        );
        
        // Create the registration sidecar using the same image as the module (it has the CLI)
        CreateContainerResponse container = null;
        int retries = 3;
        Exception lastException = null;
        
        for (int i = 0; i < retries; i++) {
            try {
                container = dockerClient.createContainerCmd(module.getDockerImage())
                    .withName(containerName)
                    .withHostConfig(HostConfig.newHostConfig()
                        .withNetworkMode("container:" + (instanceNumber == 1 
                            ? module.getSidecarName() 
                            : module.getSidecarName() + "-" + instanceNumber))  // Share network with Consul sidecar
                    )
                    .withEntrypoint("/bin/sh")  // Override the default entrypoint
                    .withCmd("-c", registrationScript)
                    .withLabels(Map.of(
                        "pipeline.module", module.getModuleName(),
                        "pipeline.module.name", module.getModuleName(),
                        "pipeline.type", "registration-sidecar",
                        "pipeline.sidecar.for", module.getModuleName(),
                        "pipeline.instance", String.valueOf(instanceNumber)
                    ))
                    .exec();
                break;  // Success!
            } catch (Exception e) {
                lastException = e;
                if (e.getMessage() != null && e.getMessage().contains("cannot join network namespace")) {
                    LOG.warnf("Consul container not ready for network sharing (attempt %d/%d), waiting...", i + 1, retries);
                    try {
                        Thread.sleep(2000);  // Wait 2 seconds before retry
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted while waiting for retry", ie);
                    }
                } else {
                    throw e;  // Different error, don't retry
                }
            }
        }
        
        if (container == null) {
            throw new RuntimeException("Failed to create registration sidecar after " + retries + " attempts", lastException);
        }
            
        // Start the container
        dockerClient.startContainerCmd(container.getId()).exec();
        LOG.infof("Started registration sidecar: %s", containerName);
        
        return container.getId();
    }
    
    /**
     * Remove a container by Container object
     */
    private void removeContainer(Container container) {
        String containerName = container.getNames()[0];
        containerName = containerName.startsWith("/") ? containerName.substring(1) : containerName;
        removeContainerIfExists(containerName);
    }
    
    /**
     * Removes a container if it exists, with proper handling of "removing" state
     */
    private void removeContainerIfExists(String containerName) {
        try {
            List<Container> containers = dockerClient.listContainersCmd()
                .withShowAll(true)
                .withNameFilter(List.of(containerName))
                .exec();
                
            for (Container container : containers) {
                LOG.infof("Found existing container: %s (state: %s)", containerName, container.getState());
                
                // First try to stop the container if it's running
                if ("running".equalsIgnoreCase(container.getState())) {
                    try {
                        LOG.infof("Stopping container: %s", containerName);
                        dockerClient.stopContainerCmd(container.getId())
                            .withTimeout(5)  // 5 seconds timeout
                            .exec();
                        // Wait a bit for stop to complete
                        Thread.sleep(500);
                    } catch (Exception e) {
                        LOG.debugf("Failed to stop container %s: %s", containerName, e.getMessage());
                    }
                }
                
                // Now remove the container
                try {
                    LOG.infof("Removing container: %s", containerName);
                    dockerClient.removeContainerCmd(container.getId())
                        .withForce(true)
                        .exec();
                } catch (Exception e) {
                    // If it's already being removed, wait for it
                    if (e.getMessage() != null && e.getMessage().contains("is removing")) {
                        LOG.infof("Container %s is already being removed, waiting...", containerName);
                        waitForContainerRemoval(containerName, Duration.ofSeconds(10));
                    } else {
                        throw e;
                    }
                }
            }
            
            // Final check - wait to ensure container is fully removed
            waitForContainerRemoval(containerName, Duration.ofSeconds(5));
            
        } catch (Exception e) {
            LOG.warnf("Failed to remove container %s: %s", containerName, e.getMessage());
            // Don't fail deployment, just warn
        }
    }
    
    /**
     * Waits for a container to be fully removed
     */
    private void waitForContainerRemoval(String containerName, Duration timeout) {
        long startTime = System.currentTimeMillis();
        long timeoutMillis = timeout.toMillis();
        
        while (System.currentTimeMillis() - startTime < timeoutMillis) {
            try {
                List<Container> containers = dockerClient.listContainersCmd()
                    .withShowAll(true)
                    .withNameFilter(List.of(containerName))
                    .exec();
                    
                if (containers.isEmpty()) {
                    LOG.infof("Container %s has been fully removed", containerName);
                    return;
                }
                
                // Check if any container is still in "removing" state
                boolean stillRemoving = containers.stream()
                    .anyMatch(c -> "removing".equalsIgnoreCase(c.getState()));
                    
                if (!stillRemoving && containers.stream().allMatch(c -> "exited".equalsIgnoreCase(c.getState()))) {
                    // All containers are exited, try to remove them again
                    for (Container container : containers) {
                        try {
                            dockerClient.removeContainerCmd(container.getId())
                                .withForce(true)
                                .exec();
                        } catch (Exception e) {
                            LOG.debugf("Retry remove failed: %s", e.getMessage());
                        }
                    }
                }
                
                // Wait a bit before checking again
                Thread.sleep(500);
            } catch (Exception e) {
                LOG.debugf("Error checking container removal: %s", e.getMessage());
            }
        }
        
        LOG.warnf("Container %s was not fully removed within %s", containerName, timeout);
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
     * Stops and removes a specific module instance by moduleId
     */
    public void stopModuleByInstanceId(PipelineModule module, String moduleId) {
        LOG.infof("Stopping module instance by ID: %s (moduleId: %s)", module.getModuleName(), moduleId);
        
        // Fire undeploying event
        moduleLifecycleEvent.fire(new ModuleLifecycleEvent(
            module.getModuleName(),
            ModuleLifecycleEvent.EventType.UNDEPLOYING,
            String.format("Stopping module instance %s", moduleId)
        ));
        
        // First, deregister from Consul
        moduleRegistry.deregisterModule(moduleId)
            .subscribe().with(
                success -> {
                    if (success) {
                        LOG.infof("Successfully deregistered module instance: %s", moduleId);
                        moduleLifecycleEvent.fire(new ModuleLifecycleEvent(
                            module.getModuleName(),
                            ModuleLifecycleEvent.EventType.DEREGISTERED,
                            String.format("Module instance %s deregistered", moduleId)
                        ));
                    } else {
                        LOG.warnf("Failed to deregister module instance: %s", moduleId);
                    }
                },
                failure -> LOG.errorf(failure, "Error deregistering module instance: %s", moduleId)
            );
        
        // Extract instance suffix from moduleId (e.g., "echo-module-abc123" -> find containers)
        // We need to find which containers belong to this specific module instance
        // by matching the port from the registration
        moduleRegistry.getModule(moduleId)
            .subscribe().with(
                registration -> {
                    if (registration != null) {
                        int port = registration.port();
                        // Find containers using this port
                        cleanupModuleInstanceByPort(module, port);
                    } else {
                        LOG.warnf("No registration found for moduleId: %s, attempting cleanup by name pattern", moduleId);
                        // Try to clean up by moduleId pattern
                        cleanupModuleInstanceByPattern(module, moduleId);
                    }
                },
                failure -> {
                    LOG.errorf(failure, "Failed to get module registration for cleanup: %s", moduleId);
                    // Try cleanup anyway
                    cleanupModuleInstanceByPattern(module, moduleId);
                }
            );
        
        // Fire undeployed event
        moduleLifecycleEvent.fire(new ModuleLifecycleEvent(
            module.getModuleName(),
            ModuleLifecycleEvent.EventType.UNDEPLOYED,
            String.format("Module instance %s stopped successfully", moduleId)
        ));
        
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
     * Clean up module instance by port number
     */
    private void cleanupModuleInstanceByPort(PipelineModule module, int port) {
        LOG.infof("Cleaning up module instance by port: %s (port: %d)", module.getModuleName(), port);
        
        // Find containers that expose this port
        List<Container> containers = dockerClient.listContainersCmd()
            .withShowAll(true)
            .exec().stream()
            .filter(container -> {
                // Check if this container exposes the target port
                ContainerPort[] ports = container.getPorts();
                if (ports != null) {
                    for (ContainerPort p : ports) {
                        if (p.getPublicPort() != null && p.getPublicPort() == port) {
                            return true;
                        }
                    }
                }
                return false;
            })
            .collect(Collectors.toList());
        
        // Find the consul agent container that owns this port
        Container consulAgent = containers.stream()
            .filter(c -> {
                String[] names = c.getNames();
                if (names != null) {
                    for (String name : names) {
                        if (name.contains("consul-agent-" + module.getModuleName())) {
                            return true;
                        }
                    }
                }
                return false;
            })
            .findFirst()
            .orElse(null);
        
        if (consulAgent != null) {
            // Extract instance suffix from consul agent name
            String consulAgentName = consulAgent.getNames()[0];
            consulAgentName = consulAgentName.startsWith("/") ? consulAgentName.substring(1) : consulAgentName;
            
            // Determine instance suffix (e.g., "-2", "-3", or empty for first instance)
            String suffix = "";
            if (consulAgentName.matches(".*-\\d+$")) {
                suffix = consulAgentName.substring(consulAgentName.lastIndexOf("-"));
            }
            
            // Remove containers in order
            removeContainerIfExists(module.getModuleName() + "-registrar" + suffix);
            removeContainerIfExists(module.getContainerName() + suffix);
            removeContainerIfExists(consulAgentName);
        } else {
            LOG.warnf("Could not find consul agent container for port %d", port);
        }
    }
    
    /**
     * Clean up module instance by pattern matching
     */
    private void cleanupModuleInstanceByPattern(PipelineModule module, String moduleId) {
        LOG.infof("Cleaning up module instance by pattern: %s (moduleId: %s)", module.getModuleName(), moduleId);
        
        // This is a fallback - try to find containers that might belong to this instance
        // This is less precise but better than nothing
        List<Container> moduleContainers = dockerClient.listContainersCmd()
            .withShowAll(true)
            .exec().stream()
            .filter(container -> {
                String[] names = container.getNames();
                if (names != null) {
                    for (String name : names) {
                        String cleanName = name.startsWith("/") ? name.substring(1) : name;
                        // Look for containers that might belong to this module
                        if (cleanName.contains(module.getModuleName())) {
                            return true;
                        }
                    }
                }
                return false;
            })
            .collect(Collectors.toList());
        
        // Just log what we found - don't remove without being certain
        LOG.infof("Found %d potential containers for module %s", moduleContainers.size(), module.getModuleName());
        for (Container c : moduleContainers) {
            LOG.debugf("  - %s", Arrays.toString(c.getNames()));
        }
    }
    
    /**
     * Stops and removes a specific module instance
     */
    public void stopModuleInstance(PipelineModule module, int instanceNumber) {
        LOG.infof("Stopping module instance: %s (instance %d)", module.getModuleName(), instanceNumber);
        
        // Fire undeploying event
        moduleLifecycleEvent.fire(new ModuleLifecycleEvent(
            module.getModuleName(),
            ModuleLifecycleEvent.EventType.UNDEPLOYING,
            String.format("Stopping module instance %d", instanceNumber)
        ));
        
        // Find the specific instance's module ID for deregistration
        String moduleIdPattern = module.getModuleName() + "-module-";
        
        // Deregister the specific instance from Consul
        moduleRegistry.listRegisteredModules()
            .onItem().transformToUni(modules -> {
                // Find modules matching this instance
                Optional<GlobalModuleRegistryService.ModuleRegistration> instanceReg = modules.stream()
                    .filter(m -> {
                        // Check if this is the right instance based on port or container metadata
                        int port = m.port();
                        int expectedPort = BASE_MODULE_PORT + instanceNumber - 1;
                        return m.moduleName().equals(module.getModuleName() + "-module") && port == expectedPort;
                    })
                    .findFirst();
                    
                if (instanceReg.isPresent()) {
                    LOG.infof("Deregistering module instance: %s", instanceReg.get().moduleId());
                    return moduleRegistry.deregisterModule(instanceReg.get().moduleId())
                        .map(success -> {
                            if (success) {
                                LOG.infof("Successfully deregistered module instance: %s", instanceReg.get().moduleId());
                                moduleLifecycleEvent.fire(new ModuleLifecycleEvent(
                                    module.getModuleName(),
                                    ModuleLifecycleEvent.EventType.DEREGISTERED,
                                    String.format("Module instance %d deregistered", instanceNumber)
                                ));
                            }
                            return success;
                        });
                } else {
                    LOG.warnf("No registration found for module instance %s-%d", module.getModuleName(), instanceNumber);
                    return Uni.createFrom().item(false);
                }
            })
            .subscribe().with(
                success -> LOG.debugf("Instance deregistration completed: %s", success),
                failure -> LOG.errorf(failure, "Failed to deregister instance")
            );
        
        // Clean up the specific instance's containers
        cleanupModuleInstance(module, instanceNumber);
        
        // Fire undeployed event
        moduleLifecycleEvent.fire(new ModuleLifecycleEvent(
            module.getModuleName(),
            ModuleLifecycleEvent.EventType.UNDEPLOYED,
            String.format("Module instance %d stopped successfully", instanceNumber)
        ));
        
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
     * Stops a deployed module and its sidecars
     */
    public void stopModule(PipelineModule module) {
        LOG.infof("Stopping module: %s", module.getModuleName());
        
        // Notify via SSE that undeployment is starting
        try {
            var sseInstance = Arc.container().instance(ModuleDeploymentSSE.class);
            if (sseInstance.isAvailable()) {
                sseInstance.get().notifyModuleUndeploying(module.getModuleName());
            }
        } catch (Exception e) {
            LOG.debugf("Failed to send SSE notification: %s", e.getMessage());
        }
        
        // Fire undeploying event
        moduleLifecycleEvent.fire(new ModuleLifecycleEvent(
            module.getModuleName(),
            ModuleLifecycleEvent.EventType.UNDEPLOYING,
            "Starting module undeployment"
        ));
        
        // Deregister from Consul BEFORE stopping containers
        deregisterModuleFromConsul(module.getModuleName());
        
        // Now clean up containers
        cleanupModule(module);
        
        // Fire undeployed event
        moduleLifecycleEvent.fire(new ModuleLifecycleEvent(
            module.getModuleName(),
            ModuleLifecycleEvent.EventType.UNDEPLOYED,
            "Module undeployed successfully"
        ));
        
        // Notify via SSE that undeployment is complete
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
     * Cleans up a specific module instance
     */
    private void cleanupModuleInstance(PipelineModule module, int instanceNumber) {
        LOG.infof("Cleaning up module instance: %s (instance %d)", module.getModuleName(), instanceNumber);
        
        // Determine container names for this instance
        String registrarName = instanceNumber == 1 ? 
            module.getModuleName() + "-registrar" : 
            module.getModuleName() + "-registrar-" + instanceNumber;
        String moduleName = instanceNumber == 1 ? 
            module.getContainerName() : 
            module.getContainerName() + "-" + instanceNumber;
        String sidecarName = instanceNumber == 1 ? 
            module.getSidecarName() : 
            module.getSidecarName() + "-" + instanceNumber;
        
        // Remove containers in order
        removeContainerIfExists(registrarName);
        removeContainerIfExists(moduleName);
        removeContainerIfExists(sidecarName);
    }
    
    /**
     * Cleans up module resources
     */
    private void cleanupModule(PipelineModule module) {
        // Stop and remove containers in order - module first, then sidecars
        LOG.infof("Cleaning up ALL containers for module: %s", module.getModuleName());
        
        // First, find all containers that belong to this module
        List<Container> moduleContainers = dockerClient.listContainersCmd()
            .withShowAll(true)
            .exec().stream()
            .filter(container -> {
                String[] names = container.getNames();
                if (names != null) {
                    for (String name : names) {
                        // Remove leading slash from container name
                        String cleanName = name.startsWith("/") ? name.substring(1) : name;
                        // Check if this container belongs to our module
                        if (cleanName.startsWith(module.getModuleName() + "-registrar") ||
                            cleanName.equals(module.getContainerName()) || 
                            cleanName.startsWith(module.getContainerName() + "-") ||
                            cleanName.equals(module.getSidecarName()) ||
                            cleanName.startsWith(module.getSidecarName() + "-")) {
                            return true;
                        }
                    }
                }
                return false;
            })
            .collect(Collectors.toList());
        
        LOG.infof("Found %d containers to clean up for module %s", moduleContainers.size(), module.getModuleName());
        
        // Group containers by type and instance number for proper ordering
        Map<String, List<Container>> containersByType = new HashMap<>();
        containersByType.put("registrar", new java.util.ArrayList<>());
        containersByType.put("module", new java.util.ArrayList<>());
        containersByType.put("sidecar", new java.util.ArrayList<>());
        
        for (Container container : moduleContainers) {
            String containerName = container.getNames()[0];
            containerName = containerName.startsWith("/") ? containerName.substring(1) : containerName;
            
            if (containerName.contains("-registrar")) {
                containersByType.get("registrar").add(container);
            } else if (containerName.contains("consul-agent-")) {
                containersByType.get("sidecar").add(container);
            } else {
                containersByType.get("module").add(container);
            }
        }
        
        // Remove in order: registrars first, then modules, then sidecars
        for (Container container : containersByType.get("registrar")) {
            removeContainer(container);
        }
        for (Container container : containersByType.get("module")) {
            removeContainer(container);
        }
        for (Container container : containersByType.get("sidecar")) {
            removeContainer(container);
        }
        
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
        String networkName,
        int instanceNumber,
        int allocatedPort
    ) {
        public ModuleDeploymentResult(boolean success, String message) {
            this(success, message, null, null, null, null, 1, 0);
        }
        
        public ModuleDeploymentResult(boolean success, String message, 
                String moduleContainerId, String consulContainerId, 
                String otelContainerId, String networkName) {
            this(success, message, moduleContainerId, consulContainerId, 
                    otelContainerId, networkName, 1, 0);
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
    
    /**
     * Deregisters module from Consul
     */
    private void deregisterModuleFromConsul(String moduleName) {
        try {
            // Use the module registry to find and deregister all instances
            moduleRegistry.listRegisteredModules()
                .subscribe().with(registrations -> {
                    // Find all registrations for this module
                    registrations.stream()
                        .filter(reg -> reg.moduleName() != null && 
                                reg.moduleName().toLowerCase().contains(moduleName.toLowerCase()))
                        .forEach(reg -> {
                            LOG.infof("Deregistering module instance: %s", reg.moduleId());
                            moduleRegistry.deregisterModule(reg.moduleId())
                                .subscribe().with(
                                    success -> {
                                        if (success) {
                                            LOG.infof("Successfully deregistered module instance: %s", reg.moduleId());
                                            // Fire deregistered event
                                            moduleLifecycleEvent.fire(new ModuleLifecycleEvent(
                                                moduleName,
                                                ModuleLifecycleEvent.EventType.DEREGISTERED,
                                                "Module instance deregistered",
                                                reg.moduleId()
                                            ));
                                        } else {
                                            LOG.warnf("Failed to deregister module instance: %s", reg.moduleId());
                                        }
                                    },
                                    failure -> LOG.warnf("Error deregistering module instance %s: %s", 
                                        reg.moduleId(), failure.getMessage())
                                );
                        });
                }, failure -> {
                    LOG.warnf("Failed to list registered modules for deregistration: %s", failure.getMessage());
                });
        } catch (Exception e) {
            LOG.warnf("Error deregistering module from Consul: %s", e.getMessage());
        }
    }
    
    /**
     * Finds module containers that are running but not registered
     */
    public List<OrphanedModule> findOrphanedModules() {
        List<OrphanedModule> orphaned = new ArrayList<>();
        
        try {
            // Get all containers with pipeline.module label
            List<Container> containers = dockerClient.listContainersCmd()
                .withShowAll(true)  // Include stopped containers
                .withLabelFilter(Map.of("pipeline.type", "module"))
                .exec();
                
            // Get registered modules from the global registry
            Set<String> registeredModuleIds = new HashSet<>();
            globalModuleRegistry.listRegisteredModules()
                .subscribe().with(
                    modules -> {
                        modules.forEach(reg -> registeredModuleIds.add(reg.moduleId()));
                    },
                    failure -> LOG.warnf("Failed to get registered modules: %s", failure.getMessage())
                );
            
            // Find orphaned containers
            for (Container container : containers) {
                Map<String, String> labels = container.getLabels();
                if (labels != null) {
                    String moduleName = labels.get("pipeline.module.name");
                    String modulePort = labels.get("pipeline.port");
                    String instanceStr = labels.get("pipeline.instance");
                    
                    if (moduleName != null) {
                        boolean isRunning = "running".equals(container.getState());
                        boolean isRegistered = registeredModuleIds.contains(container.getId());
                        
                        // Only show running containers that aren't registered
                        if (isRunning && !isRegistered) {
                            orphaned.add(new OrphanedModule(
                                container.getId(),
                                container.getNames()[0].replaceFirst("^/", ""),
                                moduleName,
                                modulePort != null ? Integer.parseInt(modulePort) : 0,
                                instanceStr != null ? Integer.parseInt(instanceStr) : 1,
                                container.getState(),
                                container.getStatus(),
                                labels
                            ));
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOG.errorf("Failed to find orphaned modules: %s", e.getMessage(), e);
        }
        
        return orphaned;
    }
    
    /**
     * Attempts to register an orphaned module container
     */
    public boolean registerOrphanedModule(String containerId) {
        try {
            // Inspect the container to get its details
            InspectContainerResponse containerInfo = dockerClient.inspectContainerCmd(containerId).exec();
            
            if (containerInfo == null) {
                LOG.warnf("Container not found: %s", containerId);
                return false;
            }
            
            Map<String, String> labels = containerInfo.getConfig().getLabels();
            if (labels == null || !"module".equals(labels.get("pipeline.type"))) {
                LOG.warnf("Container is not a module: %s", containerId);
                return false;
            }
            
            String moduleName = labels.get("pipeline.module.name");
            String modulePort = labels.get("pipeline.port");
            
            if (moduleName == null || modulePort == null) {
                LOG.warnf("Container missing required labels: %s", containerId);
                return false;
            }
            
            // Create a new registration container to register this module
            String registrationScript = String.format("""
                #!/bin/sh
                echo 'Attempting to register orphaned module %s...'
                
                # Try to register the module with the engine
                if java -jar /deployments/pipeline-cli.jar register \
                    --module-host=%s \
                    --module-port=%s \
                    --engine-host=%s \
                    --engine-port=39001; then
                    echo 'Module registered successfully!'
                else
                    echo 'Registration failed'
                    exit 1
                fi
                """,
                moduleName,
                containerInfo.getName().replaceFirst("^/", ""),
                modulePort,
                hostIPDetector.detectHostIP()
            );
            
            // Create a temporary registration container
            CreateContainerResponse registrar = dockerClient.createContainerCmd("pipeline/" + moduleName + "-module:latest")
                .withName(moduleName + "-orphan-registrar-" + System.currentTimeMillis())
                .withHostConfig(HostConfig.newHostConfig()
                    .withNetworkMode("host")  // Use host network to reach the module
                    .withAutoRemove(true)     // Remove after completion
                )
                .withEntrypoint("/bin/sh")
                .withCmd("-c", registrationScript)
                .withLabels(Map.of(
                    "pipeline.module", moduleName,
                    "pipeline.type", "orphan-registrar"
                ))
                .exec();
                
            // Start the registration container
            dockerClient.startContainerCmd(registrar.getId()).exec();
            LOG.infof("Started orphan registration for module: %s", moduleName);
            
            return true;
        } catch (Exception e) {
            LOG.errorf("Failed to register orphaned module: %s", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Data class for orphaned module information
     */
    public record OrphanedModule(
        String containerId,
        String containerName,
        String moduleName,
        int port,
        int instance,
        String state,
        String status,
        Map<String, String> labels
    ) {}
}