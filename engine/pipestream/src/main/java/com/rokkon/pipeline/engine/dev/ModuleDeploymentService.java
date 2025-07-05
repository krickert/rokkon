package com.rokkon.pipeline.engine.dev;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.CreateNetworkResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.ConflictException;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.command.LogContainerCmd;
import com.github.dockerjava.api.async.ResultCallback;
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
    
    // All modules use the same internal port
    private static final int MODULE_INTERNAL_PORT = 39100;
    
    // External port range for dynamic allocation
    private static final int EXTERNAL_PORT_START = 39100;
    private static final int EXTERNAL_PORT_END = 39200;
    
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
        ExposedPort modulePort = ExposedPort.tcp(MODULE_INTERNAL_PORT);
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
                        "MODULE_PORT=" + MODULE_INTERNAL_PORT,
                        "ENGINE_HOST=" + hostIP,
                        "ENGINE_PORT=39001",  // Engine unified port
                        "CONSUL_HOST=localhost",  // Via shared network namespace
                        "CONSUL_PORT=8500",  // Consul HTTP API port
                        // Registration is handled by the sidecar, not needed here
                        "QUARKUS_HTTP_PORT=" + MODULE_INTERNAL_PORT,
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
                        "pipeline.port", String.valueOf(MODULE_INTERNAL_PORT),
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
        LOG.infof("Started module container: %s with internal port %d", containerName, MODULE_INTERNAL_PORT);
        
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
            MODULE_INTERNAL_PORT,
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
            // Get all containers with pipeline labels (module, registrar, sidecar)
            List<Container> allContainers = dockerClient.listContainersCmd()
                .withShowAll(false)  // Only running containers
                .exec();
                
            // Filter for containers that have pipeline labels
            List<Container> containers = allContainers.stream()
                .filter(c -> {
                    Map<String, String> labels = c.getLabels();
                    return labels != null && 
                           (labels.containsKey("pipeline.module") || 
                            labels.containsKey("pipeline.module.name") ||
                            labels.containsKey("pipeline.type"));
                })
                .collect(Collectors.toList());
                
            // Get registered modules from the global registry
            Set<String> registeredModuleNames = new HashSet<>();
            Map<String, Set<Integer>> registeredModuleInstances = new HashMap<>();
            try {
                Set<GlobalModuleRegistryService.ModuleRegistration> registrations = 
                    moduleRegistry.listRegisteredModules().await().atMost(Duration.ofSeconds(5));
                registrations.forEach(reg -> {
                    // Extract module name from moduleId (e.g., "test-module-abc123" -> "test")
                    String moduleId = reg.moduleId();
                    String moduleName = reg.moduleName();
                    if (moduleName != null) {
                        registeredModuleNames.add(moduleName);
                        // Track instances for each module
                        registeredModuleInstances.computeIfAbsent(moduleName, k -> new HashSet<>());
                        // Try to extract instance number from moduleId
                        if (moduleId.matches(".*-\\d+$")) {
                            String[] parts = moduleId.split("-");
                            try {
                                int instance = Integer.parseInt(parts[parts.length - 1]);
                                registeredModuleInstances.get(moduleName).add(instance);
                            } catch (NumberFormatException e) {
                                // Ignore
                            }
                        }
                    }
                });
            } catch (Exception e) {
                LOG.warnf("Failed to get registered modules: %s", e.getMessage());
            }
            
            // Find orphaned containers
            for (Container container : containers) {
                Map<String, String> labels = container.getLabels();
                if (labels != null) {
                    // Support both old and new label formats
                    String moduleName = labels.get("pipeline.module.name");
                    if (moduleName == null) {
                        moduleName = labels.get("pipeline.module");
                    }
                    
                    String modulePort = labels.get("pipeline.port");
                    String instanceStr = labels.get("pipeline.instance");
                    
                    if (moduleName != null) {
                        boolean isRunning = "running".equals(container.getState());
                        String containerName = container.getNames()[0].replaceFirst("^/", "");
                        
                        // Check if this specific module instance is registered
                        // Only show module containers (not sidecars/registrars) that aren't registered
                        String containerType = labels.get("pipeline.type");
                        boolean isModuleContainer = "module".equals(containerType);
                        
                        // For module containers, check if they're registered
                        boolean isRegistered = false;
                        if (isModuleContainer && registeredModuleNames.contains(moduleName)) {
                            // Check if this specific instance is registered
                            int thisInstance = 1;
                            if (instanceStr != null) {
                                thisInstance = Integer.parseInt(instanceStr);
                            } else {
                                // Try to extract from container name
                                if (containerName.matches(".*-\\d+$")) {
                                    String[] parts = containerName.split("-");
                                    try {
                                        thisInstance = Integer.parseInt(parts[parts.length - 1]);
                                    } catch (NumberFormatException e) {
                                        // Keep default
                                    }
                                }
                            }
                            Set<Integer> instances = registeredModuleInstances.get(moduleName);
                            isRegistered = instances != null && (instances.contains(thisInstance) || instances.isEmpty());
                        }
                        
                        // Only show module containers that are running but not registered
                        if (isRunning && isModuleContainer && !isRegistered) {
                            // First check if we can get the actual port from Docker
                            int port = 0;
                            
                            // Try to get from label first
                            if (modulePort != null) {
                                port = Integer.parseInt(modulePort);
                            } else {
                                // Try to get port from container's port bindings
                                ContainerPort[] ports = container.getPorts();
                                if (ports != null && ports.length > 0) {
                                    for (ContainerPort p : ports) {
                                        // Look for the module's unified port (usually 39xxx)
                                        if (p.getPublicPort() != null && 
                                            p.getPublicPort() >= 39000 && 
                                            p.getPublicPort() < 40000) {
                                            port = p.getPublicPort();
                                            LOG.debugf("Found port %d for container %s from port bindings", port, containerName);
                                            break;
                                        }
                                    }
                                }
                                
                                // If still no port, try to call the module's gRPC service
                                if (port == 0) {
                                    try {
                                        // Use the container's internal port (all modules use the same)
                                        PipelineModule module = PipelineModule.fromName(moduleName);
                                        int internalPort = MODULE_INTERNAL_PORT;
                                        
                                        // Try to connect to localhost with various port mappings
                                        // This would need actual gRPC client implementation
                                        LOG.debugf("Would try to call GetServiceRegistration on %s at port %d", 
                                                  containerName, internalPort);
                                        
                                        // For now, use the default port
                                        port = internalPort;
                                    } catch (Exception e) {
                                        LOG.debugf("Could not determine port for module %s: %s", moduleName, e.getMessage());
                                    }
                                }
                            }
                            
                            // Determine instance from container name
                            int instance = 1;
                            if (instanceStr != null) {
                                instance = Integer.parseInt(instanceStr);
                            } else {
                                // Use the containerName already defined earlier
                                if (containerName.matches(".*-\\d+$")) {
                                    String[] parts = containerName.split("-");
                                    try {
                                        instance = Integer.parseInt(parts[parts.length - 1]);
                                    } catch (NumberFormatException e) {
                                        // Keep default
                                    }
                                }
                            }
                            
                            // Re-determine container type if needed (already declared above)
                            if (containerType == null || containerType.isEmpty()) {
                                // Try to infer from container name
                                String containerNameLower = containerName.toLowerCase();
                                if (containerNameLower.contains("registrar")) {
                                    containerType = "registrar";
                                } else if (containerNameLower.contains("sidecar") || containerNameLower.contains("consul")) {
                                    containerType = "sidecar";
                                } else {
                                    containerType = "module";
                                }
                            }
                            
                            orphaned.add(new OrphanedModule(
                                container.getId(),
                                container.getNames()[0].replaceFirst("^/", ""),
                                moduleName,
                                containerType,
                                port,
                                instance,
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
     * Redeploys an orphaned module by cleaning it up and deploying fresh
     */
    public ModuleDeploymentResult redeployOrphanedModule(String containerId) {
        LOG.infof("Attempting to redeploy orphaned module with container ID: %s", containerId);
        try {
            // Inspect the container to get its details
            InspectContainerResponse containerInfo = dockerClient.inspectContainerCmd(containerId).exec();
            
            if (containerInfo == null) {
                LOG.warnf("Container not found: %s", containerId);
                return new ModuleDeploymentResult(false, "Container not found");
            }
            
            Map<String, String> labels = containerInfo.getConfig().getLabels();
            if (labels == null) {
                LOG.warnf("Container has no labels: %s", containerId);
                return new ModuleDeploymentResult(false, "Container has no labels");
            }
            
            String containerType = labels.get("pipeline.type");
            if (!"module".equals(containerType)) {
                LOG.warnf("Container is not a module (type=%s): %s", containerType, containerId);
                return new ModuleDeploymentResult(false, "Container is not a module");
            }
            
            String moduleName = labels.get("pipeline.module.name");
            if (moduleName == null) {
                moduleName = labels.get("pipeline.module");
            }
            
            if (moduleName == null) {
                LOG.warnf("Container missing module name label: %s", containerId);
                return new ModuleDeploymentResult(false, "Cannot determine module name");
            }
            
            // Find the PipelineModule enum for this module
            PipelineModule module;
            try {
                module = PipelineModule.fromName(moduleName);
            } catch (Exception e) {
                LOG.errorf("Unknown module type: %s", moduleName);
                return new ModuleDeploymentResult(false, "Unknown module type: " + moduleName);
            }
            
            LOG.infof("Redeploying module %s - will clean up orphaned containers and deploy fresh", moduleName);
            
            // Clean up all containers for this module (module, sidecars, registrars)
            cleanupOrphanedModuleContainers(moduleName);
            
            // Wait a bit for cleanup to complete
            Thread.sleep(1000);
            
            // Now deploy fresh using the normal deployment process
            return deployModule(module);
            
        } catch (Exception e) {
            LOG.errorf("Failed to redeploy orphaned module: %s", e.getMessage(), e);
            return new ModuleDeploymentResult(false, "Failed to redeploy: " + e.getMessage());
        }
    }
    
    /**
     * Completely cleans up a module - removes all containers and Consul registrations
     * This is useful for cleaning up orphaned modules or doing a full reset
     */
    public void cleanupModuleCompletely(String moduleName) {
        LOG.infof("Performing complete cleanup for module: %s", moduleName);
        
        // First, deregister from Consul
        deregisterModuleFromConsul(moduleName);
        
        // Then clean up all containers
        cleanupOrphanedModuleContainers(moduleName);
        
        LOG.infof("Complete cleanup finished for module: %s", moduleName);
    }
    
    /**
     * Cleans up all containers related to an orphaned module
     */
    private void cleanupOrphanedModuleContainers(String moduleName) {
        LOG.infof("Cleaning up all containers for orphaned module: %s", moduleName);
        
        // Find all containers with this module name
        List<Container> containers = dockerClient.listContainersCmd()
            .withShowAll(true)
            .exec();
            
        for (Container container : containers) {
            try {
                // Check container name patterns
                String containerName = container.getNames()[0].replaceFirst("^/", "");
                boolean isModuleContainer = false;
                
                // Check various naming patterns
                if (containerName.equals(moduleName + "-module-app") ||
                    containerName.startsWith(moduleName + "-module-app-") ||
                    containerName.equals("consul-agent-" + moduleName) ||
                    containerName.startsWith("consul-agent-" + moduleName + "-") ||
                    containerName.contains(moduleName + "-registrar") ||
                    containerName.contains(moduleName + "-orphan-registrar")) {
                    isModuleContainer = true;
                }
                
                // Also check labels
                if (!isModuleContainer && container.getLabels() != null) {
                    String moduleLabel = container.getLabels().get("pipeline.module");
                    if (moduleName.equals(moduleLabel)) {
                        isModuleContainer = true;
                    }
                }
                
                if (isModuleContainer) {
                    LOG.infof("Removing container: %s", containerName);
                    try {
                        dockerClient.stopContainerCmd(container.getId()).withTimeout(5).exec();
                    } catch (Exception e) {
                        // Container might already be stopped
                    }
                    dockerClient.removeContainerCmd(container.getId()).withForce(true).exec();
                }
            } catch (Exception e) {
                LOG.warnf("Failed to remove container: %s", e.getMessage());
            }
        }
    }
    
    /**
     * Attempts to register an orphaned module container
     */
    public boolean registerOrphanedModule(String containerId) {
        LOG.infof("Attempting to register orphaned module with container ID: %s", containerId);
        try {
            // Inspect the container to get its details
            InspectContainerResponse containerInfo = dockerClient.inspectContainerCmd(containerId).exec();
            
            if (containerInfo == null) {
                LOG.warnf("Container not found: %s", containerId);
                return false;
            }
            
            Map<String, String> labels = containerInfo.getConfig().getLabels();
            if (labels == null) {
                LOG.warnf("Container has no labels: %s", containerId);
                return false;
            }
            
            String containerType = labels.get("pipeline.type");
            LOG.infof("Container type: %s, labels: %s", containerType, labels);
            
            if (!"module".equals(containerType)) {
                LOG.warnf("Container is not a module (type=%s): %s", containerType, containerId);
                return false;
            }
            
            String moduleName = labels.get("pipeline.module.name");
            if (moduleName == null) {
                moduleName = labels.get("pipeline.module");
            }
            String instanceStr = labels.get("pipeline.instance");
            int instanceNumber = instanceStr != null ? Integer.parseInt(instanceStr) : 1;
            
            if (moduleName == null) {
                LOG.warnf("Container missing module name label: %s", containerId);
                return false;
            }
            
            // Get the internal port from PipelineModule
            int internalPort = 0;
            int actualHostPort = 0;
            String registrationHost = "";
            
            try {
                PipelineModule module = PipelineModule.fromName(moduleName);
                internalPort = MODULE_INTERNAL_PORT;
                LOG.infof("Module %s uses standard internal port %d", moduleName, internalPort);
            } catch (Exception e) {
                LOG.errorf("Module %s not found in PipelineModule enum", moduleName);
                return false;
            }
            
            // Since the module shares network with consul sidecar, we need to find the mapped port
            // from the consul sidecar container (which has the port mappings)
            
            // Find the consul sidecar for this module
            String consulSidecarName = instanceNumber == 1 
                ? "consul-agent-" + moduleName 
                : "consul-agent-" + moduleName + "-" + instanceNumber;
            
            // Verify the consul sidecar exists
            List<Container> consulSidecars = dockerClient.listContainersCmd()
                .withNameFilter(List.of(consulSidecarName))
                .exec();
            
            Container consulSidecar = null;
            if (!consulSidecars.isEmpty()) {
                consulSidecar = consulSidecars.get(0);
            } else {
                LOG.errorf("Consul sidecar not found for orphaned module: %s", consulSidecarName);
                LOG.errorf("The orphaned module %s has lost its consul sidecar and cannot be re-registered. " +
                          "Please clean up this container manually using 'docker rm -f %s' or the undeploy button.", 
                          moduleName, containerInfo.getName());
                
                // For now, we'll return false. In the future, we could offer to clean up the orphaned container
                return false;
            }
            
            // For orphaned modules, we have a challenge: the module shares network with consul sidecar,
            // and the consul sidecar has the port mappings. We can't easily change port mappings
            // without recreating both containers. 
            
            // First, let's check what port mapping exists on the consul sidecar
            int existingHostPort = 0;
            if (consulSidecar != null) {
                ContainerPort[] ports = consulSidecar.getPorts();
                if (ports != null) {
                    for (ContainerPort port : ports) {
                        if (port.getPrivatePort() != null && port.getPrivatePort() == internalPort
                            && port.getPublicPort() != null) {
                            existingHostPort = port.getPublicPort();
                            LOG.infof("Found existing port mapping %d->%d on consul sidecar", 
                                     existingHostPort, internalPort);
                            break;
                        }
                    }
                }
            }
            
            // Check if there's already a module registered at this port
            if (existingHostPort > 0) {
                // Check if this port conflicts with an already registered module
                boolean portConflict = false;
                String conflictingModule = null;
                
                try {
                    // Check all registered modules to see if any are using this port
                    var registeredModules = moduleRegistry.listRegisteredModules().await().indefinitely();
                    for (var registration : registeredModules) {
                        if (registration.port() == existingHostPort) {
                            // Check if it's the same module (re-registering itself is OK)
                            if (!registration.moduleName().equals(moduleName)) {
                                portConflict = true;
                                conflictingModule = registration.moduleName();
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    LOG.errorf("Failed to check for port conflicts: %s", e.getMessage());
                    return false;
                }
                
                if (portConflict) {
                    LOG.errorf("Cannot re-register orphaned module %s: port %d is already in use by module %s. " +
                              "Please deregister the conflicting module first or clean up the orphaned containers.",
                              moduleName, existingHostPort, conflictingModule);
                    return false;
                }
                
                actualHostPort = existingHostPort;
                LOG.infof("No port conflicts found. Will use existing port mapping: %d", actualHostPort);
            } else {
                // No existing port mapping found - this shouldn't happen for orphaned modules
                LOG.errorf("No port mapping found on consul sidecar for orphaned module %s", moduleName);
                return false;
            }
            
            
            // Determine registration host based on network mode
            String hostIP = hostIPDetector.detectHostIP();
            registrationHost = hostIP;
            
            LOG.infof("Registering module %s with internal port %d mapped to host port %d", 
                     moduleName, internalPort, actualHostPort);
            
            // Use the existing registration sidecar pattern
            String registrarName = moduleName + "-orphan-registrar-" + System.currentTimeMillis();
            
            // Get the module's Docker image (prefer from container info)
            String moduleImage = containerInfo.getConfig().getImage();
            if (moduleImage == null || moduleImage.isEmpty()) {
                moduleImage = "pipeline/" + moduleName + "-module:latest";
            }
            
            // Create the registration sidecar using the same pattern as normal deployment
            return createAndStartRegistrationSidecar(
                moduleName, 
                moduleImage,
                registrarName,
                consulSidecarName,
                instanceNumber,
                registrationHost,
                actualHostPort  // Use the actual mapped host port, not internal port
            );
            
        } catch (Exception e) {
            LOG.errorf("Failed to register orphaned module: %s", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Creates a consul sidecar for an orphaned module with specific port mapping
     */
    private String createConsulSidecarForOrphanedModule(PipelineModule module, int hostPort, 
            int instanceNumber, String moduleContainerId) {
        String sidecarName = instanceNumber == 1 
            ? "consul-agent-" + module.getModuleName() 
            : "consul-agent-" + module.getModuleName() + "-" + instanceNumber;
        
        try {
            // Remove existing container if it exists
            removeContainerIfExists(sidecarName);
            
            // Create the consul sidecar that shares network with the module
            // Note: We cannot publish ports when using container network mode
            // The port mapping should already exist on the module container
            HostConfig hostConfig = HostConfig.newHostConfig()
                .withNetworkMode("container:" + moduleContainerId);  // Share network with module
            
            // Environment variables for Consul
            List<String> env = List.of(
                "CONSUL_BIND_INTERFACE=eth0",
                "CONSUL_CLIENT_INTERFACE=eth0",
                "CONSUL_HTTP_ADDR=http://127.0.0.1:8500"
            );
            
            // Consul command
            String[] cmd = new String[]{
                "consul", "agent",
                "-retry-join", "consul-server",
                "-client", "0.0.0.0",
                "-bind", "0.0.0.0",
                "-ui=false",
                "-node=" + sidecarName,
                "-datacenter=rokkon-dev"
            };
            
            CreateContainerResponse container = dockerClient.createContainerCmd("hashicorp/consul:" + consulVersion)
                .withName(sidecarName)
                .withHostConfig(hostConfig)
                .withEnv(env)
                .withCmd(cmd)
                .withLabels(Map.of(
                    "pipeline.module", module.getModuleName(),
                    "pipeline.module.name", module.getModuleName(),
                    "pipeline.type", "consul-sidecar",
                    "pipeline.sidecar.for", module.getModuleName(),
                    "pipeline.instance", String.valueOf(instanceNumber),
                    "pipeline.port.mapping", hostPort + "->" + MODULE_INTERNAL_PORT
                ))
                .exec();
                
            // Start the container
            dockerClient.startContainerCmd(container.getId()).exec();
            LOG.infof("Started orphaned module consul sidecar: %s with port %d->%d", 
                     sidecarName, hostPort, MODULE_INTERNAL_PORT);
            
            return container.getId();
            
        } catch (Exception e) {
            LOG.errorf("Failed to create consul sidecar for orphaned module: %s", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Creates and starts a registration sidecar for orphaned modules
     */
    private boolean createAndStartRegistrationSidecar(String moduleName, String moduleImage, 
            String registrarName, String consulSidecarName, int instanceNumber, 
            String registrationHost, int allocatedPort) {
        try {
            String hostIP = hostIPDetector.detectHostIP();
            
            // All modules use the same internal port
            int internalPort = MODULE_INTERNAL_PORT;
            
            // Create registration script matching the normal deployment pattern
            String moduleServiceName = moduleName + "-module" + (instanceNumber > 1 ? "-" + instanceNumber : "");
            String registrationScript = String.format("""
                #!/bin/sh
                echo 'Waiting for module to be ready...'
                sleep 15
                
                echo 'Attempting to register orphaned module %s (instance %d) with engine...'
                echo 'Registration host: %s'
                echo 'Registration port: %d (external/host port)'
                echo 'Module port: %d (internal port)'
                
                # Try to register the module
                # Note: module-port is the internal port since we share network with module
                # registration-port is the external/host port for engine to connect back
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
                moduleServiceName,
                instanceNumber,
                registrationHost,
                allocatedPort,
                internalPort,
                internalPort,  // Module's internal port for communication
                hostIP,
                registrationHost,
                allocatedPort   // External/host port for engine to connect back
            );
            
            // Remove any existing registrar with same name
            removeContainerIfExists(registrarName);
            
            // Create the registration sidecar sharing network with Consul sidecar
            CreateContainerResponse registrar = dockerClient.createContainerCmd(moduleImage)
                .withName(registrarName)
                .withHostConfig(HostConfig.newHostConfig()
                    .withNetworkMode("container:" + consulSidecarName)  // Share network with Consul sidecar
                )
                .withEntrypoint("/bin/sh")
                .withCmd("-c", registrationScript)
                .withLabels(Map.of(
                    "pipeline.module", moduleName,
                    "pipeline.module.name", moduleName,
                    "pipeline.type", "registration-sidecar",
                    "pipeline.sidecar.for", moduleName,
                    "pipeline.instance", String.valueOf(instanceNumber),
                    "pipeline.orphan.registrar", "true"
                ))
                .exec();
                
            // Start the registration container
            dockerClient.startContainerCmd(registrar.getId()).exec();
            LOG.infof("Started registration sidecar for orphaned module: %s", registrarName);
            
            return true;
        } catch (Exception e) {
            LOG.errorf("Failed to create registration sidecar: %s", e.getMessage(), e);
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
        String containerType,
        int port,
        int instance,
        String state,
        String status,
        Map<String, String> labels
    ) {}
    
    /**
     * Scale up a module by deploying another instance
     */
    public ModuleDeploymentResult scaleUpModule(PipelineModule module) {
        // Find the highest instance number currently running
        int nextInstance = 1;
        try {
            List<Container> containers = dockerClient.listContainersCmd()
                .withNameFilter(List.of(module.getContainerName()))
                .exec();
                
            for (Container container : containers) {
                String name = container.getNames()[0].substring(1); // Remove leading /
                if (name.matches(module.getContainerName() + "-\\d+")) {
                    String instanceStr = name.substring(module.getContainerName().length() + 1);
                    try {
                        int instance = Integer.parseInt(instanceStr);
                        nextInstance = Math.max(nextInstance, instance + 1);
                    } catch (NumberFormatException e) {
                        // Ignore
                    }
                }
            }
        } catch (Exception e) {
            LOG.warnf("Error finding next instance number: %s", e.getMessage());
        }
        
        // Also check the instance count map
        Integer tracked = moduleInstanceCounts.get(module.getModuleName());
        if (tracked != null && tracked >= nextInstance) {
            nextInstance = tracked + 1;
        }
        
        return deployModuleInstance(module, nextInstance);
    }
    
    /**
     * Get container logs
     */
    public List<String> getContainerLogs(String containerName, int lines) {
        try {
            // Find the container
            List<Container> containers = dockerClient.listContainersCmd()
                .withNameFilter(List.of(containerName))
                .exec();
                
            if (containers.isEmpty()) {
                return List.of("Container not found: " + containerName);
            }
            
            // Get logs from the first matching container
            String containerId = containers.get(0).getId();
            LogContainerCmd logCmd = dockerClient.logContainerCmd(containerId)
                .withStdOut(true)
                .withStdErr(true)
                .withTail(lines);
                
            // Collect logs in a frame consumer
            final List<String> logLines = new ArrayList<>();
            ResultCallback.Adapter<Frame> callback = new ResultCallback.Adapter<Frame>() {
                @Override
                public void onNext(Frame frame) {
                    logLines.add(new String(frame.getPayload()).trim());
                }
            };
            
            logCmd.exec(callback);
            
            // Wait a bit for logs to be collected
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            try {
                callback.close();
            } catch (Exception e) {
                // Ignore
            }
                
            return logLines;
        } catch (Exception e) {
            LOG.errorf("Failed to get logs for container %s: %s", containerName, e.getMessage());
            return List.of("Failed to retrieve logs: " + e.getMessage());
        }
    }
}