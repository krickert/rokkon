package com.rokkon.pipeline.engine.api;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Development REST API for managing module containers.
 * This helps with local development and testing.
 * 
 * Usage:
 * - POST /api/v1/dev/modules/echo/deploy     - Deploy echo module
 * - DELETE /api/v1/dev/modules/echo          - Stop echo module
 * - GET /api/v1/dev/modules                  - List all modules
 * - POST /api/v1/dev/modules/parser/restart  - Restart parser module
 */
@Path("/api/v1/dev/modules")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Development", description = "Dev-only module management")
public class DevModuleResource {
    
    private static final Logger LOG = Logger.getLogger(DevModuleResource.class);
    
    // Track deployed modules
    private static final Map<String, ModuleInfo> deployedModules = new ConcurrentHashMap<>();
    
    // Module port assignments
    private static final Map<String, ModulePorts> MODULE_PORTS = Map.of(
        "echo", new ModulePorts(39091, 49091),
        "chunker", new ModulePorts(39092, 49092),
        "parser", new ModulePorts(39093, 49093),
        "embedder", new ModulePorts(39094, 49094)
    );
    
    record ModulePorts(int http, int grpc) {}
    record ModuleInfo(
        String name, 
        String containerId, 
        int httpPort, 
        int grpcPort, 
        boolean running,
        String status
    ) {}
    
    record DeployRequest(boolean buildImage) {}
    record ModuleStatus(
        String name,
        String status,
        int httpPort,
        int grpcPort,
        String healthUrl,
        String containerId
    ) {}
    
    @GET
    @Operation(summary = "List all deployed modules")
    public Response listModules() {
        Map<String, ModuleStatus> statuses = new HashMap<>();
        
        for (Map.Entry<String, ModuleInfo> entry : deployedModules.entrySet()) {
            ModuleInfo info = entry.getValue();
            boolean healthy = checkModuleHealth(info);
            
            statuses.put(entry.getKey(), new ModuleStatus(
                info.name,
                healthy ? "healthy" : "unhealthy",
                info.httpPort,
                info.grpcPort,
                "http://localhost:" + info.httpPort + "/q/health",
                info.containerId
            ));
        }
        
        return Response.ok(statuses).build();
    }
    
    @POST
    @Path("/{module}/deploy")
    @Operation(summary = "Deploy a module container")
    public Response deployModule(
            @PathParam("module") String module,
            DeployRequest request) {
        
        if (!MODULE_PORTS.containsKey(module)) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of(
                    "error", "Unknown module: " + module,
                    "available", MODULE_PORTS.keySet()
                ))
                .build();
        }
        
        if (deployedModules.containsKey(module)) {
            return Response.status(Response.Status.CONFLICT)
                .entity(Map.of("error", "Module already deployed"))
                .build();
        }
        
        try {
            ModulePorts ports = MODULE_PORTS.get(module);
            String containerName = "pipeline-" + module;
            String imageName = "pipeline/" + module + "-module:latest";
            
            LOG.infof("Deploying %s module...", module);
            
            // Stop any existing container
            exec("docker", "stop", containerName);
            exec("docker", "rm", containerName);
            
            // Build image if requested
            if (request != null && request.buildImage) {
                LOG.infof("Building %s module image...", module);
                buildModuleImage(module, imageName);
            }
            
            // Start the container
            String containerId = startModuleContainer(module, containerName, imageName, ports);
            
            // Wait for module to be ready
            Thread.sleep(3000);
            
            // Register with engine
            registerModuleWithEngine(module, ports);
            
            ModuleInfo info = new ModuleInfo(
                module, containerId, ports.http, ports.grpc, true, "running"
            );
            deployedModules.put(module, info);
            
            return Response.ok(Map.of(
                "status", "deployed",
                "module", module,
                "httpPort", ports.http,
                "grpcPort", ports.grpc,
                "healthUrl", "http://localhost:" + ports.http + "/q/health",
                "containerId", containerId
            )).build();
            
        } catch (Exception e) {
            LOG.errorf(e, "Failed to deploy module %s", module);
            return Response.serverError()
                .entity(Map.of("error", e.getMessage()))
                .build();
        }
    }
    
    @DELETE
    @Path("/{module}")
    @Operation(summary = "Undeploy a module")
    public Response undeployModule(@PathParam("module") String module) {
        ModuleInfo info = deployedModules.get(module);
        if (info == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", "Module not deployed"))
                .build();
        }
        
        try {
            LOG.infof("Stopping %s module...", module);
            
            exec("docker", "stop", info.containerId);
            exec("docker", "rm", info.containerId);
            
            deployedModules.remove(module);
            
            return Response.ok(Map.of(
                "status", "stopped",
                "module", module
            )).build();
            
        } catch (Exception e) {
            LOG.errorf(e, "Failed to stop module %s", module);
            return Response.serverError()
                .entity(Map.of("error", e.getMessage()))
                .build();
        }
    }
    
    @POST
    @Path("/{module}/restart")
    @Operation(summary = "Restart a module")
    public Response restartModule(@PathParam("module") String module) {
        if (!deployedModules.containsKey(module)) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", "Module not deployed"))
                .build();
        }
        
        try {
            // Stop
            undeployModule(module);
            Thread.sleep(1000);
            
            // Start
            return deployModule(module, new DeployRequest(false));
            
        } catch (Exception e) {
            return Response.serverError()
                .entity(Map.of("error", e.getMessage()))
                .build();
        }
    }
    
    private void buildModuleImage(String module, String imageName) throws Exception {
        String projectDir = System.getProperty("user.dir");
        String moduleDir = projectDir + "/../../modules/" + module;
        
        // Build with Gradle
        ProcessBuilder gradleBuild = new ProcessBuilder(
            "./gradlew", "build", "-x", "test"
        );
        gradleBuild.directory(new java.io.File(moduleDir));
        gradleBuild.inheritIO();
        Process gradleProcess = gradleBuild.start();
        if (gradleProcess.waitFor() != 0) {
            throw new RuntimeException("Gradle build failed");
        }
        
        // Build Docker image
        ProcessBuilder dockerBuild = new ProcessBuilder(
            "docker", "build", 
            "-f", "src/main/docker/Dockerfile.jvm",
            "-t", imageName,
            "."
        );
        dockerBuild.directory(new java.io.File(moduleDir));
        dockerBuild.inheritIO();
        Process dockerProcess = dockerBuild.start();
        if (dockerProcess.waitFor() != 0) {
            throw new RuntimeException("Docker build failed");
        }
    }
    
    private String startModuleContainer(String module, String containerName, 
                                       String imageName, ModulePorts ports) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
            "docker", "run", "-d",
            "--name", containerName,
            "--network", "host",
            "-e", "QUARKUS_HTTP_PORT=" + ports.http,
            "-e", "QUARKUS_GRPC_SERVER_PORT=" + ports.grpc,
            "-e", "CONSUL_HOST=localhost",
            "-e", "CONSUL_PORT=8500",
            "-e", "ENGINE_HOST=localhost",
            "-e", "ENGINE_GRPC_PORT=48082",
            imageName
        );
        
        Process process = pb.start();
        if (process.waitFor() != 0) {
            throw new RuntimeException("Docker run failed");
        }
        
        // Get container ID
        ProcessBuilder idPb = new ProcessBuilder(
            "docker", "ps", "-q", "-f", "name=" + containerName
        );
        Process idProcess = idPb.start();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(idProcess.getInputStream()))) {
            return reader.readLine();
        }
    }
    
    private void registerModuleWithEngine(String module, ModulePorts ports) {
        // For now, just log - in real implementation would call the register-module CLI
        // or use the gRPC registration service
        LOG.infof("Module %s ready for registration at gRPC port %d", module, ports.grpc);
        
        // TODO: Call the actual registration service
        // Could either:
        // 1. Use the CLI: java -jar cli/register-module/build/quarkus-app/quarkus-run.jar ...
        // 2. Call the gRPC service directly
        // 3. Use REST API if available
    }
    
    private boolean checkModuleHealth(ModuleInfo info) {
        try {
            var url = new java.net.URL("http://localhost:" + info.httpPort + "/q/health");
            var connection = (java.net.HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(1000);
            connection.setReadTimeout(1000);
            return connection.getResponseCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }
    
    private void exec(String... command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        process.waitFor();
    }
}