# Pipeline Dev Mode Design - Zero Setup Development Environment

## Overview
This document outlines the complete design for a zero-setup development environment where `./gradlew quarkusDev` automatically starts all required infrastructure in Docker while keeping the engine local for hot reload capabilities.

## Goals
1. **Zero Setup**: Developers run one command and everything works
2. **Hot Reload**: Engine code changes apply instantly without restart
3. **Full Ecosystem**: All pipeline components available for testing
4. **Easy Module Management**: Start/stop modules on demand
5. **Production Parity**: Use the same service mesh patterns as production

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        Host Machine                           │
│                                                               │
│  ┌─────────────────────┐                                     │
│  │   Engine (Quarkus)   │                                     │
│  │   - Hot Reload       │──────connects to──────┐            │
│  │   - REST API :39000  │                       │            │
│  │   - gRPC :49000      │                       │            │
│  └─────────────────────┘                        │            │
│                                                  ▼            │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │                    Docker Environment                     │ │
│  │                                                           │ │
│  │  ┌──────────────────┐    ┌─────────────────────────┐    │ │
│  │  │  Consul Server   │    │  Engine Consul Sidecar  │    │ │
│  │  │    :8500         │◄───│      localhost:8501     │    │ │
│  │  └──────────────────┘    │   Advertises HOST_IP    │    │ │
│  │           ▲               └─────────────────────────┘    │ │
│  │           │                                               │ │
│  │  ┌────────┴────────┐    ┌──────────────┐                │ │
│  │  │ Module Sidecars │    │   Modules    │                │ │
│  │  │  - Echo         │────│  - Echo      │                │ │
│  │  │  - Test         │    │  - Test      │                │ │
│  │  └─────────────────┘    └──────────────┘                │ │
│  │                                                           │ │
│  └─────────────────────────────────────────────────────────┘ │
└───────────────────────────────────────────────────────────────┘
```

## Key Components

### 1. Engine Consul Sidecar
- Runs in Docker on port 8501 (to avoid conflicts with main Consul on 8500)
- Engine connects to `localhost:8501` instead of main Consul
- Sidecar advertises the host machine's IP so Docker containers can reach the engine
- Joins the main Consul cluster for service discovery

### 2. Host IP Detection
- Uses Quarkus Docker Client extension to detect the Docker bridge gateway IP
- This IP is what containers use to reach the host machine
- Injected as `ENGINE_HOST_IP` environment variable to the sidecar

### 3. Module Management
- Default modules (echo, test) start automatically
- Additional modules can be started/stopped via REST API or Dev UI
- Each module runs with its own Consul sidecar (existing pattern)

## Implementation Details

### Dependency Management
**IMPORTANT**: Dev mode dependencies are isolated to prevent classpath conflicts:
- Docker Client is added as `quarkusDev("io.quarkiverse.dockerclient:quarkus-docker-client:0.2.0")`
- This ensures it's ONLY available during `./gradlew quarkusDev` and not in production builds
- All dev mode code is protected with `@Profile("dev")` annotations
- Production builds will not include any Docker client dependencies or dev mode infrastructure

### PipelineModule Enum
```java
package com.rokkon.pipeline.engine.dev;

public enum PipelineModule {
    ECHO("echo", "pipeline/echo:latest", "Simple echo module", "1G"),
    TEST("test", "pipeline/test-module:latest", "Test module for pipeline testing", "1G"),
    PARSER("parser", "pipeline/parser:latest", "Document parser module", "1G"),
    CHUNKER("chunker", "pipeline/chunker:latest", "Text chunking module", "4G"),
    EMBEDDER("embedder", "pipeline/embedder:latest", "ML embedding module", "8G");
    
    private final String moduleName;
    private final String dockerImage;
    private final String description;
    private final String defaultMemory;
    
    PipelineModule(String moduleName, String dockerImage, String description, String defaultMemory) {
        this.moduleName = moduleName;
        this.dockerImage = dockerImage;
        this.description = description;
        this.defaultMemory = defaultMemory;
    }
    
    public String getModuleName() { return moduleName; }
    public String getDockerImage() { return dockerImage; }
    public String getDescription() { return description; }
    public String getDefaultMemory() { return defaultMemory; }
    
    public static PipelineModule fromName(String name) {
        for (PipelineModule module : values()) {
            if (module.moduleName.equalsIgnoreCase(name)) {
                return module;
            }
        }
        throw new IllegalArgumentException("Unknown module: " + name);
    }
}
```

### Dev Infrastructure Manager
```java
package com.rokkon.pipeline.engine.dev;

import io.quarkus.runtime.StartupEvent;
import io.quarkus.runtime.configuration.ProfileManager;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.WaitContainerResultCallback;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Network;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
@Profile("dev")
public class PipelineDevModeInfrastructure {
    
    private static final Logger LOG = Logger.getLogger(PipelineDevModeInfrastructure.class);
    private static final String PROJECT_NAME = "pipeline-dev";
    
    @Inject
    DockerClient dockerClient;
    
    private ComposeContainer devEnvironment;
    private String hostIP;
    
    @ConfigProperty(name = "pipeline.dev.auto-start", defaultValue = "true")
    boolean autoStart;
    
    @ConfigProperty(name = "pipeline.dev.default-modules", defaultValue = "ECHO,TEST")
    List<PipelineModule> defaultModules;
    
    @ConfigProperty(name = "pipeline.dev.compose-file", 
                    defaultValue = "docker/development/docker-compose.dev.yml")
    String composeFile;
    
    @PostConstruct
    void validateDockerAvailable() {
        try {
            dockerClient.pingCmd().exec();
            LOG.info("Docker is available and running");
        } catch (Exception e) {
            throw new IllegalStateException(
                "Docker is not available. Please ensure Docker is running.", e);
        }
    }
    
    void onStart(@Observes StartupEvent event) {
        if (!autoStart || !isDevProfile()) {
            return;
        }
        
        try {
            startDevInfrastructure();
        } catch (Exception e) {
            LOG.error("Failed to start dev infrastructure. Ensure Docker is running.", e);
            throw new RuntimeException(
                "Dev mode requires Docker. Please start Docker and try again.\n" +
                "To disable auto-start, set: pipeline.dev.auto-start=false", e);
        }
    }
    
    private boolean isDevProfile() {
        return ProfileManager.getActiveProfile().equals("dev");
    }
    
    private void startDevInfrastructure() {
        // Check if already running
        if (isInfrastructureRunning()) {
            LOG.info("Dev infrastructure already running");
            verifyHealthy();
            return;
        }
        
        LOG.info("Starting dev infrastructure...");
        
        // Detect host IP
        hostIP = detectHostIP();
        LOG.infof("Detected host IP: %s", hostIP);
        
        // Build services list
        List<String> services = buildServicesList();
        
        // Start infrastructure
        devEnvironment = new ComposeContainer(
            PROJECT_NAME,
            new File(composeFile))
            .withLocalCompose(true)
            .withEnv("ENGINE_HOST_IP", hostIP)
            .withEnv("COMPOSE_PROJECT_NAME", PROJECT_NAME)
            .withServices(services.toArray(new String[0]))
            .withExposedService("consul-server", 8500)
            .withExposedService("consul-agent-engine", 8500)
            .waitingFor("consul-server", 
                Wait.forHttp("/v1/status/leader")
                    .forPort(8500)
                    .forStatusCode(200))
            .withStartupTimeout(Duration.ofMinutes(3));
            
        devEnvironment.start();
        
        // Wait a bit for services to stabilize
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        LOG.info("Dev infrastructure started successfully");
        LOG.info("Consul UI: http://localhost:8500");
        LOG.info("Engine Dashboard: http://localhost:39000");
        LOG.info("Dev UI: http://localhost:39000/q/dev");
    }
    
    private boolean isInfrastructureRunning() {
        try {
            List<Container> containers = dockerClient.listContainersCmd()
                .withLabelFilter(Map.of("com.docker.compose.project", PROJECT_NAME))
                .exec();
            
            // Check if core services are running
            boolean hasConsul = containers.stream()
                .anyMatch(c -> Arrays.asList(c.getNames())
                    .contains("/consul-server-dev"));
            
            boolean hasEngineSidecar = containers.stream()
                .anyMatch(c -> Arrays.asList(c.getNames())
                    .contains("/consul-agent-engine-dev"));
                    
            return hasConsul && hasEngineSidecar;
        } catch (Exception e) {
            LOG.debug("Error checking infrastructure status", e);
            return false;
        }
    }
    
    private void verifyHealthy() {
        // Could add health checks here
        LOG.debug("Verifying infrastructure health...");
    }
    
    private String detectHostIP() {
        // First try host.docker.internal (works on Docker Desktop)
        try {
            String hostInternal = InetAddress.getByName("host.docker.internal").getHostAddress();
            LOG.infof("Using host.docker.internal: %s", hostInternal);
            return hostInternal;
        } catch (UnknownHostException e) {
            LOG.debug("host.docker.internal not available, falling back to bridge detection");
        }
        
        // Fall back to Docker bridge gateway detection
        try {
            Network bridge = dockerClient.inspectNetworkCmd()
                .withNetworkId("bridge")
                .exec();
            
            String gateway = bridge.getIpam().getConfig().get(0).getGateway();
            LOG.debugf("Detected Docker bridge gateway: %s", gateway);
            return gateway;
        } catch (Exception e) {
            LOG.warn("Failed to detect host IP from Docker, using fallback", e);
            return "172.17.0.1"; // Common default
        }
    }
    
    private List<String> buildServicesList() {
        List<String> services = new ArrayList<>();
        
        // Core infrastructure
        services.add("consul-server");
        services.add("seeder");
        services.add("consul-agent-engine");
        
        // Default modules with their sidecars
        for (PipelineModule module : defaultModules) {
            services.add("consul-agent-" + module.getModuleName());
            services.add(module.getModuleName() + "-module");
        }
        
        LOG.debugf("Starting services: %s", services);
        return services;
    }
    
    // Module management methods using Docker-Java API
    public void startModule(PipelineModule module) {
        LOG.infof("Starting module: %s", module.getModuleName());
        
        try {
            // Start the module's sidecar first
            String sidecarName = "consul-agent-" + module.getModuleName() + "-dev";
            startContainer(sidecarName);
            
            // Then start the module itself
            String moduleName = module.getModuleName() + "-module-dev";
            startContainer(moduleName);
            
            // Wait for health check
            waitForModuleHealth(module);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to start module: " + module.getModuleName(), e);
        }
    }
    
    public void stopModule(PipelineModule module) {
        LOG.infof("Stopping module: %s", module.getModuleName());
        
        try {
            // Stop module first
            String moduleName = module.getModuleName() + "-module-dev";
            stopContainer(moduleName);
            
            // Then stop its sidecar
            String sidecarName = "consul-agent-" + module.getModuleName() + "-dev";
            stopContainer(sidecarName);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to stop module: " + module.getModuleName(), e);
        }
    }
    
    private void startContainer(String containerName) {
        // Check if container exists but is stopped
        List<Container> containers = dockerClient.listContainersCmd()
            .withShowAll(true)
            .withNameFilter(List.of(containerName))
            .exec();
        
        if (!containers.isEmpty()) {
            Container container = containers.get(0);
            String containerId = container.getId();
            
            if (!"running".equals(container.getState())) {
                LOG.debugf("Starting existing container: %s", containerName);
                dockerClient.startContainerCmd(containerId).exec();
            } else {
                LOG.debugf("Container already running: %s", containerName);
            }
        } else {
            // Container doesn't exist - use compose via Docker API
            LOG.infof("Creating container via compose: %s", containerName);
            executeDockerComposeViaAPI("up", "-d", containerName.replace("-dev", ""));
        }
    }
    
    private void stopContainer(String containerName) {
        List<Container> containers = dockerClient.listContainersCmd()
            .withNameFilter(List.of(containerName))
            .exec();
        
        if (!containers.isEmpty()) {
            String containerId = containers.get(0).getId();
            LOG.debugf("Stopping container: %s", containerName);
            dockerClient.stopContainerCmd(containerId).exec();
        }
    }
    
    private void waitForModuleHealth(PipelineModule module) {
        // Wait for module to register with Consul
        int maxAttempts = 30;
        int attempt = 0;
        
        while (attempt < maxAttempts) {
            if (isModuleHealthy(module)) {
                LOG.infof("Module %s is healthy", module.getModuleName());
                return;
            }
            
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for module health", e);
            }
            
            attempt++;
        }
        
        throw new RuntimeException("Module " + module.getModuleName() + " failed to become healthy");
    }
    
    // Execute docker compose commands via Docker API
    private void executeDockerComposeViaAPI(String... args) {
        try {
            // Create command
            List<String> cmd = new ArrayList<>();
            cmd.add("compose");
            cmd.add("-f");
            cmd.add(composeFile);
            cmd.add("-p");
            cmd.add(PROJECT_NAME);
            cmd.addAll(Arrays.asList(args));
            
            // Use Docker API to run compose in a container
            String composeImage = "docker/compose:latest";
            
            CreateContainerResponse container = dockerClient.createContainerCmd(composeImage)
                .withCmd(cmd)
                .withHostConfig(HostConfig.newHostConfig()
                    .withBinds(
                        Bind.parse("/var/run/docker.sock:/var/run/docker.sock"),
                        Bind.parse(new File(composeFile).getAbsolutePath() + ":" + composeFile)
                    ))
                .withWorkingDir(new File(composeFile).getParent())
                .exec();
            
            dockerClient.startContainerCmd(container.getId()).exec();
            
            // Wait for completion
            WaitContainerResultCallback resultCallback = new WaitContainerResultCallback();
            dockerClient.waitContainerCmd(container.getId()).exec(resultCallback);
            int exitCode = resultCallback.awaitStatusCode();
            
            // Clean up
            dockerClient.removeContainerCmd(container.getId()).exec();
            
            if (exitCode != 0) {
                throw new RuntimeException("Docker compose command failed with exit code: " + exitCode);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute docker compose command", e);
        }
    }
    
    public List<ModuleStatus> getModuleStatus() {
        List<ModuleStatus> statuses = new ArrayList<>();
        
        for (PipelineModule module : PipelineModule.values()) {
            boolean running = isModuleRunning(module);
            statuses.add(new ModuleStatus(
                module,
                running,
                running ? getModuleHealth(module) : false
            ));
        }
        
        return statuses;
    }
    
    private boolean isModuleRunning(PipelineModule module) {
        try {
            List<Container> containers = dockerClient.listContainersCmd()
                .withLabelFilter(Map.of(
                    "com.docker.compose.project", PROJECT_NAME,
                    "pipeline.module.name", module.getModuleName()
                ))
                .exec();
            
            return !containers.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }
    
    private boolean getModuleHealth(PipelineModule module) {
        // Could check Consul health here
        return true; // Simplified for now
    }
    
    private boolean isModuleHealthy(PipelineModule module) {
        // Check if module is registered and healthy in Consul
        // This is a simplified version - could integrate with Consul API
        return isModuleRunning(module);
    }
    
    private void checkMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        
        double usagePercent = (double) usedMemory / maxMemory * 100;
        if (usagePercent > 85) {
            LOG.warn("Memory usage is at {}%. Consider stopping unused modules.", 
                     String.format("%.1f", usagePercent));
        }
    }
    
    public void resetEnvironment() {
        LOG.info("Resetting dev environment...");
        
        if (devEnvironment != null) {
            devEnvironment.stop();
        }
        
        // Clean up any remaining containers
        executeDockerComposeViaAPI("down", "-v");
        
        // Restart
        startDevInfrastructure();
    }
    
    @PreDestroy
    void cleanup() {
        // Note: We intentionally do NOT stop containers on shutdown
        // This allows for faster restarts during development
        LOG.info("Engine shutting down. Dev infrastructure will remain running.");
        LOG.info("To stop infrastructure, use: docker compose -p " + PROJECT_NAME + " down");
    }
    
    public record ModuleStatus(
        PipelineModule module,
        boolean running,
        boolean healthy
    ) {}
}
```

### Dev Mode REST API
```java
package com.rokkon.pipeline.engine.api.dev;

import com.rokkon.pipeline.engine.dev.PipelineDevModeInfrastructure;
import com.rokkon.pipeline.engine.dev.PipelineModule;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Path("/q/dev/pipeline")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@IfBuildProfile("dev")
@Tag(name = "Dev Mode", description = "Development mode operations")
public class DevModuleResource {
    
    @Inject
    PipelineDevModeInfrastructure infrastructure;
    
    @GET
    @Path("/modules")
    @Operation(summary = "List all modules and their status")
    public Response listModules() {
        List<PipelineDevModeInfrastructure.ModuleStatus> statuses = infrastructure.getModuleStatus();
        
        Map<String, List<ModuleInfo>> grouped = Map.of(
            "running", statuses.stream()
                .filter(PipelineDevModeInfrastructure.ModuleStatus::running)
                .map(this::toModuleInfo)
                .collect(Collectors.toList()),
            "available", statuses.stream()
                .filter(s -> !s.running())
                .map(this::toModuleInfo)
                .collect(Collectors.toList())
        );
        
        return Response.ok(grouped).build();
    }
    
    @POST
    @Path("/modules/{module}/start")
    @Operation(summary = "Start a specific module")
    public Response startModule(@PathParam("module") String moduleName) {
        try {
            PipelineModule module = PipelineModule.fromName(moduleName);
            infrastructure.startModule(module);
            return Response.ok(Map.of(
                "status", "started",
                "module", module.getModuleName(),
                "message", "Module " + module.getModuleName() + " is starting..."
            )).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "Unknown module: " + moduleName))
                .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", "Failed to start module: " + e.getMessage()))
                .build();
        }
    }
    
    @POST
    @Path("/modules/{module}/stop")
    @Operation(summary = "Stop a specific module")
    public Response stopModule(@PathParam("module") String moduleName) {
        try {
            PipelineModule module = PipelineModule.fromName(moduleName);
            infrastructure.stopModule(module);
            return Response.ok(Map.of(
                "status", "stopped",
                "module", module.getModuleName(),
                "message", "Module " + module.getModuleName() + " has been stopped"
            )).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "Unknown module: " + moduleName))
                .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", "Failed to stop module: " + e.getMessage()))
                .build();
        }
    }
    
    @POST
    @Path("/reset")
    @Operation(summary = "Reset the entire dev environment")
    public Response resetEnvironment() {
        try {
            infrastructure.resetEnvironment();
            return Response.ok(Map.of(
                "status", "reset",
                "message", "Dev environment has been reset"
            )).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", "Failed to reset environment: " + e.getMessage()))
                .build();
        }
    }
    
    @GET
    @Path("/status")
    @Operation(summary = "Get dev environment status")
    public Response getStatus() {
        return Response.ok(Map.of(
            "profile", "dev",
            "infrastructure", "running",
            "dashboardUrl", "http://localhost:39000",
            "consulUrl", "http://localhost:8500",
            "devUiUrl", "http://localhost:39000/q/dev"
        )).build();
    }
    
    @POST
    @Path("/modules/batch/start")
    @Operation(summary = "Start multiple modules in parallel")
    public Response startModules(List<String> moduleNames) {
        Map<String, String> results = new HashMap<>();
        
        for (String moduleName : moduleNames) {
            try {
                PipelineModule module = PipelineModule.fromName(moduleName);
                infrastructure.startModule(module);
                results.put(moduleName, "started");
            } catch (Exception e) {
                results.put(moduleName, "failed: " + e.getMessage());
            }
        }
        
        return Response.ok(results).build();
    }
    
    private ModuleInfo toModuleInfo(PipelineDevModeInfrastructure.ModuleStatus status) {
        PipelineModule module = status.module();
        return new ModuleInfo(
            module.getModuleName(),
            module.getDescription(),
            module.getDefaultMemory(),
            module.getDockerImage(),
            status.running(),
            status.healthy()
        );
    }
    
    public record ModuleInfo(
        String name,
        String description,
        String memory,
        String dockerImage,
        boolean running,
        boolean healthy
    ) {}
}
```

### Docker Compose Configuration
```yaml
# docker/development/docker-compose.dev.yml
# Development environment with Consul + modules in Docker, engine runs locally in dev mode
version: '3.8'

services:
  # ==========================================
  # 1. CONSUL SERVER - Central Service Discovery
  # ==========================================
  consul-server:
    image: hashicorp/consul:${CONSUL_VERSION:-1.21}
    container_name: consul-server-dev
    ports:
      - "${CONSUL_HTTP_PORT:-8500}:8500"      # HTTP API & UI
      - "${CONSUL_DNS_PORT:-8600}:8600/tcp"  # DNS
      - "${CONSUL_DNS_PORT:-8600}:8600/udp"  # DNS
    volumes:
      - consul_data_dev:/consul/data
    command: >
      agent -server -ui 
      -client=0.0.0.0 
      -bind=0.0.0.0
      -bootstrap-expect=1 
      -dev
    environment:
      - CONSUL_BIND_INTERFACE=eth0
    healthcheck:
      test: ["CMD", "consul", "info"]
      interval: 5s
      timeout: 3s
      retries: 10
    networks:
      - pipeline-dev-network
    labels:
      - "com.docker.compose.project=${COMPOSE_PROJECT_NAME:-pipeline-dev}"
      - "pipeline.service.type=infrastructure"

  # ==========================================
  # 2. CONFIGURATION SEEDER - One-time Job
  # ==========================================
  seeder:
    build:
      context: ../../cli/seed-engine-consul-config
      dockerfile: Dockerfile
    container_name: consul-seeder-dev
    depends_on:
      consul-server:
        condition: service_healthy
    entrypoint: ["/bin/sh", "-c"]
    command:
      - |
        # Seed with dev-specific configuration
        java -jar build/quarkus-app/quarkus-run.jar -h consul-server -p 8500 --key config/application --import seed-data.json --force &&
        java -jar build/quarkus-app/quarkus-run.jar -h consul-server -p 8500 --key config/dev --import seed-data.json --force
    volumes:
      - ../../cli/seed-engine-consul-config/seed-data.json:/app/seed-data.json:ro
    networks:
      - pipeline-dev-network
    labels:
      - "com.docker.compose.project=${COMPOSE_PROJECT_NAME:-pipeline-dev}"

  # ==========================================
  # 3. ENGINE CONSUL SIDECAR - NEW!
  # ==========================================
  consul-agent-engine:
    image: hashicorp/consul:${CONSUL_VERSION:-1.21}
    container_name: consul-agent-engine-dev
    depends_on:
      consul-server:
        condition: service_healthy
    ports:
      - "8501:8500"  # Engine connects to localhost:8501
    command: >
      agent 
      -node="engine-sidecar-dev"
      -client=0.0.0.0
      -bind=0.0.0.0
      -retry-join=consul-server
      -advertise=${ENGINE_HOST_IP:-172.17.0.1}
      -log-level=info
    environment:
      - CONSUL_BIND_INTERFACE=eth0
      - ENGINE_HOST_IP=${ENGINE_HOST_IP:-172.17.0.1}
    networks:
      - pipeline-dev-network
    labels:
      - "com.docker.compose.project=${COMPOSE_PROJECT_NAME:-pipeline-dev}"
      - "pipeline.service.type=infrastructure"
      - "pipeline.service.name=engine-sidecar"

  # ==========================================
  # 4. MODULE SERVICES WITH SIDECARS
  # ==========================================
  
  # Echo Module
  consul-agent-echo:
    image: hashicorp/consul:${CONSUL_VERSION:-1.21}
    container_name: consul-agent-echo-dev
    depends_on:
      consul-server:
        condition: service_healthy
    ports:
      - "${ECHO_MODULE_REST_PORT:-39090}:39090"
      - "${ECHO_MODULE_GRPC_PORT:-49090}:49090"
    command: >
      agent 
      -node="echo-sidecar-dev"
      -client=0.0.0.0 
      -bind=0.0.0.0
      -retry-join=consul-server
      -log-level=info
    environment:
      - CONSUL_BIND_INTERFACE=eth0
    networks:
      - pipeline-dev-network
    labels:
      - "com.docker.compose.project=${COMPOSE_PROJECT_NAME:-pipeline-dev}"
      - "pipeline.service.type=module-sidecar"

  echo-module:
    image: ${ECHO_MODULE_IMAGE:-pipeline/echo:latest}
    container_name: echo-module-dev
    depends_on:
      consul-agent-echo:
        condition: service_started
    environment:
      - MODULE_NAME=echo
      - MODULE_HOST=consul-agent-echo
      - MODULE_HTTP_PORT=39090
      - MODULE_GRPC_PORT=49090
      - QUARKUS_HTTP_PORT=39090
      - QUARKUS_GRPC_SERVER_PORT=49090
      - ENGINE_HOST=${ENGINE_HOST_IP:-172.17.0.1}
      - ENGINE_PORT=49000
      - QUARKUS_PROFILE=${PROFILE:-dev}
      - JAVA_OPTS=-Xmx1g -Xms512m
    network_mode: "service:consul-agent-echo"
    deploy:
      resources:
        limits:
          memory: 1G
        reservations:
          memory: 512M
    labels:
      - "com.docker.compose.project=${COMPOSE_PROJECT_NAME:-pipeline-dev}"
      - "pipeline.module=true"
      - "pipeline.module.name=echo"

  # Test Module
  consul-agent-test:
    image: hashicorp/consul:${CONSUL_VERSION:-1.21}
    container_name: consul-agent-test-dev
    depends_on:
      consul-server:
        condition: service_healthy
    ports:
      - "${TEST_MODULE_REST_PORT:-39095}:39095"
      - "${TEST_MODULE_GRPC_PORT:-49095}:49095"
    command: >
      agent 
      -node="test-sidecar-dev"
      -client=0.0.0.0 
      -bind=0.0.0.0
      -retry-join=consul-server
      -log-level=info
    environment:
      - CONSUL_BIND_INTERFACE=eth0
    networks:
      - pipeline-dev-network
    labels:
      - "com.docker.compose.project=${COMPOSE_PROJECT_NAME:-pipeline-dev}"
      - "pipeline.service.type=module-sidecar"

  test-module:
    image: ${TEST_MODULE_IMAGE:-pipeline/test-module:latest}
    container_name: test-module-dev
    depends_on:
      consul-agent-test:
        condition: service_started
    environment:
      - MODULE_NAME=test
      - MODULE_HOST=consul-agent-test
      - MODULE_HTTP_PORT=39095
      - MODULE_GRPC_PORT=49095
      - QUARKUS_HTTP_PORT=39095
      - QUARKUS_GRPC_SERVER_PORT=49095
      - ENGINE_HOST=${ENGINE_HOST_IP:-172.17.0.1}
      - ENGINE_PORT=49000
      - QUARKUS_PROFILE=${PROFILE:-dev}
      - JAVA_OPTS=-Xmx1g -Xms512m
    network_mode: "service:consul-agent-test"
    deploy:
      resources:
        limits:
          memory: 1G
        reservations:
          memory: 512M
    labels:
      - "com.docker.compose.project=${COMPOSE_PROJECT_NAME:-pipeline-dev}"
      - "pipeline.module=true"
      - "pipeline.module.name=test"

  # Parser Module (started on demand)
  consul-agent-parser:
    image: hashicorp/consul:${CONSUL_VERSION:-1.21}
    container_name: consul-agent-parser-dev
    depends_on:
      consul-server:
        condition: service_healthy
    ports:
      - "${PARSER_MODULE_REST_PORT:-39093}:39093"
      - "${PARSER_MODULE_GRPC_PORT:-49093}:49093"
    command: >
      agent 
      -node="parser-sidecar-dev"
      -client=0.0.0.0 
      -bind=0.0.0.0
      -retry-join=consul-server
      -log-level=info
    environment:
      - CONSUL_BIND_INTERFACE=eth0
    networks:
      - pipeline-dev-network
    labels:
      - "com.docker.compose.project=${COMPOSE_PROJECT_NAME:-pipeline-dev}"
      - "pipeline.service.type=module-sidecar"

  parser-module:
    image: ${PARSER_MODULE_IMAGE:-pipeline/parser:latest}
    container_name: parser-module-dev
    depends_on:
      consul-agent-parser:
        condition: service_started
    environment:
      - MODULE_NAME=parser
      - MODULE_HOST=consul-agent-parser
      - MODULE_HTTP_PORT=39093
      - MODULE_GRPC_PORT=49093
      - QUARKUS_HTTP_PORT=39093
      - QUARKUS_GRPC_SERVER_PORT=49093
      - ENGINE_HOST=${ENGINE_HOST_IP:-172.17.0.1}
      - ENGINE_PORT=49000
      - QUARKUS_PROFILE=${PROFILE:-dev}
      - JAVA_OPTS=-Xmx1g -Xms512m
    network_mode: "service:consul-agent-parser"
    deploy:
      resources:
        limits:
          memory: 1G
        reservations:
          memory: 512M
    labels:
      - "com.docker.compose.project=${COMPOSE_PROJECT_NAME:-pipeline-dev}"
      - "pipeline.module=true"
      - "pipeline.module.name=parser"

  # Chunker Module (started on demand)
  consul-agent-chunker:
    image: hashicorp/consul:${CONSUL_VERSION:-1.21}
    container_name: consul-agent-chunker-dev
    depends_on:
      consul-server:
        condition: service_healthy
    ports:
      - "${CHUNKER_MODULE_REST_PORT:-39092}:39092"
      - "${CHUNKER_MODULE_GRPC_PORT:-49092}:49092"
    command: >
      agent 
      -node="chunker-sidecar-dev"
      -client=0.0.0.0 
      -bind=0.0.0.0
      -retry-join=consul-server
      -log-level=info
    environment:
      - CONSUL_BIND_INTERFACE=eth0
    networks:
      - pipeline-dev-network
    labels:
      - "com.docker.compose.project=${COMPOSE_PROJECT_NAME:-pipeline-dev}"
      - "pipeline.service.type=module-sidecar"

  chunker-module:
    image: ${CHUNKER_MODULE_IMAGE:-pipeline/chunker:latest}
    container_name: chunker-module-dev
    depends_on:
      consul-agent-chunker:
        condition: service_started
    environment:
      - MODULE_NAME=chunker
      - MODULE_HOST=consul-agent-chunker
      - MODULE_HTTP_PORT=39092
      - MODULE_GRPC_PORT=49092
      - QUARKUS_HTTP_PORT=39092
      - QUARKUS_GRPC_SERVER_PORT=49092
      - ENGINE_HOST=${ENGINE_HOST_IP:-172.17.0.1}
      - ENGINE_PORT=49000
      - QUARKUS_PROFILE=${PROFILE:-dev}
      - JAVA_OPTS=-Xmx4g -Xms1g
    network_mode: "service:consul-agent-chunker"
    deploy:
      resources:
        limits:
          memory: 4G
        reservations:
          memory: 2G
    labels:
      - "com.docker.compose.project=${COMPOSE_PROJECT_NAME:-pipeline-dev}"
      - "pipeline.module=true"
      - "pipeline.module.name=chunker"

  # Embedder Module (started on demand)
  consul-agent-embedder:
    image: hashicorp/consul:${CONSUL_VERSION:-1.21}
    container_name: consul-agent-embedder-dev
    depends_on:
      consul-server:
        condition: service_healthy
    ports:
      - "${EMBEDDER_MODULE_REST_PORT:-39094}:39094"
      - "${EMBEDDER_MODULE_GRPC_PORT:-49094}:49094"
    command: >
      agent 
      -node="embedder-sidecar-dev"
      -client=0.0.0.0 
      -bind=0.0.0.0
      -retry-join=consul-server
      -log-level=info
    environment:
      - CONSUL_BIND_INTERFACE=eth0
    networks:
      - pipeline-dev-network
    labels:
      - "com.docker.compose.project=${COMPOSE_PROJECT_NAME:-pipeline-dev}"
      - "pipeline.service.type=module-sidecar"

  embedder-module:
    image: ${EMBEDDER_MODULE_IMAGE:-pipeline/embedder:latest}
    container_name: embedder-module-dev
    depends_on:
      consul-agent-embedder:
        condition: service_started
    environment:
      - MODULE_NAME=embedder
      - MODULE_HOST=consul-agent-embedder
      - MODULE_HTTP_PORT=39094
      - MODULE_GRPC_PORT=49094
      - QUARKUS_HTTP_PORT=39094
      - QUARKUS_GRPC_SERVER_PORT=49094
      - ENGINE_HOST=${ENGINE_HOST_IP:-172.17.0.1}
      - ENGINE_PORT=49000
      - QUARKUS_PROFILE=${PROFILE:-dev}
      - JAVA_OPTS=-Xmx8g -Xms2g
    network_mode: "service:consul-agent-embedder"
    deploy:
      resources:
        limits:
          memory: 8G
        reservations:
          memory: 4G
    labels:
      - "com.docker.compose.project=${COMPOSE_PROJECT_NAME:-pipeline-dev}"
      - "pipeline.module=true"
      - "pipeline.module.name=embedder"

# ==========================================
# NETWORKS & VOLUMES
# ==========================================
networks:
  pipeline-dev-network:
    driver: bridge

volumes:
  consul_data_dev:
```

### Engine Dev Configuration
```yaml
# engine/pipestream/src/main/resources/application-dev.yml
# Dev profile configuration
quarkus:
  # Console logging for dev
  log:
    console:
      enable: true
    level: INFO
    category:
      "com.rokkon.pipeline": DEBUG
      "io.quarkus.consul": DEBUG
  
  # Consul configuration for dev - uses sidecar
  consul:
    host: localhost
    port: 8501  # Engine's sidecar port (not main Consul)
  
  consul-config:
    enabled: true
    fail-on-missing-key: false  # More forgiving in dev
    properties-value-keys:
      - config/application
      - config/dev
      - config/${quarkus.profile}
  
  # HTTP/gRPC ports
  http:
    port: 39000
    host: 0.0.0.0
  
  grpc:
    server:
      port: 49000
      host: 0.0.0.0
  
  # Dev UI
  dev:
    ui:
      always-enabled: true

# Pipeline dev configuration
pipeline:
  dev:
    auto-start: true
    default-modules: ECHO,TEST
    compose-file: docker/development/docker-compose.dev.yml
  
  cluster:
    name: dev-cluster
  
  engine:
    name: dev-engine

# Service discovery - use Consul
stork:
  pipeline-engine:
    service-registrar:
      type: consul
      consul-host: localhost
      consul-port: 8501  # Register via sidecar
      instance-port: ${quarkus.grpc.server.port:49000}
      metadata:
        service-type: ENGINE
        grpc-port: ${quarkus.grpc.server.port:49000}
        http-port: ${quarkus.http.port:39000}
```

## Usage Guide

### 1. Start Development Environment
```bash
# Just run this - everything else is automatic!
./gradlew quarkusDev

# Or with specific profile
./gradlew quarkusDev -Dquarkus.profile=dev
```

This will:
- Start Consul server and configuration seeder
- Start engine's Consul sidecar on port 8501
- Start default modules (echo, test) with their sidecars
- Engine runs locally with hot reload enabled

### 2. Access Services
- **Engine Dashboard**: http://localhost:39000
- **Consul UI**: http://localhost:8500
- **Dev UI**: http://localhost:39000/q/dev
- **Module Management API**: http://localhost:39000/q/dev/pipeline/modules

### 3. Manage Modules

#### Via REST API:
```bash
# List modules
curl http://localhost:39000/q/dev/pipeline/modules

# Start chunker module
curl -X POST http://localhost:39000/q/dev/pipeline/modules/chunker/start

# Stop test module
curl -X POST http://localhost:39000/q/dev/pipeline/modules/test/stop

# Reset environment
curl -X POST http://localhost:39000/q/dev/pipeline/reset
```

#### Via Dev UI:
Navigate to http://localhost:39000/q/dev and use the visual interface to:
- See running/available modules
- Start/stop modules with one click
- View module health and status

### 4. Create and Test Pipelines

With modules running, you can create and test pipelines:

```bash
# Create a pipeline definition
curl -X POST http://localhost:39000/api/v1/pipeline-definitions \
  -H "Content-Type: application/json" \
  -d '{
    "name": "dev-test-pipeline",
    "description": "Test pipeline for development",
    "steps": [
      {
        "name": "echo-step",
        "processorInfo": {
          "moduleName": "echo",
          "serviceType": "PipeStepProcessor"
        }
      }
    ]
  }'

# Deploy the pipeline
curl -X POST http://localhost:39000/api/v1/clusters/dev-cluster/pipelines/dev-test \
  -H "Content-Type: application/json" \
  -d '{
    "pipelineDefinitionName": "dev-test-pipeline"
  }'

# Send data through the pipeline
# (Implementation depends on your pipeline input mechanism)
```

### 5. Development Workflow

1. **Make code changes** - Engine code is hot-reloaded automatically
2. **Test changes** - Use the running modules to test your changes
3. **Add modules as needed** - Start additional modules when required
4. **Clean restart** - Use reset endpoint if needed

### 6. Shutdown

When you stop Quarkus (`Ctrl+C`), the Docker infrastructure remains running for faster restarts.

To completely stop everything:
```bash
docker compose -p pipeline-dev down

# Or with volumes cleanup
docker compose -p pipeline-dev down -v
```

## Configuration Options

### Application Properties
```properties
# Disable auto-start (useful for debugging)
pipeline.dev.auto-start=false

# Change default modules
pipeline.dev.default-modules=ECHO,TEST,PARSER

# Use different compose file
pipeline.dev.compose-file=docker/development/my-compose.yml
```

### Environment Variables
```bash
# Override engine host IP detection
ENGINE_HOST_IP=192.168.1.100

# Change Consul version
CONSUL_VERSION=1.22

# Module-specific ports
ECHO_MODULE_REST_PORT=39091
ECHO_MODULE_GRPC_PORT=49091
```

## Troubleshooting

### Docker Not Running
```
Error: Dev mode requires Docker. Please start Docker and try again.
```
**Solution**: Start Docker Desktop or Docker daemon

### Port Conflicts
```
Error: Port 8501 is already in use
```
**Solution**: Check for running containers or change the port mapping

### Module Won't Start
1. Check Docker logs: `docker logs <module-name>-dev`
2. Verify Consul health: http://localhost:8500
3. Check module registration in engine logs

### Reset Everything
```bash
# Stop all containers
docker compose -p pipeline-dev down -v

# Remove all pipeline-dev containers
docker ps -a | grep pipeline-dev | awk '{print $1}' | xargs docker rm -f

# Restart
./gradlew clean quarkusDev
```

## Benefits

1. **Zero Setup**: One command starts everything
2. **Hot Reload**: Engine changes apply instantly
3. **Full Ecosystem**: All pipeline components available
4. **Easy Module Management**: Start/stop modules on demand
5. **Production Parity**: Same service mesh patterns as production
6. **Fast Iteration**: Infrastructure persists between restarts
7. **Developer Friendly**: Visual Dev UI for module management

## Future Enhancements

1. **Module Templates**: Quick scaffolding for new modules
2. **Pipeline Templates**: Pre-built pipeline configurations for common use cases
3. **Performance Monitoring**: Dev-specific metrics and profiling
4. **Mock Data Generators**: Built-in test data generation
5. **Visual Pipeline Builder**: Drag-and-drop pipeline creation in Dev UI

## Implementation Notes

### Key Improvements from Feedback

1. **Eliminated ProcessBuilder**: Module management now uses Docker-Java API directly for consistency and better error handling
2. **Enhanced Host IP Detection**: Added support for `host.docker.internal` which works universally on Docker Desktop (Windows, macOS, Linux)
3. **Unified Docker Control**: All Docker operations now go through the same API, making the solution more maintainable
4. **Memory Monitoring**: Added warnings when memory usage exceeds 85% to help developers manage resource constraints
5. **Batch Operations**: Added batch start/stop endpoints for efficient module management
6. **Docker Validation**: Added upfront Docker availability check to fail fast with clear error messages

### Integration with Existing Infrastructure

The dev mode leverages existing components:
- **Zombie Cleanup**: Existing zombie detection and cleanup processes work with dev mode containers
- **Health Checks**: Module health checks use the existing Consul health infrastructure
- **Metrics Dashboard**: Dev mode modules appear in the existing metrics dashboard
- **Module Registration**: Uses the same gRPC registration flow as production

### Platform Compatibility

The host IP detection now tries multiple approaches in order:
1. `host.docker.internal` - Works on Docker Desktop across all platforms
2. Docker bridge gateway - Works on native Linux Docker installations
3. Fallback to common default (`172.17.0.1`)

This ensures the dev environment works seamlessly across different developer machines without configuration.

### Future Reactive Enhancements

When implementing the UI and real-time features, consider using Mutiny for:
- Parallel module startup with progress streaming
- Server-Sent Events for live status updates
- Reactive log streaming from containers
- Non-blocking health check monitoring

### Dev UI to Production Dashboard Migration

Features developed for the Dev UI should be evaluated for inclusion in the production dashboard:

#### Candidates for Migration (UI Features Only):
1. **Real-time Module Status Updates** - SSE/WebSocket updates would benefit production monitoring
2. **Module Deregistration Controls** - Soft stop via Consul deregistration (already implemented)
3. **Memory Usage Indicators** - Critical for production resource monitoring
4. **Health Timeline Visualization** - Historical health data from Consul
5. **Module Filtering and Search** - Better navigation with many modules
6. **Batch Selection UI** - Select multiple modules for operations

#### Important Distinctions:
- **Dev Mode**: Uses Docker API for hard start/stop of containers
- **Production**: Uses Consul deregistration for soft stop (modules handle graceful shutdown)
- **No Docker dependency in production** - Production dashboard remains infrastructure-agnostic

#### Implementation Strategy:
1. Develop UI components in Dev UI first (faster iteration)
2. Keep Docker operations isolated to dev mode only
3. Production dashboard uses existing Consul APIs:
    - Module deregistration (soft stop)
    - Health status monitoring
    - Service discovery
4. Share only UI components and visualization logic

#### Shared UI Components:
```javascript
// Shared module status component
export const ModuleStatusCard = ({ 
  module, 
  onDeregister,      // Dev: Docker stop, Prod: Consul deregister
  showMemory = true,
  showHealth = true
}) => {
  // Component implementation
  // Actions call different backends based on context
};
```

This separation ensures dev mode innovations enhance the production UI without introducing Docker dependencies or changing the production architecture.