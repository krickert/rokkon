package com.rokkon.pipeline.engine.api;

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
import com.rokkon.pipeline.commons.model.GlobalModuleRegistryService;
import io.opentelemetry.instrumentation.annotations.WithSpan;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST API for production module management operations.
 * Provides operations for querying and managing registered modules via the module registry.
 * 
 * For development mode operations (Docker containers, deployment, etc.), see ModuleDevManagementResource.
 */
@Path("/api/v1/module-management")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Module Management", description = "Module registry operations")
public class ModuleManagementResource {

    private static final Logger LOG = Logger.getLogger(ModuleManagementResource.class);

    @Inject
    GlobalModuleRegistryService moduleRegistry;

    /**
     * Response for module operations
     */
    @Schema(description = "Module operation response")
    public record ModuleOperationResponse(
        @Schema(description = "Whether the operation was successful", example = "true")
        boolean success,
        
        @Schema(description = "Operation message", example = "Module deregistered successfully")
        String message,
        
        @Schema(description = "Additional details about the operation")
        Map<String, Object> details
    ) {}

    /**
     * Registered module information from the registry
     */
    @Schema(description = "Registered module information")
    public record RegisteredModule(
        @Schema(description = "Module ID", example = "echo-abc123")
        String moduleId,
        
        @Schema(description = "Module name", example = "echo")
        String moduleName,
        
        @Schema(description = "Module host", example = "module-host.example.com")
        String host,
        
        @Schema(description = "Module port", example = "39100")
        int port,
        
        @Schema(description = "Service type", example = "GRPC")
        String serviceType,
        
        @Schema(description = "Module version", example = "1.0.0")
        String version,
        
        @Schema(description = "Whether module is enabled", example = "true")
        boolean enabled,
        
        @Schema(description = "Registration timestamp")
        long registeredAt,
        
        @Schema(description = "Additional metadata")
        Map<String, String> metadata
    ) {}

    @GET
    @Path("/registered")
    @Operation(
        summary = "List registered modules",
        description = "Returns a list of all modules registered in the module registry"
    )
    @APIResponse(
        responseCode = "200",
        description = "List of registered modules",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(
                type = SchemaType.ARRAY,
                implementation = RegisteredModule.class
            )
        )
    )
    public Uni<List<RegisteredModule>> listRegisteredModules() {
        LOG.info("Listing registered modules from registry");
        
        return moduleRegistry.listRegisteredModules()
            .map(registrations -> registrations.stream()
                .map(reg -> new RegisteredModule(
                    reg.moduleId(),
                    reg.moduleName(),
                    reg.host(),
                    reg.port(),
                    reg.serviceType(),
                    reg.version(),
                    reg.enabled(),
                    reg.registeredAt(),
                    reg.metadata()
                ))
                .collect(Collectors.toList())
            );
    }

    @GET
    @Path("/{moduleId}")
    @Operation(
        summary = "Get module details",
        description = "Returns detailed information about a specific registered module"
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Module details retrieved successfully",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = RegisteredModule.class)
            )
        ),
        @APIResponse(
            responseCode = "404",
            description = "Module not found"
        )
    })
    public Uni<Response> getModuleDetails(
            @Parameter(description = "Module ID", required = true, example = "echo-abc123")
            @PathParam("moduleId") String moduleId) {
        
        LOG.infof("Getting details for module: %s", moduleId);
        
        return moduleRegistry.getModule(moduleId)
            .map(reg -> {
                if (reg != null) {
                    RegisteredModule module = new RegisteredModule(
                        reg.moduleId(),
                        reg.moduleName(),
                        reg.host(),
                        reg.port(),
                        reg.serviceType(),
                        reg.version(),
                        reg.enabled(),
                        reg.registeredAt(),
                        reg.metadata()
                    );
                    return Response.ok(module).build();
                } else {
                    return Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "Module not found: " + moduleId))
                        .build();
                }
            });
    }

    @DELETE
    @Path("/{moduleId}/deregister")
    @Operation(
        summary = "Deregister a module",
        description = "Removes a module from the module registry"
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Module deregistered successfully",
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
    public Uni<Response> deregisterModule(
            @Parameter(description = "Module ID", required = true, example = "echo-abc123")
            @PathParam("moduleId") String moduleId) {
        
        LOG.infof("Request to deregister module: %s", moduleId);
        
        return moduleRegistry.deregisterModule(moduleId)
            .map(success -> {
                if (success) {
                    return Response.ok(new ModuleOperationResponse(true,
                        "Module deregistered successfully",
                        Map.of("moduleId", moduleId)))
                        .build();
                } else {
                    return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ModuleOperationResponse(false,
                            "Module not found or deregistration failed",
                            Map.of("moduleId", moduleId)))
                        .build();
                }
            });
    }

    @PUT
    @Path("/{moduleId}/enable")
    @Operation(
        summary = "Enable a module",
        description = "Enables a disabled module in the registry"
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Module enabled successfully",
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
    public Uni<Response> enableModule(
            @Parameter(description = "Module ID", required = true, example = "echo-abc123")
            @PathParam("moduleId") String moduleId) {
        
        LOG.infof("Request to enable module: %s", moduleId);
        
        return moduleRegistry.enableModule(moduleId)
            .map(success -> {
                if (success) {
                    return Response.ok(new ModuleOperationResponse(true,
                        "Module enabled successfully",
                        Map.of("moduleId", moduleId)))
                        .build();
                } else {
                    return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ModuleOperationResponse(false,
                            "Module not found or enable failed",
                            Map.of("moduleId", moduleId)))
                        .build();
                }
            });
    }

    @PUT
    @Path("/{moduleId}/disable")
    @Operation(
        summary = "Disable a module",
        description = "Disables a module in the registry without removing it"
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Module disabled successfully",
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
    public Uni<Response> disableModule(
            @Parameter(description = "Module ID", required = true, example = "echo-abc123")
            @PathParam("moduleId") String moduleId) {
        
        LOG.infof("Request to disable module: %s", moduleId);
        
        return moduleRegistry.disableModule(moduleId)
            .map(success -> {
                if (success) {
                    return Response.ok(new ModuleOperationResponse(true,
                        "Module disabled successfully",
                        Map.of("moduleId", moduleId)))
                        .build();
                } else {
                    return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ModuleOperationResponse(false,
                            "Module not found or disable failed",
                            Map.of("moduleId", moduleId)))
                        .build();
                }
            });
    }

    @GET
    @Path("/{moduleId}/health")
    @Operation(
        summary = "Check module health",
        description = "Performs health check on a specific module"
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Health check result",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = GlobalModuleRegistryService.ServiceHealthStatus.class)
            )
        ),
        @APIResponse(
            responseCode = "404",
            description = "Module not found"
        )
    })
    public Uni<Response> checkModuleHealth(
            @Parameter(description = "Module ID", required = true, example = "echo-abc123")
            @PathParam("moduleId") String moduleId) {
        
        LOG.infof("Performing health check on module: %s", moduleId);
        
        return moduleRegistry.getModuleHealthStatus(moduleId)
            .map(status -> {
                if (status != null) {
                    return Response.ok(status).build();
                } else {
                    return Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "Module not found: " + moduleId))
                        .build();
                }
            });
    }

    @DELETE
    @Path("/zombies/cleanup")
    @Operation(
        summary = "Clean up zombie modules",
        description = "Removes modules from registry that are no longer responding"
    )
    @APIResponse(
        responseCode = "200",
        description = "Zombie cleanup results",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = GlobalModuleRegistryService.ZombieCleanupResult.class)
        )
    )
    public Uni<GlobalModuleRegistryService.ZombieCleanupResult> cleanupZombies() {
        LOG.info("Cleaning up zombie modules from registry");
        
        return moduleRegistry.cleanupZombieInstances();
    }
}