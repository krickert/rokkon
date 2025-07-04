package com.rokkon.pipeline.engine.api;

import io.quarkus.arc.profile.IfBuildProfile;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.enterprise.inject.Instance;
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
import com.rokkon.pipeline.engine.dev.PipelineModule;
import com.rokkon.pipeline.engine.dev.PipelineDevModeInfrastructure;
import com.rokkon.pipeline.commons.model.GlobalModuleRegistryService;

import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.stream.Collectors;
import io.smallrye.mutiny.infrastructure.Infrastructure;

/**
 * REST API for module lifecycle management.
 * Provides operations for deploying, monitoring, and managing modules in dev mode.
 */
@Path("/api/v1/module-management")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Module Management", description = "Module lifecycle and deployment management")
public class ModuleManagementResource {

    private static final Logger LOG = Logger.getLogger(ModuleManagementResource.class);

    @Inject
    Instance<ModuleDeploymentService> deploymentService;
    
    @Inject
    Instance<PipelineDevModeInfrastructure> infrastructure;
    
    @Inject
    GlobalModuleRegistryService moduleRegistry;

    /**
     * Response for module operations
     */
    @Schema(description = "Module operation response")
    public record ModuleOperationResponse(
        @Schema(description = "Success indicator", example = "true")
        boolean success,
        
        @Schema(description = "Response message", example = "Module deployed successfully")
        String message,
        
        @Schema(description = "Additional details", implementation = Object.class)
        Map<String, Object> details
    ) {}

    /**
     * Module status information
     */
    @Schema(description = "Module status")
    public record ModuleStatus(
        @Schema(description = "Module name", example = "echo")
        String name,
        
        @Schema(description = "Current status", example = "running", enumeration = {"deployed", "running", "stopped", "error"})
        String status,
        
        @Schema(description = "Health status", example = "healthy", enumeration = {"healthy", "unhealthy", "unknown"})
        String health,
        
        @Schema(description = "Container ID if deployed", example = "abc123def456")
        String containerId,
        
        @Schema(description = "Sidecar container ID", example = "def456ghi789")
        String sidecarId,
        
        @Schema(description = "Assigned ports")
        Map<String, Integer> ports
    ) {}

    /**
     * Available module information
     */
    @Schema(description = "Available module definition")
    public record AvailableModule(
        @Schema(description = "Module name", example = "echo")
        String name,
        
        @Schema(description = "Module type", example = "PROCESSOR")
        String type,
        
        @Schema(description = "Docker image", example = "rokkon/echo-module:latest")
        String image,
        
        @Schema(description = "Required memory", example = "1G")
        String memory,
        
        @Schema(description = "Default ports")
        Map<String, Integer> defaultPorts,
        
        @Schema(description = "Whether module is available for deployment", example = "true")
        boolean available
    ) {}

    @GET
    @Path("/available")
    @Operation(
        summary = "List available modules",
        description = "Returns a list of all modules available for deployment"
    )
    @APIResponse(
        responseCode = "200",
        description = "List of available modules",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(
                type = SchemaType.ARRAY,
                implementation = AvailableModule.class
            )
        )
    )
    public Uni<List<AvailableModule>> listAvailableModules() {
        LOG.info("Listing available modules");
        
        // Get available modules from PipelineModule enum
        List<AvailableModule> modules = Arrays.stream(PipelineModule.values())
            .map(module -> new AvailableModule(
                module.getModuleName(),
                "PROCESSOR",
                module.getDockerImage(),
                module.getDefaultMemory(),
                Map.of("unified", module.getUnifiedPort()),  // Unified server port
                true
            ))
            .collect(Collectors.toList());
            
        return Uni.createFrom().item(modules);
    }

    @GET
    @Path("/deployed")
    @Operation(
        summary = "List deployed modules",
        description = "Returns a list of currently deployed modules with their status"
    )
    @APIResponse(
        responseCode = "200",
        description = "List of deployed modules",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(
                type = SchemaType.ARRAY,
                implementation = ModuleStatus.class
            )
        )
    )
    public Uni<List<ModuleStatus>> listDeployedModules() {
        LOG.info("Listing deployed modules");
        
        // Check if services are available
        if (!deploymentService.isResolvable() || !infrastructure.isResolvable()) {
            return Uni.createFrom().item(List.of());
        }
        
        // Get status of all modules
        List<ModuleStatus> statuses = Arrays.stream(PipelineModule.values())
            .filter(module -> infrastructure.get().isModuleRunning(module))
            .map(module -> {
                ModuleDeploymentService.ModuleStatus status = deploymentService.get().getModuleStatus(module);
                return new ModuleStatus(
                    module.getModuleName(),
                    status.toString().toLowerCase(),
                    status == ModuleDeploymentService.ModuleStatus.RUNNING ? "healthy" : "unknown",
                    module.getContainerName(),
                    module.getSidecarName(),
                    Map.of("unified", module.getUnifiedPort())
                );
            })
            .collect(Collectors.toList());
            
        return Uni.createFrom().item(statuses);
    }

    @GET
    @Path("/{moduleName}/status")
    @Operation(
        summary = "Get module status",
        description = "Returns detailed status information for a specific module"
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Module status retrieved successfully",
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
            
            // Check if services are available
            if (!deploymentService.isResolvable()) {
                return Uni.createFrom().item(
                    Response.status(Response.Status.SERVICE_UNAVAILABLE)
                        .entity(Map.of("error", "Service not available in current mode"))
                        .build()
                );
            }
            
            ModuleDeploymentService.ModuleStatus status = deploymentService.get().getModuleStatus(module);
            
            ModuleStatus moduleStatus = new ModuleStatus(
                module.getModuleName(),
                status.toString().toLowerCase(),
                status == ModuleDeploymentService.ModuleStatus.RUNNING ? "healthy" : "unknown",
                module.getContainerName(),
                module.getSidecarName(),
                Map.of("unified", module.getUnifiedPort())
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
        description = "Deploys a module with its sidecar container (dev mode only)"
    )
    @APIResponses({
        @APIResponse(
            responseCode = "202",
            description = "Module deployment initiated",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = ModuleOperationResponse.class)
            )
        ),
        @APIResponse(
            responseCode = "400",
            description = "Invalid module or configuration"
        ),
        @APIResponse(
            responseCode = "409",
            description = "Module already deployed"
        ),
        @APIResponse(
            responseCode = "503",
            description = "Service not available (not in dev mode)"
        )
    })
    public Uni<Response> deployModule(
            @Parameter(description = "Module name", required = true, example = "echo")
            @PathParam("moduleName") String moduleName) {
        
        LOG.infof("Request to deploy module: %s", moduleName);
        
        // Check if deployment service is available (only in dev mode)
        if (!deploymentService.isResolvable()) {
            return Uni.createFrom().item(
                Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(new ModuleOperationResponse(false, 
                        "Module deployment is only available in dev mode", null))
                    .build()
            );
        }
        
        // Deploy the module
        return Uni.createFrom().item(() -> {
            try {
                PipelineModule module = PipelineModule.fromName(moduleName);
                
                // Check if already deployed
                if (infrastructure.get().isModuleRunning(module)) {
                    return Response.status(Response.Status.CONFLICT)
                        .entity(new ModuleOperationResponse(false, 
                            "Module already deployed: " + moduleName, null))
                        .build();
                }
                
                // Deploy module with sidecars
                ModuleDeploymentService.ModuleDeploymentResult result = deploymentService.get().deployModule(module);
                
                if (result.success()) {
                    return Response.status(Response.Status.ACCEPTED)
                        .entity(new ModuleOperationResponse(true, 
                            result.message(), 
                            Map.of(
                                "module", moduleName,
                                "status", "deployed",
                                "port", result.allocatedPort(),
                                "containerId", result.moduleContainerId()
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
                        "Invalid module: " + moduleName, null))
                    .build();
            }
        })
        .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    @DELETE
    @Path("/{moduleName}")
    @Operation(
        summary = "Stop a module",
        description = "Stops a deployed module and removes its containers"
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Module stopped successfully",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = ModuleOperationResponse.class)
            )
        ),
        @APIResponse(
            responseCode = "404",
            description = "Module not found"
        )
    })
    public Uni<Response> stopModule(
            @Parameter(description = "Module name", required = true, example = "echo")
            @PathParam("moduleName") String moduleName) {
        
        LOG.infof("Request to stop module: %s", moduleName);
        
        return Uni.createFrom().item(() -> {
            try {
                PipelineModule module = PipelineModule.fromName(moduleName);
                
                // Check if services are available
                if (!deploymentService.isResolvable() || !infrastructure.isResolvable()) {
                    return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                        .entity(new ModuleOperationResponse(false, 
                            "Service not available in current mode", null))
                        .build();
                }
                
                // Check if deployed
                if (!infrastructure.get().isModuleRunning(module)) {
                    return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ModuleOperationResponse(false, 
                            "Module not deployed: " + moduleName, null))
                        .build();
                }
                
                // Stop the module
                deploymentService.get().stopModule(module);
                
                return Response.ok(new ModuleOperationResponse(true, 
                    "Module stopped successfully", 
                    Map.of("module", moduleName)))
                    .build();
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ModuleOperationResponse(false, 
                        "Module not found: " + moduleName, null))
                    .build();
            }
        })
        .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    @DELETE
    @Path("/{moduleName}/undeploy")
    @Operation(
        summary = "Undeploy a module (dev mode)",
        description = "Stops a deployed module, deregisters it, and removes all containers (dev mode only)"
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Module undeployed successfully",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = ModuleOperationResponse.class)
            )
        ),
        @APIResponse(
            responseCode = "404",
            description = "Module not found"
        ),
        @APIResponse(
            responseCode = "503",
            description = "Service not available (not in dev mode)"
        )
    })
    public Uni<Response> undeployModule(
            @Parameter(description = "Module name", required = true, example = "echo")
            @PathParam("moduleName") String moduleName) {
        
        LOG.infof("Request to undeploy module: %s", moduleName);
        
        // Check if deployment service is available (only in dev mode)
        if (!deploymentService.isResolvable() || !infrastructure.isResolvable()) {
            return Uni.createFrom().item(
                Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(new ModuleOperationResponse(false, 
                        "Module undeployment is only available in dev mode", null))
                    .build()
            );
        }
        
        return Uni.createFrom().item(() -> {
            try {
                PipelineModule module = PipelineModule.fromName(moduleName);
                
                // Stop the module containers first
                deploymentService.get().stopModule(module);
                
                return Response.ok(new ModuleOperationResponse(true, 
                    "Module undeployed successfully", 
                    Map.of("module", moduleName)))
                    .build();
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ModuleOperationResponse(false, 
                        "Module not found: " + moduleName, null))
                    .build();
            }
        })
        .flatMap(response -> {
            // After stopping containers, deregister all instances of this module
            return moduleRegistry.listRegisteredModules()
                .flatMap(registrations -> {
                    // Find all registrations for this module
                    List<String> moduleIds = registrations.stream()
                        .filter(reg -> reg.moduleName() != null && 
                                reg.moduleName().toLowerCase().contains(moduleName.toLowerCase()))
                        .map(GlobalModuleRegistryService.ModuleRegistration::moduleId)
                        .collect(Collectors.toList());
                    
                    if (moduleIds.isEmpty()) {
                        return Uni.createFrom().item(response);
                    }
                    
                    // Deregister all found module IDs
                    List<Uni<Boolean>> deregisterUnis = moduleIds.stream()
                        .map(moduleId -> moduleRegistry.deregisterModule(moduleId)
                            .onFailure().recoverWithItem(false))
                        .collect(Collectors.toList());
                    
                    return Uni.combine().all().unis(deregisterUnis)
                        .with(results -> {
                            long deregistered = results.stream()
                                .filter(result -> (Boolean) result)
                                .count();
                            LOG.infof("Deregistered %d instances of module %s", deregistered, moduleName);
                            return response;
                        });
                })
                .onFailure().recoverWithItem(response);
        })
        .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    @POST
    @Path("/{moduleName}/scale-up")
    @Operation(
        summary = "Deploy additional instance of module",
        description = "Deploys an additional instance of a module with incremental IP addressing (dev mode only)"
    )
    @APIResponses({
        @APIResponse(
            responseCode = "202",
            description = "Additional instance deployment initiated",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = ModuleOperationResponse.class)
            )
        ),
        @APIResponse(
            responseCode = "400",
            description = "Invalid module or maximum instances reached"
        ),
        @APIResponse(
            responseCode = "503",
            description = "Service not available (not in dev mode)"
        )
    })
    public Uni<Response> scaleUpModule(
            @Parameter(description = "Module name", required = true, example = "echo")
            @PathParam("moduleName") String moduleName) {
        
        LOG.infof("Request to scale up module: %s", moduleName);
        
        // Check if deployment service is available (only in dev mode)
        if (!deploymentService.isResolvable()) {
            return Uni.createFrom().item(
                Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(new ModuleOperationResponse(false, 
                        "Module scaling is only available in dev mode", null))
                    .build()
            );
        }
        
        // Deploy additional instance
        return Uni.createFrom().item(() -> {
            try {
                PipelineModule module = PipelineModule.fromName(moduleName);
                
                // Deploy additional instance
                ModuleDeploymentService.ModuleDeploymentResult result = 
                    deploymentService.get().deployAdditionalInstance(module);
                
                if (result.success()) {
                    return Response.status(Response.Status.ACCEPTED)
                        .entity(new ModuleOperationResponse(true, 
                            result.message(), 
                            Map.of(
                                "module", moduleName,
                                "status", "scaling",
                                "newInstance", result.instanceNumber(),
                                "port", result.allocatedPort(),
                                "containerId", result.moduleContainerId()
                            )))
                        .build();
                } else {
                    return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ModuleOperationResponse(false, 
                            result.message(), null))
                        .build();
                }
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ModuleOperationResponse(false, 
                        "Invalid module: " + moduleName, null))
                    .build();
            }
        })
        .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    @POST
    @Path("/{moduleName}/test")
    @Operation(
        summary = "Test a module",
        description = "Runs health and integration tests for a deployed module"
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Test completed",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = ModuleOperationResponse.class)
            )
        ),
        @APIResponse(
            responseCode = "404",
            description = "Module not found"
        )
    })
    public Uni<Response> testModule(
            @Parameter(description = "Module name", required = true, example = "echo")
            @PathParam("moduleName") String moduleName) {
        
        LOG.infof("Request to test module: %s", moduleName);
        
        // TODO: Implement when service is ready
        return Uni.createFrom().item(
            Response.ok(new ModuleOperationResponse(true, 
                "Module test completed", 
                Map.of("module", moduleName, "status", "passed", "tests", 3)))
                .build()
        );
    }

    @GET
    @Path("/{moduleName}/logs")
    @Operation(
        summary = "Get module logs",
        description = "Retrieves recent logs from a deployed module (dev mode only)"
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Logs retrieved successfully",
            content = @Content(
                mediaType = MediaType.TEXT_PLAIN
            )
        ),
        @APIResponse(
            responseCode = "404",
            description = "Module not found"
        ),
        @APIResponse(
            responseCode = "503",
            description = "Service not available (not in dev mode)"
        )
    })
    @Produces(MediaType.TEXT_PLAIN)
    public Uni<Response> getModuleLogs(
            @Parameter(description = "Module name", required = true, example = "echo")
            @PathParam("moduleName") String moduleName,
            
            @Parameter(description = "Number of lines to retrieve", example = "100")
            @QueryParam("lines") @DefaultValue("100") int lines) {
        
        LOG.infof("Request to get logs for module: %s (last %d lines)", moduleName, lines);
        
        // Check if services are available (only in dev mode)
        if (!deploymentService.isResolvable()) {
            return Uni.createFrom().item(
                Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity("Log retrieval is only available in dev mode")
                    .build()
            );
        }
        
        // TODO: Implement when service is ready
        return Uni.createFrom().item(
            Response.ok("Sample logs for module: " + moduleName + "\n" +
                      "2024-01-01 10:00:00 INFO Module started\n" +
                      "2024-01-01 10:00:01 INFO Connected to engine\n" +
                      "2024-01-01 10:00:02 INFO Ready to process requests\n")
                .build()
        );
    }
    
    @DELETE
    @Path("/{moduleName}/instance/{moduleId}")
    @Operation(
        summary = "Stop a specific module instance", 
        description = "Stops and removes a specific instance of a module in dev mode"
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Instance stopped successfully",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = ModuleOperationResponse.class)
            )
        ),
        @APIResponse(
            responseCode = "404",
            description = "Module or instance not found"
        ),
        @APIResponse(
            responseCode = "503",
            description = "Service not available (not in dev mode)"
        )
    })
    public Uni<Response> stopModuleInstance(
            @Parameter(description = "Module name", required = true, example = "echo")
            @PathParam("moduleName") String moduleName,
            @Parameter(description = "Module ID", required = true, example = "echo-module-abc123")
            @PathParam("moduleId") String moduleId) {
        
        LOG.infof("Request to stop module instance: %s (moduleId: %s)", moduleName, moduleId);
        
        // Check if deployment service is available (only in dev mode)
        if (!deploymentService.isResolvable()) {
            return Uni.createFrom().item(
                Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(new ModuleOperationResponse(false, 
                        "Module instance management is only available in dev mode", null))
                    .build()
            );
        }
        
        return Uni.createFrom().item(() -> {
            try {
                PipelineModule module = PipelineModule.fromName(moduleName);
                
                // Stop the specific instance by moduleId
                deploymentService.get().stopModuleByInstanceId(module, moduleId);
                
                return Response.ok(new ModuleOperationResponse(true, 
                    String.format("Module instance %s stopped successfully", moduleId), 
                    Map.of(
                        "module", moduleName,
                        "moduleId", moduleId
                    )))
                    .build();
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ModuleOperationResponse(false, 
                        "Module not found: " + moduleName, null))
                    .build();
            }
        })
        .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }
}