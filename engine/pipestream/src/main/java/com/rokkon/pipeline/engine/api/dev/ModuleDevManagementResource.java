package com.rokkon.pipeline.engine.api.dev;

import io.quarkus.arc.profile.IfBuildProfile;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;
import com.rokkon.pipeline.engine.dev.ModuleDeploymentService;
import com.rokkon.pipeline.engine.dev.ModuleDeploymentService.OrphanedModule;
import com.rokkon.pipeline.engine.dev.PipelineModule;
import com.rokkon.pipeline.engine.dev.PipelineDevModeInfrastructure;

import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.stream.Collectors;
import io.smallrye.mutiny.infrastructure.Infrastructure;

/**
 * REST API for module management operations in development mode.
 * Provides Docker-based operations for deploying, monitoring, and managing modules locally.
 * 
 * This resource is only available in dev mode and handles all container-related operations.
 */
@Path("/api/v1/dev/modules")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Dev Module Management", description = "Development mode module lifecycle and container management")
@IfBuildProfile("dev")
public class ModuleDevManagementResource {

    private static final Logger LOG = Logger.getLogger(ModuleDevManagementResource.class);

    @Inject
    ModuleDeploymentService deploymentService;
    
    @Inject
    PipelineDevModeInfrastructure infrastructure;

    /**
     * Response for module operations
     */
    @Schema(description = "Module operation response")
    public record ModuleOperationResponse(
        @Schema(description = "Whether the operation was successful", example = "true")
        boolean success,
        
        @Schema(description = "Operation message", example = "Module deployed successfully")
        String message,
        
        @Schema(description = "Additional details about the operation")
        Map<String, Object> details
    ) {}

    /**
     * Module status information
     */
    @Schema(description = "Module status")
    public record ModuleStatus(
        @Schema(description = "Module name", example = "echo")
        String name,
        
        @Schema(description = "Module status", example = "running")
        String status,
        
        @Schema(description = "Module health", example = "healthy")
        String health,
        
        @Schema(description = "Container name", example = "echo-module-app")
        String containerName,
        
        @Schema(description = "Sidecar name", example = "consul-agent-echo")
        String sidecarName,
        
        @Schema(description = "Port information")
        Map<String, Integer> ports
    ) {}

    /**
     * Available module information for dev deployment
     */
    @Schema(description = "Available module for development")
    public record DevModule(
        @Schema(description = "Module name", example = "echo")
        String name,
        
        @Schema(description = "Module type", example = "PROCESSOR")
        String type,
        
        @Schema(description = "Docker image", example = "pipeline/echo-module:latest")
        String image,
        
        @Schema(description = "Required memory", example = "1G")
        String memory,
        
        @Schema(description = "Module description")
        String description
    ) {}

    @GET
    @Path("/available")
    @Operation(
        summary = "List available modules for development",
        description = "Returns a list of all modules available for local deployment"
    )
    @APIResponse(
        responseCode = "200",
        description = "List of available modules",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(
                type = SchemaType.ARRAY,
                implementation = DevModule.class
            )
        )
    )
    public Uni<List<DevModule>> listAvailableModules() {
        LOG.info("Listing available modules for development");
        
        List<DevModule> modules = Arrays.stream(PipelineModule.values())
            .map(module -> new DevModule(
                module.getModuleName(),
                "PROCESSOR",
                module.getDockerImage(),
                module.getDefaultMemory(),
                module.getDescription()
            ))
            .collect(Collectors.toList());
            
        return Uni.createFrom().item(modules);
    }

    @GET
    @Path("/{moduleName}/status")
    @Operation(
        summary = "Get module status",
        description = "Returns the current status of a module"
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Module status",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = ModuleStatus.class)
            )
        ),
        @APIResponse(
            responseCode = "404",
            description = "Module not found"
        )
    })
    public Uni<Response> getModuleStatus(
            @Parameter(description = "Module name", required = true, example = "echo")
            @PathParam("moduleName") String moduleName) {
        
        LOG.infof("Getting status for module: %s", moduleName);
        
        try {
            PipelineModule module = PipelineModule.fromName(moduleName);
            ModuleDeploymentService.ModuleStatus status = deploymentService.getModuleStatus(module);
            
            ModuleStatus moduleStatus = new ModuleStatus(
                module.getModuleName(),
                status.toString().toLowerCase(),
                status == ModuleDeploymentService.ModuleStatus.RUNNING ? "healthy" : "unknown",
                module.getContainerName(),
                module.getSidecarName(),
                Map.of("internal", 39100) // All modules use the same internal port
            );
            
            return Uni.createFrom().item(Response.ok(moduleStatus).build());
        } catch (IllegalArgumentException e) {
            return Uni.createFrom().item(
                Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Module not found: " + moduleName))
                    .build()
            );
        }
    }

    @POST
    @Path("/{moduleName}/deploy")
    @Operation(
        summary = "Deploy a module",
        description = "Deploys a module with its sidecars in development mode"
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Module deployed successfully",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = ModuleOperationResponse.class)
            )
        ),
        @APIResponse(
            responseCode = "409",
            description = "Module already deployed"
        )
    })
    public Uni<Response> deployModule(
            @Parameter(description = "Module name", required = true, example = "echo")
            @PathParam("moduleName") String moduleName) {
        
        LOG.infof("Request to deploy module: %s", moduleName);
        
        return Uni.createFrom().item(() -> {
            try {
                PipelineModule module = PipelineModule.fromName(moduleName);
                
                // Check if already deployed
                if (infrastructure.isModuleRunning(module)) {
                    return Response.status(Response.Status.CONFLICT)
                        .entity(new ModuleOperationResponse(false, 
                            "Module " + moduleName + " is already deployed", null))
                        .build();
                }
                
                // Deploy the module
                ModuleDeploymentService.ModuleDeploymentResult result = deploymentService.deployModule(module);
                
                if (result.success()) {
                    return Response.ok(new ModuleOperationResponse(true, 
                        result.message(), 
                        Map.of(
                            "port", result.allocatedPort(),
                            "containerId", result.moduleContainerId(),
                            "module", moduleName,
                            "status", "deployed",
                            "instanceNumber", result.instanceNumber()
                        )))
                        .build();
                } else {
                    return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(new ModuleOperationResponse(false, 
                            result.message(), null))
                        .build();
                }
                    
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ModuleOperationResponse(false, 
                        "Invalid module name: " + moduleName, null))
                    .build();
            } catch (Exception e) {
                LOG.error("Failed to deploy module: " + moduleName, e);
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ModuleOperationResponse(false, 
                        "Deployment failed: " + e.getMessage(), null))
                    .build();
            }
        })
        .runSubscriptionOn(Infrastructure.getDefaultExecutor());
    }

    @DELETE
    @Path("/{moduleName}/stop")
    @Operation(
        summary = "Stop a module",
        description = "Stops all instances of a module and its sidecars"
    )
    @APIResponse(
        responseCode = "200",
        description = "Module stopped",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ModuleOperationResponse.class)
        )
    )
    public Uni<Response> stopModule(
            @Parameter(description = "Module name", required = true, example = "echo")
            @PathParam("moduleName") String moduleName) {
        
        LOG.infof("Request to stop module: %s", moduleName);
        
        return Uni.createFrom().item(() -> {
            try {
                PipelineModule module = PipelineModule.fromName(moduleName);
                deploymentService.stopModule(module);
                
                return Response.ok(new ModuleOperationResponse(true,
                    "Module " + moduleName + " stopped successfully",
                    Map.of("module", moduleName, "status", "stopped")))
                    .build();
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ModuleOperationResponse(false,
                        "Module not found: " + moduleName, null))
                    .build();
            } catch (Exception e) {
                LOG.error("Failed to stop module: " + moduleName, e);
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ModuleOperationResponse(false,
                        "Failed to stop module: " + e.getMessage(), null))
                    .build();
            }
        })
        .runSubscriptionOn(Infrastructure.getDefaultExecutor());
    }

    @POST
    @Path("/{moduleName}/scale-up")
    @Operation(
        summary = "Scale up a module",
        description = "Adds another instance of a running module"
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Module scaled up successfully",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = ModuleOperationResponse.class)
            )
        ),
        @APIResponse(
            responseCode = "400",
            description = "Module not running or scale-up failed"
        )
    })
    public Uni<Response> scaleUpModule(
            @Parameter(description = "Module name", required = true, example = "echo")
            @PathParam("moduleName") String moduleName) {
        
        LOG.infof("Request to scale up module: %s", moduleName);
        
        return Uni.createFrom().item(() -> {
            try {
                PipelineModule module = PipelineModule.fromName(moduleName);
                
                // Check if module is running
                if (!infrastructure.isModuleRunning(module)) {
                    return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ModuleOperationResponse(false,
                            "Module " + moduleName + " is not running", null))
                        .build();
                }
                
                // Scale up the module
                ModuleDeploymentService.ModuleDeploymentResult result = deploymentService.scaleUpModule(module);
                
                if (result.success()) {
                    return Response.ok(new ModuleOperationResponse(true,
                        result.message(),
                        Map.of(
                            "port", result.allocatedPort(),
                            "instanceNumber", result.instanceNumber(),
                            "module", moduleName,
                            "status", "scaled"
                        )))
                        .build();
                } else {
                    return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(new ModuleOperationResponse(false,
                            result.message(), null))
                        .build();
                }
                    
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ModuleOperationResponse(false,
                        "Invalid module name: " + moduleName, null))
                    .build();
            } catch (Exception e) {
                LOG.error("Failed to scale up module: " + moduleName, e);
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ModuleOperationResponse(false,
                        "Scale-up failed: " + e.getMessage(), null))
                    .build();
            }
        })
        .runSubscriptionOn(Infrastructure.getDefaultExecutor());
    }

    @GET
    @Path("/{moduleName}/logs")
    @Operation(
        summary = "Get module logs",
        description = "Returns recent logs from the module container"
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Module logs",
            content = @Content(mediaType = MediaType.TEXT_PLAIN)
        ),
        @APIResponse(
            responseCode = "404",
            description = "Module not found"
        )
    })
    public Uni<Response> getModuleLogs(
            @Parameter(description = "Module name", required = true, example = "echo")
            @PathParam("moduleName") String moduleName,
            @Parameter(description = "Number of lines to return", example = "100")
            @QueryParam("lines") @DefaultValue("100") int lines) {
        
        LOG.infof("Getting logs for module: %s", moduleName);
        
        return Uni.createFrom().item(() -> {
            try {
                PipelineModule module = PipelineModule.fromName(moduleName);
                // Get logs from the first instance if no specific instance is requested
                String containerName = module.getContainerName();
                List<String> logs = deploymentService.getContainerLogs(containerName, lines);
                String logsText = String.join("\n", logs);
                return Response.ok(logsText).type(MediaType.TEXT_PLAIN).build();
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity("Module not found: " + moduleName)
                    .type(MediaType.TEXT_PLAIN)
                    .build();
            } catch (Exception e) {
                LOG.error("Failed to get logs for module: " + moduleName, e);
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Failed to retrieve logs: " + e.getMessage())
                    .type(MediaType.TEXT_PLAIN)
                    .build();
            }
        })
        .runSubscriptionOn(Infrastructure.getDefaultExecutor());
    }

    @GET
    @Path("/orphaned")
    @Operation(
        summary = "Find orphaned module containers",
        description = "Lists containers that are running but not registered with the pipeline"
    )
    @APIResponse(
        responseCode = "200",
        description = "List of orphaned modules",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(
                type = SchemaType.ARRAY,
                implementation = OrphanedModule.class
            )
        )
    )
    public Uni<List<OrphanedModule>> findOrphanedModules() {
        LOG.info("Request to find orphaned module containers");
        
        return Uni.createFrom().item(() -> {
            List<OrphanedModule> orphanedModules = deploymentService.findOrphanedModules();
            return orphanedModules;
        })
        .runSubscriptionOn(Infrastructure.getDefaultExecutor());
    }

    @POST
    @Path("/orphaned/{containerId}/register")
    @Operation(
        summary = "Register an orphaned module",
        description = "Attempts to register an orphaned module container with the pipeline"
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Module registered successfully",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = ModuleOperationResponse.class)
            )
        ),
        @APIResponse(
            responseCode = "404",
            description = "Container not found"
        )
    })
    public Uni<Response> registerOrphanedModule(
            @Parameter(description = "Container ID", required = true)
            @PathParam("containerId") String containerId) {
        
        LOG.infof("Request to register orphaned module container: %s", containerId);
        
        return Uni.createFrom().item(() -> {
            try {
                boolean success = deploymentService.registerOrphanedModule(containerId);
                
                if (success) {
                    return Response.ok(new ModuleOperationResponse(true,
                        "Orphaned module registered successfully",
                        Map.of("containerId", containerId)))
                        .build();
                } else {
                    return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ModuleOperationResponse(false,
                            "Container not found or registration failed",
                            Map.of("containerId", containerId)))
                        .build();
                }
            } catch (Exception e) {
                LOG.errorf("Failed to register orphaned module: %s", e.getMessage(), e);
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ModuleOperationResponse(false,
                        "Registration failed: " + e.getMessage(),
                        Map.of("containerId", containerId)))
                    .build();
            }
        })
        .runSubscriptionOn(Infrastructure.getDefaultExecutor());
    }

    @DELETE
    @Path("/{moduleName}/cleanup")
    @Operation(
        summary = "Completely clean up a module",
        description = "Removes all containers and Consul registrations for a module"
    )
    @APIResponse(
        responseCode = "200",
        description = "Module cleaned up",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ModuleOperationResponse.class)
        )
    )
    public Uni<Response> cleanupModule(
            @Parameter(description = "Module name", required = true, example = "test-module")
            @PathParam("moduleName") String moduleName) {
        
        LOG.infof("Request to completely clean up module: %s", moduleName);
        
        return Uni.createFrom().item(() -> {
            try {
                deploymentService.cleanupModuleCompletely(moduleName);
                
                return Response.ok(new ModuleOperationResponse(true,
                    "Module " + moduleName + " cleaned up completely",
                    Map.of(
                        "module", moduleName,
                        "status", "cleaned"
                    )))
                    .build();
            } catch (Exception e) {
                LOG.error("Failed to clean up module: " + moduleName, e);
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ModuleOperationResponse(false,
                        "Cleanup failed: " + e.getMessage(),
                        Map.of("module", moduleName)))
                    .build();
            }
        })
        .runSubscriptionOn(Infrastructure.getDefaultExecutor());
    }

    @POST
    @Path("/orphaned/{containerId}/redeploy")
    @Operation(
        summary = "Redeploy an orphaned module",
        description = "Stops the orphaned container and deploys a fresh instance"
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Module redeployed successfully",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = ModuleOperationResponse.class)
            )
        ),
        @APIResponse(
            responseCode = "404",
            description = "Container not found"
        )
    })
    public Uni<Response> redeployOrphanedModule(
            @Parameter(description = "Container ID", required = true)
            @PathParam("containerId") String containerId) {
        
        LOG.infof("Request to redeploy orphaned module container: %s", containerId);
        
        return Uni.createFrom().item(() -> {
            try {
                ModuleDeploymentService.ModuleDeploymentResult result = deploymentService.redeployOrphanedModule(containerId);
                
                if (result.success()) {
                    return Response.ok(new ModuleOperationResponse(true,
                        result.message(),
                        Map.of(
                            "port", result.allocatedPort(),
                            "containerId", result.moduleContainerId(),
                            "instanceNumber", result.instanceNumber()
                        )))
                        .build();
                } else {
                    return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ModuleOperationResponse(false,
                            "Container not found or redeploy failed",
                            Map.of("containerId", containerId)))
                        .build();
                }
            } catch (Exception e) {
                LOG.errorf("Failed to redeploy orphaned module: %s", e.getMessage(), e);
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ModuleOperationResponse(false,
                        "Redeploy failed: " + e.getMessage(),
                        Map.of("containerId", containerId)))
                    .build();
            }
        })
        .runSubscriptionOn(Infrastructure.getDefaultExecutor());
    }
}